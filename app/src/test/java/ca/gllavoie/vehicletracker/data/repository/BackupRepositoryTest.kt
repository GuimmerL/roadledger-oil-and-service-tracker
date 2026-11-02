package ca.gllavoie.vehicletracker.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ca.gllavoie.vehicletracker.data.local.OilChangeEntity
import ca.gllavoie.vehicletracker.data.local.ReceiptAttachmentEntity
import ca.gllavoie.vehicletracker.data.local.SpecificationSourceEntity
import ca.gllavoie.vehicletracker.data.local.VehicleDatabase
import ca.gllavoie.vehicletracker.data.local.VehicleEntity
import ca.gllavoie.vehicletracker.data.local.VehicleSpecificationEntity
import ca.gllavoie.vehicletracker.domain.document.VehicleDocumentType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupRepositoryTest {
    private lateinit var context: Context
    private lateinit var source: TestStore
    private lateinit var clean: TestStore

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        source = newStore("source")
        clean = newStore("clean")
    }

    @After fun tearDown() {
        source.close()
        clean.close()
    }

    @Test fun backupRestoresVehiclesServicesReadingsAndReceiptFilesIntoCleanStore() = runBlocking {
        source.seedWithReceipt()
        val archive = source.backupBytes()
        val preview = clean.backup.validateBackup(ByteArrayInputStream(archive))
        assertEquals(2, preview.recordCounts.getValue("vehicles"))
        assertEquals(1, preview.recordCounts.getValue("oil_changes"))
        assertEquals(1, preview.recordCounts.getValue("odometer_readings"))
        assertEquals(1, preview.attachmentCount)

        val restored = clean.backup.restoreBackup(ByteArrayInputStream(archive), confirmReplace = true)
        assertEquals(1, restored.attachmentCount)
        assertEquals("backup-car", clean.vehicleRepository.getVehicle("backup-car")?.id)
        assertEquals("backup-spec", clean.vehicleRepository.getVehicle("backup-car")?.defaultSpecificationId)
        assertEquals(75.0, clean.vehicleRepository.getVehicle("backup-car")?.startingDailyUsageKmPerDay!!, 0.0)
        assertEquals("SEVERE", clean.vehicleRepository.getVehicle("backup-car")?.oilIntervalMode)
        assertEquals("PICKUP", clean.vehicleRepository.getVehicle("backup-car")?.vehicleType)
        assertEquals("AI_GENERATED_UNVERIFIED", clean.database.vehicleDao().getSpecification("backup-spec")?.verificationStatus)
        assertEquals(1, clean.database.vehicleDao().getSpecificationSources("backup-spec").size)
        assertEquals(listOf("backup-archived"), clean.vehicleRepository.observeArchivedVehicles().first().map { it.id })
        val oil = clean.oilRepository.observeRecords("backup-car").first().single()
        assertEquals("backup-oil", oil.id)
        assertEquals(85_000L, oil.odometerValue)
        val reading = clean.database.vehicleDao().observeOdometerReadings("backup-car").first().single()
        assertEquals(oil.id, reading.sourceRecordId)
        val attachment = clean.database.vehicleDao().observeAttachments(oil.id).first().single()
        assertEquals("invoice.pdf", attachment.originalFileName)
        assertTrue(clean.receiptRepository.getVerifiedFile(attachment.id).readBytes().contentEquals(PDF_BYTES))
    }

    @Test fun backupRestoresVehicleDocumentsAndTheirClassification() = runBlocking {
        source.vehicleRepository.save(vehicle("document-car", "Toyota", "RAV4"))
        val bytes = "%PDF-1.4 vehicle insurance".toByteArray()
        val saved = source.receiptRepository.addForVehicle(
            "document-car", VehicleDocumentType.INSURANCE.storedValue, "policy.pdf", "application/pdf", ByteArrayInputStream(bytes)
        )
        val archive = source.backupBytes()

        clean.backup.restoreBackup(ByteArrayInputStream(archive), confirmReplace = true)

        val restored = clean.database.vehicleDao().getVehicleAttachments("document-car").single()
        assertEquals(VehicleDocumentType.INSURANCE.storedValue, restored.documentType)
        assertNull(restored.oilChangeId)
        assertNull(restored.fuelFillUpId)
        assertArrayEquals(bytes, clean.receiptRepository.getVerifiedFile(restored.id).readBytes())
        assertEquals(saved.originalFileName, restored.originalFileName)
    }

    @Test fun priorSchemaBackupRestoresWithEmptyStartingDailyUsage() = runBlocking {
        source.seedWithReceipt()
        val oldArchive = toVersionOneBackup(source.backupBytes())

        val preview = clean.backup.validateBackup(ByteArrayInputStream(oldArchive))
        assertEquals(2, preview.recordCounts.getValue("vehicles"))
        clean.backup.restoreBackup(ByteArrayInputStream(oldArchive), confirmReplace = true)

        assertNull(clean.vehicleRepository.getVehicle("backup-car")?.startingDailyUsageKmPerDay)
        assertEquals("NORMAL", clean.vehicleRepository.getVehicle("backup-car")?.oilIntervalMode)
        assertEquals("OTHER", clean.vehicleRepository.getVehicle("backup-car")?.vehicleType)
        assertEquals("Honda", clean.vehicleRepository.getVehicle("backup-car")?.make)
        assertEquals(1, clean.database.vehicleDao().observeAttachments("backup-oil").first().size)
    }

    @Test fun priorSchemaTwoBackupRestoresStartingUsageAndDefaultsOnlyNewIntervalMode() = runBlocking {
        source.seedWithReceipt()
        val oldArchive = toLegacyBackup(source.backupBytes(), targetVersion = 2)

        clean.backup.restoreBackup(ByteArrayInputStream(oldArchive), confirmReplace = true)

        assertEquals(75.0, clean.vehicleRepository.getVehicle("backup-car")?.startingDailyUsageKmPerDay!!, 0.0)
        assertEquals("NORMAL", clean.vehicleRepository.getVehicle("backup-car")?.oilIntervalMode)
    }

    @Test fun priorSchemaFourBackupDefaultsOldAttachmentsToServiceReceipts() = runBlocking {
        source.seedWithReceipt()
        val oldArchive = toLegacyBackup(source.backupBytes(), targetVersion = 4)

        clean.backup.restoreBackup(ByteArrayInputStream(oldArchive), confirmReplace = true)

        val attachment = clean.database.vehicleDao().getVehicleAttachments("backup-car").single()
        assertEquals(VehicleDocumentType.RECEIPT.storedValue, attachment.documentType)
    }

    @Test fun priorSchemaThreeBackupDefaultsVehicleTypeWithoutChangingOtherFields() = runBlocking {
        source.seedWithReceipt()
        val oldArchive = toLegacyBackup(source.backupBytes(), targetVersion = 3)

        clean.backup.restoreBackup(ByteArrayInputStream(oldArchive), confirmReplace = true)

        assertEquals(75.0, clean.vehicleRepository.getVehicle("backup-car")?.startingDailyUsageKmPerDay!!, 0.0)
        assertEquals("SEVERE", clean.vehicleRepository.getVehicle("backup-car")?.oilIntervalMode)
        assertEquals("OTHER", clean.vehicleRepository.getVehicle("backup-car")?.vehicleType)
    }

    @Test fun corruptedBackupIsRejectedBeforeExistingRecordsAreChanged() = runBlocking {
        source.vehicleRepository.save(vehicle("existing", "Keep", "Me"))
        source.vehicleRepository.save(vehicle("archived", "Archived", "Vehicle"))
        source.vehicleRepository.archive("archived", 20L)
        val corrupt = tamperDatabaseJson(source.backupBytes()) { databaseJson ->
            databaseJson.put("schemaVersion", databaseJson.getInt("schemaVersion") + 1)
        }
        try {
            source.backup.restoreBackup(ByteArrayInputStream(corrupt), confirmReplace = true)
            error("Corrupt backup must be rejected")
        } catch (_: IllegalArgumentException) {
            assertEquals("Keep", source.vehicleRepository.getVehicle("existing")?.make)
            assertEquals(1, source.vehicleRepository.observeActiveVehicles().first().size)
            assertEquals(1, source.vehicleRepository.observeArchivedVehicles().first().size)
        }
    }

    @Test fun repeatedReplaceRestoreDoesNotDuplicateStableRecordsOrAttachments() = runBlocking {
        source.seedWithReceipt()
        val archive = source.backupBytes()
        repeat(2) { source.backup.restoreBackup(ByteArrayInputStream(archive), confirmReplace = true) }
        assertEquals(1, source.vehicleRepository.observeActiveVehicles().first().size)
        assertEquals(1, source.oilRepository.observeRecords("backup-car").first().size)
        assertEquals(1, source.database.vehicleDao().observeOdometerReadings("backup-car").first().size)
        assertEquals(1, source.database.vehicleDao().observeAttachments("backup-oil").first().size)
        val metadata = source.database.vehicleDao().observeAttachments("backup-oil").first().single()
        assertTrue(source.receiptRepository.getVerifiedFile(metadata.id).readBytes().contentEquals(PDF_BYTES))
    }

    @Test fun failedDatabaseCommitRollsBackAndRetainsExistingGarageData() = runBlocking {
        source.vehicleRepository.save(vehicle("keep", "Existing", "Data"))
        val modified = addDuplicateUniqueOverrides(source.backupBytes(), "keep")
        var failed = false
        try {
            source.backup.restoreBackup(ByteArrayInputStream(modified), confirmReplace = true)
        } catch (_: Exception) {
            failed = true
        }
        assertTrue("The restore transaction should reject the uniqueness conflict", failed)
        assertEquals("Existing", source.vehicleRepository.getVehicle("keep")?.make)
        assertEquals(1, source.vehicleRepository.observeActiveVehicles().first().size)
    }

    @Test fun replaceRestoreRequiresExplicitConfirmation() = runBlocking {
        source.vehicleRepository.save(vehicle("keep", "Existing", "Data"))
        val archive = source.backupBytes()
        try {
            source.backup.restoreBackup(ByteArrayInputStream(archive), confirmReplace = false)
            error("Restore must require explicit confirmation")
        } catch (_: IllegalArgumentException) {
            assertEquals("Existing", source.vehicleRepository.getVehicle("keep")?.make)
        }
    }

    private fun newStore(label: String): TestStore {
        val db = File(context.cacheDir, "backup-$label-${System.nanoTime()}.db")
        val root = File(context.cacheDir, "backup-$label-receipts-${System.nanoTime()}")
        val database = Room.databaseBuilder(context, VehicleDatabase::class.java, db.absolutePath).build()
        val vehicleRepository = RoomVehicleRepository(database.vehicleDao())
        val oilRepository = RoomOilChangeRepository(database.vehicleDao(), vehicleRepository)
        val receipts = ReceiptRepository(context, database, root)
        val backup = BackupRepository(context, database, root)
        return TestStore(database, db, root, vehicleRepository, oilRepository, receipts, backup)
    }

    private fun vehicle(id: String, make: String, model: String) = VehicleEntity(
        id = id, make = make, model = model, createdAtUtc = 1L, updatedAtUtc = 1L
    )

    private suspend fun TestStore.seedWithReceipt() {
        val specification = VehicleSpecificationEntity(
            id = "backup-spec", make = "Honda", model = "Civic", modelYear = 2020,
            verificationStatus = "AI_GENERATED_UNVERIFIED", serviceNotes = "AI data; verify.", createdAtUtc = 2L, updatedAtUtc = 2L
        )
        database.vehicleDao().insertSpecification(specification)
        database.vehicleDao().upsertSpecificationSource(
            SpecificationSourceEntity(
                id = "backup-spec-source", specificationId = specification.id,
                sourceName = "AI-generated user dataset", verificationStatus = "AI_GENERATED_UNVERIFIED", notes = "Verify with OEM manual."
            )
        )
        vehicleRepository.save(
            vehicle("backup-car", "Honda", "Civic").copy(
                defaultSpecificationId = specification.id,
                startingDailyUsageKmPerDay = 75.0,
                oilIntervalMode = "SEVERE",
                vehicleType = "PICKUP"
            )
        )
        vehicleRepository.save(vehicle("backup-archived", "Ford", "Focus"))
        vehicleRepository.archive("backup-archived", 20L)
        oilRepository.save(
            OilChangeEntity(
                id = "backup-oil", vehicleId = "backup-car", serviceDate = LocalDate.now().minusDays(1).toString(),
                odometerValue = 85_000L, odometerUnit = "KM", oilBrand = "Test Oil", quantityUsedMl = 4_200,
                createdAtUtc = 10L, updatedAtUtc = 10L
            )
        )
        receiptRepository.addForOilChange("backup-car", "backup-oil", "invoice.pdf", "application/pdf", ByteArrayInputStream(PDF_BYTES))
    }

    private suspend fun TestStore.backupBytes(): ByteArray {
        val output = ByteArrayOutputStream()
        backup.createBackup(output)
        return output.toByteArray()
    }

    private fun toVersionOneBackup(archive: ByteArray): ByteArray = toLegacyBackup(archive, targetVersion = 1)

    private fun toLegacyBackup(archive: ByteArray, targetVersion: Int): ByteArray {
        val entries = readEntries(archive)
        val databaseJson = JSONObject(entries.getValue("database.json").toString(Charsets.UTF_8))
        val vehicles = databaseJson.getJSONObject("tables").getJSONObject("vehicles")
        val columns = vehicles.getJSONArray("columns")
        val removedColumns = buildSet {
            if (targetVersion < 2) add("startingDailyUsageKmPerDay")
            if (targetVersion < 3) add("oilIntervalMode")
            if (targetVersion < 4) add("vehicleType")
        }
        val removedIndices = (0 until columns.length()).filter { columns.getString(it) in removedColumns }.toSet()
        val oldColumns = JSONArray()
        for (index in 0 until columns.length()) if (index !in removedIndices) oldColumns.put(columns.getString(index))
        val oldRows = JSONArray()
        val rows = vehicles.getJSONArray("rows")
        for (rowIndex in 0 until rows.length()) {
            val oldRow = JSONArray()
            val row = rows.getJSONArray(rowIndex)
            for (columnIndex in 0 until row.length()) if (columnIndex !in removedIndices) oldRow.put(row.get(columnIndex))
            oldRows.put(oldRow)
        }
        vehicles.put("columns", oldColumns).put("rows", oldRows)
        if (targetVersion < 5) {
            val attachments = databaseJson.getJSONObject("tables").getJSONObject("receipt_attachments")
            val attachmentColumns = attachments.getJSONArray("columns")
            val documentTypeIndex = (0 until attachmentColumns.length()).firstOrNull { attachmentColumns.getString(it) == "documentType" }
            if (documentTypeIndex != null) {
                val oldAttachmentColumns = JSONArray()
                for (index in 0 until attachmentColumns.length()) if (index != documentTypeIndex) oldAttachmentColumns.put(attachmentColumns.getString(index))
                val oldAttachmentRows = JSONArray()
                val attachmentRows = attachments.getJSONArray("rows")
                for (rowIndex in 0 until attachmentRows.length()) {
                    val oldRow = JSONArray()
                    val row = attachmentRows.getJSONArray(rowIndex)
                    for (columnIndex in 0 until row.length()) if (columnIndex != documentTypeIndex) oldRow.put(row.get(columnIndex))
                    oldAttachmentRows.put(oldRow)
                }
                attachments.put("columns", oldAttachmentColumns).put("rows", oldAttachmentRows)
            }
        }
        databaseJson.put("schemaVersion", targetVersion)
        entries["database.json"] = databaseJson.toString().toByteArray(Charsets.UTF_8)

        val manifest = JSONObject(entries.getValue("manifest.json").toString(Charsets.UTF_8))
        manifest.put("databaseVersion", targetVersion)
        manifest.getJSONObject("checksums").put("database.json", JSONObject()
            .put("sizeBytes", entries.getValue("database.json").size)
            .put("sha256", entries.getValue("database.json").sha256()))
        entries["manifest.json"] = manifest.toString().toByteArray(Charsets.UTF_8)
        return writeEntries(entries)
    }

    private fun tamperDatabaseJson(archive: ByteArray, update: (JSONObject) -> Unit): ByteArray {
        val entries = readEntries(archive)
        val databaseJson = JSONObject(entries.getValue("database.json").toString(Charsets.UTF_8))
        update(databaseJson)
        entries["database.json"] = databaseJson.toString().toByteArray(Charsets.UTF_8)
        return writeEntries(entries)
    }

    private fun addDuplicateUniqueOverrides(archive: ByteArray, vehicleId: String): ByteArray {
        val entries = readEntries(archive)
        val databaseJson = JSONObject(entries.getValue("database.json").toString(Charsets.UTF_8))
        val table = databaseJson.getJSONObject("tables").getJSONObject("vehicle_specification_overrides")
        val columns = table.getJSONArray("columns")
        val rows = table.getJSONArray("rows")
        repeat(2) { index ->
            val row = JSONArray()
            for (columnIndex in 0 until columns.length()) {
                when (columns.getString(columnIndex)) {
                    "id" -> row.put("duplicate-override-$index")
                    "vehicleId" -> row.put(vehicleId)
                    "updatedAtUtc" -> row.put(100L + index)
                    else -> row.put(JSONObject.NULL)
                }
            }
            rows.put(row)
        }
        entries["database.json"] = databaseJson.toString().toByteArray(Charsets.UTF_8)
        val manifest = JSONObject(entries.getValue("manifest.json").toString(Charsets.UTF_8))
        manifest.getJSONObject("recordCounts").put("vehicle_specification_overrides", 2)
        manifest.getJSONObject("checksums").put("database.json", JSONObject()
            .put("sizeBytes", entries.getValue("database.json").size)
            .put("sha256", entries.getValue("database.json").sha256()))
        entries["manifest.json"] = manifest.toString().toByteArray(Charsets.UTF_8)
        return writeEntries(entries)
    }

    private fun readEntries(archive: ByteArray): MutableMap<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                result[entry.name] = zip.readBytes()
            }
        }
        return result
    }

    private fun writeEntries(entries: Map<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, content) -> zip.putNextEntry(ZipEntry(name)); zip.write(content); zip.closeEntry() }
        }
        return output.toByteArray()
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }

    private class TestStore(
        val database: VehicleDatabase,
        private val dbFile: File,
        private val receiptRoot: File,
        val vehicleRepository: RoomVehicleRepository,
        val oilRepository: RoomOilChangeRepository,
        val receiptRepository: ReceiptRepository,
        val backup: BackupRepository
    ) {
        fun close() {
            database.close()
            dbFile.delete()
            File(dbFile.path + "-shm").delete()
            File(dbFile.path + "-wal").delete()
            receiptRoot.deleteRecursively()
        }
    }

    companion object {
        private val PDF_BYTES = "%PDF-1.4\nbackup receipt".toByteArray()
    }
}
