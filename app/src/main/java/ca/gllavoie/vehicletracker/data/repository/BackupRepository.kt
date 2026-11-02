package ca.gllavoie.vehicletracker.data.repository

import android.content.Context
import android.database.Cursor
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import ca.gllavoie.vehicletracker.BuildConfig
import ca.gllavoie.vehicletracker.data.local.VehicleDatabase
import ca.gllavoie.vehicletracker.domain.document.VehicleDocumentType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val BACKUP_FORMAT = "vehicle-tracker-backup"
private const val BACKUP_VERSION = 1
private const val MAX_BACKUP_BYTES = 200L * 1024L * 1024L
private const val MAX_DATABASE_JSON_BYTES = 50L * 1024L * 1024L

/** Validated metadata shown before the user explicitly confirms a replace restore. */
data class BackupPreview(
    val createdAtUtc: Long,
    val appVersion: String,
    val recordCounts: Map<String, Int>,
    val attachmentCount: Int
)

/** Versioned ZIP backup. The database export is JSON; original attachments are separate entries. */
class BackupRepository(
    private val context: Context,
    private val database: VehicleDatabase,
    private val receiptStorage: File = File(context.filesDir, "receipts"),
    private val maxBackupBytes: Long = MAX_BACKUP_BYTES
) {
    suspend fun createBackup(output: OutputStream): BackupPreview = withContext(Dispatchers.IO) {
        try {
            val snapshot = exportSnapshot()
            val jsonBytes = snapshot.json.toString().toByteArray(Charsets.UTF_8)
            require(jsonBytes.size <= MAX_DATABASE_JSON_BYTES) { "Database export exceeds the backup size limit." }
            val checksums = JSONObject()
            checksums.put("database.json", checksumEntry(jsonBytes))
            val attachments = readSnapshotAttachments(snapshot.json)
            val attachmentPayloads = LinkedHashMap<String, ByteArray>()
            for (attachment in attachments) {
                val file = resolveReceipt(attachment.relativePath)
                require(file.isFile) { "A receipt file is missing: ${attachment.originalName}" }
                require(file.length() == attachment.sizeBytes) { "A receipt size does not match its database metadata: ${attachment.originalName}" }
                val bytes = file.readBytes()
                val hash = bytes.sha256()
                require(hash == attachment.sha256) { "A receipt checksum does not match its database metadata: ${attachment.originalName}" }
                val entryName = "attachments/${attachment.relativePath}"
                attachmentPayloads[entryName] = bytes
                checksums.put(entryName, checksumEntry(bytes))
            }
            val totalArchivePayloadBytes = attachmentPayloads.values.sumOf { it.size.toLong() } + jsonBytes.size
            require(totalArchivePayloadBytes <= maxBackupBytes) { "Backup exceeds the maximum supported size." }
            val manifest = JSONObject()
                .put("format", BACKUP_FORMAT)
                .put("backupVersion", BACKUP_VERSION)
                .put("databaseVersion", snapshot.schemaVersion)
                .put("appVersion", BuildConfig.VERSION_NAME)
                .put("createdAtUtc", System.currentTimeMillis())
                .put("recordCounts", JSONObject(snapshot.recordCounts))
                .put("attachmentCount", attachments.size)
                .put("checksums", checksums)
            ZipOutputStream(output).use { zip ->
                putZipEntry(zip, "manifest.json", manifest.toString().toByteArray(Charsets.UTF_8))
                putZipEntry(zip, "database.json", jsonBytes)
                attachmentPayloads.forEach { (name, bytes) -> putZipEntry(zip, name, bytes) }
                zip.finish()
            }
            val createdAt = manifest.getLong("createdAtUtc")
            recordHistory("backup.last_success_utc", createdAt.toString())
            recordHistory("backup.last_error", "")
            BackupPreview(createdAt, BuildConfig.VERSION_NAME, snapshot.recordCounts, attachments.size)
        } catch (error: Exception) {
            runCatching { recordHistory("backup.last_error", error.message.orEmpty().take(240)) }
            throw error
        }
    }

    suspend fun validateBackup(input: InputStream): BackupPreview = withContext(Dispatchers.IO) {
        parseAndValidate(input).preview
    }

    /** Destructive replacement is deliberately impossible without an explicit UI confirmation. */
    suspend fun restoreBackup(input: InputStream, confirmReplace: Boolean): BackupPreview = withContext(Dispatchers.IO) {
        require(confirmReplace) { "Restore replaces all existing garage data. Confirm replacement before continuing." }
        val validated = parseAndValidate(input)
        val liveDb = database.openHelper.writableDatabase
        val oldReceiptPaths = readLiveReceiptPaths(liveDb)
        val stagedFiles = mutableListOf<File>()
        val replacementFiles = LinkedHashMap<String, String>()
        var restoreCommitted = false
        try {
            for ((entryName, bytes) in validated.attachmentPayloads) {
                val oldRelative = entryName.removePrefix("attachments/")
                val extension = when (mimeForRelativePath(oldRelative)) {
                    "application/pdf" -> "pdf"
                    "image/jpeg" -> "jpg"
                    "image/png" -> "png"
                    "image/webp" -> "webp"
                    "image/gif" -> "gif"
                    "image/heic" -> "heic"
                    "image/heif" -> "heif"
                    "image/avif" -> "avif"
                    else -> throw IllegalArgumentException("Unsupported receipt attachment in backup.")
                }
                val internalName = "${UUID.randomUUID()}.$extension"
                val stage = File(receiptStorage, ".restore-${UUID.randomUUID()}.part")
                val destination = File(receiptStorage, internalName)
                require(receiptStorage.mkdirs() || receiptStorage.isDirectory) { "Receipt storage is not available." }
                try {
                    stage.writeBytes(bytes)
                    if (!stage.renameTo(destination)) throw IOException("Could not stage a restored receipt file.")
                    stagedFiles += destination
                } catch (failure: Exception) {
                    stage.delete()
                    destination.delete()
                    throw failure
                }
                replacementFiles[oldRelative] = "receipts/$internalName"
            }
            rewriteReceiptPaths(validated.databaseJson, replacementFiles)
            liveDb.beginTransaction()
            try {
                deleteCurrentRows(liveDb)
                insertRestoredRows(liveDb, validated.databaseJson)
                val foreignKeyErrors = liveDb.query("PRAGMA foreign_key_check")
                foreignKeyErrors.use { check(!it.moveToFirst()) { "Restored data contains broken foreign-key references." } }
                liveDb.setTransactionSuccessful()
            } finally {
                liveDb.endTransaction()
            }
            restoreCommitted = true
            oldReceiptPaths.forEach { path -> runCatching { resolveReceipt(path).delete() } }
            runCatching { recordHistory("backup.last_restore_utc", System.currentTimeMillis().toString()) }
            runCatching { recordHistory("backup.last_error", "") }
            validated.preview
        } catch (error: Exception) {
            if (!restoreCommitted) stagedFiles.forEach { it.delete() }
            runCatching { recordHistory("backup.last_error", "Restore failed: ${error.message.orEmpty()}".take(240)) }
            throw error
        }
    }

    suspend fun history(): Map<String, String> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, String>()
        val cursor = database.openHelper.writableDatabase.query(
            "SELECT `key`, `value` FROM `app_settings` WHERE `key` LIKE 'backup.%'"
        )
        cursor.use { while (it.moveToNext()) result[it.getString(0)] = it.getString(1) }
        result
    }

    private data class Snapshot(val json: JSONObject, val schemaVersion: Int, val recordCounts: Map<String, Int>)
    private data class AttachmentExport(val relativePath: String, val originalName: String, val sizeBytes: Long, val sha256: String)
    private data class Validated(
        val preview: BackupPreview,
        val databaseJson: JSONObject,
        val attachmentPayloads: Map<String, ByteArray>
    )
    private data class TableSchema(val columns: List<String>, val types: List<String>, val primaryKeys: List<Int>)

    private fun exportSnapshot(): Snapshot {
        val sql = database.openHelper.writableDatabase
        sql.beginTransaction()
        try {
            val versionCursor = sql.query("PRAGMA user_version")
            val schemaVersion = versionCursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
            val tables = JSONObject()
            val counts = linkedMapOf<String, Int>()
            for (table in TABLES) {
                val schema = schemaFor(sql, table)
                val columns = JSONArray()
                schema.columns.forEach(columns::put)
                val rows = JSONArray()
                sql.query("SELECT * FROM `${table}`").use { cursor ->
                    while (cursor.moveToNext()) {
                        val row = JSONArray()
                        for (index in schema.columns.indices) row.put(cursorValue(cursor, index))
                        rows.put(row)
                    }
                }
                tables.put(table, JSONObject().put("columns", columns).put("rows", rows))
                counts[table] = rows.length()
            }
            sql.setTransactionSuccessful()
            return Snapshot(JSONObject().put("schemaVersion", schemaVersion).put("tables", tables), schemaVersion, counts)
        } finally {
            sql.endTransaction()
        }
    }

    private fun cursorValue(cursor: Cursor, index: Int): Any = when (cursor.getType(index)) {
        Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
        Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
        Cursor.FIELD_TYPE_STRING -> cursor.getString(index)
        Cursor.FIELD_TYPE_BLOB -> JSONObject().put("$blobKey", android.util.Base64.encodeToString(cursor.getBlob(index), android.util.Base64.NO_WRAP))
        else -> JSONObject.NULL
    }

    private fun parseAndValidate(input: InputStream): Validated {
        val entries = readZipEntries(input)
        val manifestBytes = requireNotNull(entries["manifest.json"]) { "Backup manifest is missing." }
        val databaseBytes = requireNotNull(entries["database.json"]) { "Backup database export is missing." }
        val manifest = runCatching { JSONObject(manifestBytes.toString(Charsets.UTF_8)) }
            .getOrElse { throw IllegalArgumentException("Backup manifest is malformed.") }
        require(manifest.optString("format") == BACKUP_FORMAT) { "This is not a vehicle tracker backup." }
        require(manifest.optInt("backupVersion") == BACKUP_VERSION) { "Unsupported backup archive version." }
        val sql = database.openHelper.writableDatabase
        val currentVersion = readSchemaVersion(sql)
        val archiveDatabaseVersion = manifest.optInt("databaseVersion", -1)
        require(archiveDatabaseVersion in 1..currentVersion) {
            "Backup database schema is incompatible with this app version."
        }
        require(databaseBytes.size.toLong() <= MAX_DATABASE_JSON_BYTES) { "Database export exceeds the backup size limit." }
        val checksumObject = manifest.optJSONObject("checksums") ?: throw IllegalArgumentException("Backup checksum list is missing.")
        val expectedNames = mutableSetOf<String>()
        val checksumKeys = checksumObject.keys()
        while (checksumKeys.hasNext()) expectedNames += checksumKeys.next()
        require(expectedNames == entries.keys - "manifest.json") { "Backup contains missing or unlisted files." }
        for (name in expectedNames) {
            validateArchivePath(name)
            val bytes = requireNotNull(entries[name])
            val checksum = checksumObject.getJSONObject(name)
            require(checksum.getLong("sizeBytes") == bytes.size.toLong()) { "Backup file size check failed for $name." }
            require(checksum.getString("sha256") == bytes.sha256()) { "Backup checksum verification failed for $name." }
        }
        val databaseJson = runCatching { JSONObject(databaseBytes.toString(Charsets.UTF_8)) }
            .getOrElse { throw IllegalArgumentException("Backup database export is malformed.") }
        require(databaseJson.optInt("schemaVersion", -1) == archiveDatabaseVersion) { "Backup database version does not match its manifest." }
        validateDatabaseRows(sql, databaseJson, archiveDatabaseVersion)
        if (archiveDatabaseVersion != currentVersion) migrateBackupDatabaseJson(databaseJson, archiveDatabaseVersion, currentVersion)
        require(databaseJson.optInt("schemaVersion") == currentVersion) { "Backup database version is incompatible." }
        val schemas = validateDatabaseRows(sql, databaseJson, currentVersion)
        val counts = schemas.mapValues { (_, info) -> info.second }
        val attachmentPayloads = validateAttachments(databaseJson, entries)
        val manifestCounts = manifest.optJSONObject("recordCounts") ?: JSONObject()
        require(counts.all { (table, count) -> manifestCounts.optInt(table, -1) == count }) { "Backup record counts do not match the database export." }
        require(manifest.optInt("attachmentCount", -1) == attachmentPayloads.size) { "Backup attachment count does not match its records." }
        val preview = BackupPreview(
            createdAtUtc = manifest.optLong("createdAtUtc", -1L).also { require(it >= 0L) { "Backup creation timestamp is invalid." } },
            appVersion = manifest.optString("appVersion", "unknown"),
            recordCounts = counts,
            attachmentCount = attachmentPayloads.size
        )
        return Validated(preview, databaseJson, attachmentPayloads)
    }

    private fun readZipEntries(input: InputStream): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        var totalBytes = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory) { "Backup may not contain directory entries." }
                validateArchivePath(entry.name)
                require(entry.name !in entries) { "Backup contains duplicate archive entries." }
                require(entry.name == "manifest.json" || entry.name == "database.json" || entry.name.startsWith("attachments/receipts/")) {
                    "Backup contains an unexpected file."
                }
                val maxEntry = if (entry.name == "database.json") MAX_DATABASE_JSON_BYTES else maxBackupBytes
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = zip.read(buffer)
                    if (count < 0) break
                    totalBytes = Math.addExact(totalBytes, count.toLong())
                    require(totalBytes <= maxBackupBytes) { "Backup exceeds the maximum supported size." }
                    require(output.size().toLong() + count <= maxEntry) { "Backup entry is too large." }
                    output.write(buffer, 0, count)
                }
                entries[entry.name] = output.toByteArray()
                zip.closeEntry()
            }
        }
        return entries
    }

    private fun validateDatabaseRows(sql: SupportSQLiteDatabase, json: JSONObject, archiveVersion: Int): Map<String, Pair<TableSchema, Int>> {
        val tableJson = json.optJSONObject("tables") ?: throw IllegalArgumentException("Backup table data is missing.")
        require(tableJson.length() == TABLES.size && TABLES.all(tableJson::has)) { "Backup table list does not match this app schema." }
        val currentVersion = readSchemaVersion(sql)
        val schemaByTable = linkedMapOf<String, Pair<TableSchema, Int>>()
        val rowsByTable = linkedMapOf<String, List<List<Any?>>>()
        for (table in TABLES) {
            val tableJsonObject = tableJson.getJSONObject(table)
            val currentSchema = schemaFor(sql, table)
            val schema = when {
                archiveVersion == currentVersion -> currentSchema
                archiveVersion in 1 until currentVersion && table == "vehicles" -> {
                    val columnsToRemove = buildSet {
                        if (archiveVersion < 2) add("startingDailyUsageKmPerDay")
                        if (archiveVersion < 3) add("oilIntervalMode")
                        if (archiveVersion < 4) add("vehicleType")
                    }
                    withoutColumns(currentSchema, columnsToRemove)
                }
                archiveVersion in 1 until currentVersion && table == "receipt_attachments" -> {
                    val columnsToRemove = if (archiveVersion < 5) setOf("documentType") else emptySet()
                    withoutColumns(currentSchema, columnsToRemove)
                }
                archiveVersion in 1 until currentVersion -> currentSchema
                else -> throw IllegalArgumentException("Backup database schema is incompatible with this app version.")
            }
            val columnsJson = tableJsonObject.getJSONArray("columns")
            require(columnsJson.length() == schema.columns.size) { "Backup columns do not match the $table schema." }
            require(schema.columns.indices.all { columnsJson.getString(it) == schema.columns[it] }) { "Backup columns do not match the $table schema." }
            val rowsArray = tableJsonObject.getJSONArray("rows")
            val rows = mutableListOf<List<Any?>>()
            val primaryKeys = mutableSetOf<String>()
            for (rowIndex in 0 until rowsArray.length()) {
                val row = rowsArray.getJSONArray(rowIndex)
                require(row.length() == schema.columns.size) { "A $table record has an invalid number of columns." }
                val values = schema.columns.indices.map { index -> jsonValue(row.get(index), schema.types[index]) }
                schema.primaryKeys.forEach { index ->
                    val key = values[index] ?: throw IllegalArgumentException("A $table record has no primary key.")
                    require(primaryKeys.add(key.toString())) { "Backup contains duplicate IDs in $table." }
                }
                rows += values
            }
            schemaByTable[table] = schema to rows.size
            rowsByTable[table] = rows
        }
        validateReferences(schemaByTable.mapValues { it.value.first }, rowsByTable)
        return schemaByTable
    }

    private fun validateReferences(schemas: Map<String, TableSchema>, rows: Map<String, List<List<Any?>>>) {
        fun idSet(table: String, column: String): Set<String> {
            val index = schemas.getValue(table).columns.indexOf(column)
            require(index >= 0) { "Required backup column $table.$column is missing." }
            return rows.getValue(table).mapNotNull { it[index]?.toString() }.toSet()
        }
        fun check(table: String, column: String, parent: String, nullable: Boolean = true) {
            val index = schemas.getValue(table).columns.indexOf(column)
            val references = idSet(parent, "id")
            rows.getValue(table).forEach { row ->
                val value = row[index]?.toString()
                require(value != null || nullable) { "$table.$column cannot be empty." }
                require(value == null || value in references) { "$table.$column references a missing $parent record." }
            }
        }
        check("vehicles", "defaultSpecificationId", "vehicle_specifications")
        check("specification_sources", "specificationId", "vehicle_specifications", nullable = false)
        check("vehicle_specification_overrides", "vehicleId", "vehicles", nullable = false)
        check("odometer_readings", "vehicleId", "vehicles", nullable = false)
        check("oil_changes", "vehicleId", "vehicles", nullable = false)
        check("fuel_fill_ups", "vehicleId", "vehicles", nullable = false)
        check("oil_changes", "odometerReadingId", "odometer_readings")
        check("fuel_fill_ups", "odometerReadingId", "odometer_readings")
        check("receipt_attachments", "vehicleId", "vehicles", nullable = false)
        check("receipt_attachments", "oilChangeId", "oil_changes")
        check("receipt_attachments", "fuelFillUpId", "fuel_fill_ups")
        val oilColumn = schemas.getValue("receipt_attachments").columns.indexOf("oilChangeId")
        val fuelColumn = schemas.getValue("receipt_attachments").columns.indexOf("fuelFillUpId")
        val documentTypeColumn = schemas.getValue("receipt_attachments").columns.indexOf("documentType")
        rows.getValue("receipt_attachments").forEach { row ->
            val hasOil = row[oilColumn] != null
            val hasFuel = row[fuelColumn] != null
            if (documentTypeColumn < 0) {
                require(hasOil xor hasFuel) { "Each legacy receipt must belong to exactly one service or fuel record." }
            } else {
                when (row[documentTypeColumn]?.toString()) {
                    VehicleDocumentType.RECEIPT.storedValue -> require(hasOil xor hasFuel) { "Each service receipt must belong to exactly one service or fuel record." }
                    in VehicleDocumentType.vehicleDocumentTypes.map { it.storedValue } -> require(!hasOil && !hasFuel) { "A vehicle document cannot be linked to a service or fuel record." }
                    else -> throw IllegalArgumentException("A document has an unsupported type.")
                }
            }
        }
    }

    private fun validateAttachments(databaseJson: JSONObject, entries: Map<String, ByteArray>): Map<String, ByteArray> {
        val tables = databaseJson.getJSONObject("tables")
        val attachmentTable = tables.getJSONObject("receipt_attachments")
        val columns = (0 until attachmentTable.getJSONArray("columns").length()).map { attachmentTable.getJSONArray("columns").getString(it) }
        val pathIndex = columns.indexOf("internalRelativePath")
        val sizeIndex = columns.indexOf("sizeBytes")
        val hashIndex = columns.indexOf("sha256")
        require(pathIndex >= 0 && sizeIndex >= 0 && hashIndex >= 0) { "Receipt metadata columns are missing." }
        val payloads = linkedMapOf<String, ByteArray>()
        val expected = mutableSetOf<String>()
        val rows = attachmentTable.getJSONArray("rows")
        for (index in 0 until rows.length()) {
            val row = rows.getJSONArray(index)
            val relative = row.getString(pathIndex)
            validateRelativeReceiptPath(relative)
            val entryName = "attachments/$relative"
            expected += entryName
            val bytes = requireNotNull(entries[entryName]) { "A receipt file is missing from the backup." }
            require(bytes.size <= 10 * 1024 * 1024) { "A receipt exceeds the supported 10 MB size limit." }
            require(bytes.size.toLong() == row.getLong(sizeIndex)) { "A receipt length does not match its metadata." }
            require(bytes.sha256() == row.getString(hashIndex)) { "A receipt checksum does not match its metadata." }
            payloads[entryName] = bytes
        }
        require(entries.keys.filter { it.startsWith("attachments/") }.toSet() == expected) { "Backup contains unlinked receipt files." }
        return payloads
    }

    private fun insertRestoredRows(sql: SupportSQLiteDatabase, json: JSONObject) {
        val tables = json.getJSONObject("tables")
        for (table in INSERT_ORDER) {
            val tableData = tables.getJSONObject(table)
            val columns = tableData.getJSONArray("columns")
            val rows = tableData.getJSONArray("rows")
            val columnNames = (0 until columns.length()).map { columns.getString(it) }
            val placeholders = columnNames.joinToString(",") { "?" }
            val quotedColumns = columnNames.joinToString(",") { "`$it`" }
            val insertSql = "INSERT INTO `$table` ($quotedColumns) VALUES ($placeholders)"
            val schema = schemaFor(sql, table)
            for (rowIndex in 0 until rows.length()) {
                val row = rows.getJSONArray(rowIndex)
                val args = Array<Any?>(row.length()) { index -> jsonValue(row.get(index), schema.types[index]) }
                sql.execSQL(insertSql, args)
            }
        }
    }

    private fun deleteCurrentRows(sql: SupportSQLiteDatabase) {
        DELETE_ORDER.forEach { sql.execSQL("DELETE FROM `$it`") }
    }

    private fun withoutColumns(schema: TableSchema, columnsToRemove: Set<String>): TableSchema {
        val removedIndices = columnsToRemove.map { name ->
            schema.columns.indexOf(name).also { require(it >= 0) { "The app schema is missing column $name." } }
        }.toSet()
        val retainedIndices = schema.columns.indices.filterNot(removedIndices::contains)
        val oldToNewIndex = retainedIndices.withIndex().associate { (newIndex, oldIndex) -> oldIndex to newIndex }
        return TableSchema(
            columns = retainedIndices.map(schema.columns::get),
            types = retainedIndices.map(schema.types::get),
            primaryKeys = schema.primaryKeys.mapNotNull(oldToNewIndex::get)
        )
    }

    private fun migrateBackupDatabaseJson(json: JSONObject, fromVersion: Int, toVersion: Int) {
        require(fromVersion in 1..toVersion) { "No backup migration exists for these database versions." }
        var version = fromVersion
        while (version < toVersion) {
            val (tableName, column, defaultValue) = when (version) {
                1 -> Triple("vehicles", "startingDailyUsageKmPerDay", JSONObject.NULL)
                2 -> Triple("vehicles", "oilIntervalMode", "NORMAL")
                3 -> Triple("vehicles", "vehicleType", "OTHER")
                4 -> Triple("receipt_attachments", "documentType", VehicleDocumentType.RECEIPT.storedValue)
                else -> throw IllegalArgumentException("No backup migration exists from database version $version.")
            }
            val table = json.getJSONObject("tables").getJSONObject(tableName)
            val columns = table.getJSONArray("columns")
            require((0 until columns.length()).none { columns.getString(it) == column }) {
                "Backup already contains $column but declares an older schema."
            }
            val rows = table.getJSONArray("rows")
            for (index in 0 until rows.length()) rows.getJSONArray(index).put(defaultValue)
            columns.put(column)
            version++
            json.put("schemaVersion", version)
        }
    }

    private fun schemaFor(sql: SupportSQLiteDatabase, table: String): TableSchema {
        require(table in TABLES) { "Unsupported database table." }
        val columns = mutableListOf<String>()
        val types = mutableListOf<String>()
        val primaryKeys = mutableListOf<Int>()
        sql.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val typeIndex = cursor.getColumnIndexOrThrow("type")
            val pkIndex = cursor.getColumnIndexOrThrow("pk")
            while (cursor.moveToNext()) {
                columns += cursor.getString(nameIndex)
                types += cursor.getString(typeIndex).orEmpty().uppercase()
                if (cursor.getInt(pkIndex) > 0) primaryKeys += columns.lastIndex
            }
        }
        require(columns.isNotEmpty()) { "Database table $table is missing." }
        return TableSchema(columns, types, primaryKeys)
    }

    private fun jsonValue(value: Any, sqliteType: String): Any? {
        if (value == JSONObject.NULL) return null
        if (value is JSONObject && value.has(blobKey)) return android.util.Base64.decode(value.getString(blobKey), android.util.Base64.DEFAULT)
        return when {
            sqliteType.contains("INT") -> (value as? Number)?.toLong() ?: throw IllegalArgumentException("Invalid integer database value.")
            sqliteType.contains("REAL") || sqliteType.contains("FLOAT") || sqliteType.contains("DOUBLE") -> (value as? Number)?.toDouble() ?: throw IllegalArgumentException("Invalid numeric database value.")
            sqliteType.contains("BLOB") -> (value as? ByteArray) ?: throw IllegalArgumentException("Invalid binary database value.")
            else -> value.toString()
        }
    }

    private fun readSnapshotAttachments(snapshot: JSONObject): List<AttachmentExport> {
        val table = snapshot.getJSONObject("tables").getJSONObject("receipt_attachments")
        val columns = table.getJSONArray("columns")
        val indices = (0 until columns.length()).associate { columns.getString(it) to it }
        val rows = table.getJSONArray("rows")
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONArray(index)
            AttachmentExport(
                relativePath = row.getString(indices.getValue("internalRelativePath")),
                originalName = row.getString(indices.getValue("originalFileName")),
                sizeBytes = row.getLong(indices.getValue("sizeBytes")),
                sha256 = row.getString(indices.getValue("sha256"))
            )
        }
    }

    private fun readLiveReceiptPaths(sql: SupportSQLiteDatabase): List<String> {
        val paths = mutableListOf<String>()
        sql.query("SELECT `internalRelativePath` FROM `receipt_attachments`").use { cursor ->
            while (cursor.moveToNext()) paths += cursor.getString(0)
        }
        return paths
    }

    private fun rewriteReceiptPaths(json: JSONObject, replacements: Map<String, String>) {
        val table = json.getJSONObject("tables").getJSONObject("receipt_attachments")
        val columns = table.getJSONArray("columns")
        val pathIndex = (0 until columns.length()).first { columns.getString(it) == "internalRelativePath" }
        val rows = table.getJSONArray("rows")
        for (index in 0 until rows.length()) {
            val row = rows.getJSONArray(index)
            val oldPath = row.getString(pathIndex)
            row.put(pathIndex, replacements[oldPath] ?: throw IllegalArgumentException("A restored receipt has no staged file."))
        }
    }

    private fun resolveReceipt(relativePath: String): File {
        validateRelativeReceiptPath(relativePath)
        val root = receiptStorage.canonicalFile
        val name = relativePath.removePrefix("receipts/")
        val result = File(root, name).canonicalFile
        require(result.path.startsWith(root.path + File.separator)) { "Receipt path escapes private storage." }
        return result
    }

    private fun validateRelativeReceiptPath(path: String) {
        require(path.startsWith("receipts/") && !path.startsWith('/') && !path.contains('\\') && !path.contains("..")) {
            "Receipt path is invalid."
        }
        val leaf = path.removePrefix("receipts/")
        require(leaf.isNotBlank() && !leaf.contains('/')) { "Receipt path is invalid." }
    }

    private fun validateArchivePath(path: String) {
        require(path.isNotBlank() && !path.startsWith('/') && !path.contains('\\') && path.split('/').none { it == ".." || it == "." }) {
            "Backup contains an unsafe file path."
        }
    }

    private fun mimeForRelativePath(path: String): String = when (path.substringAfterLast('.').lowercase()) {
        "pdf" -> "application/pdf"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "heic" -> "image/heic"
        "heif" -> "image/heif"
        "avif" -> "image/avif"
        else -> ""
    }

    private fun readSchemaVersion(sql: SupportSQLiteDatabase): Int {
        val cursor = sql.query("PRAGMA user_version")
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    private fun recordHistory(key: String, value: String) {
        database.openHelper.writableDatabase.execSQL(
            "INSERT OR REPLACE INTO `app_settings` (`key`,`value`,`updatedAtUtc`) VALUES (?,?,?)",
            arrayOf<Any?>(key, value, System.currentTimeMillis())
        )
    }

    private fun checksumEntry(bytes: ByteArray) = JSONObject().put("sizeBytes", bytes.size).put("sha256", bytes.sha256())

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }

    private fun putZipEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    companion object {
        private const val blobKey = "__vehicle_tracker_base64_blob"
        private val TABLES = listOf(
            "vehicles", "vehicle_specifications", "specification_sources", "vehicle_specification_overrides",
            "odometer_readings", "oil_changes", "fuel_fill_ups", "receipt_attachments", "app_settings"
        )
        private val DELETE_ORDER = listOf(
            "receipt_attachments", "oil_changes", "fuel_fill_ups", "odometer_readings",
            "vehicle_specification_overrides", "specification_sources", "vehicles",
            "vehicle_specifications", "app_settings"
        )
        private val INSERT_ORDER = listOf(
            "vehicle_specifications", "specification_sources", "vehicles", "vehicle_specification_overrides",
            "odometer_readings", "oil_changes", "fuel_fill_ups", "receipt_attachments", "app_settings"
        )
    }
}
