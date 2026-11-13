package ca.gllavoie.vehicletracker.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import ca.gllavoie.vehicletracker.data.local.ReceiptAttachmentEntity
import ca.gllavoie.vehicletracker.data.local.VehicleDatabase
import ca.gllavoie.vehicletracker.domain.document.VehicleDocumentType
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ReceiptRepository(
    private val context: Context,
    private val database: VehicleDatabase,
    private val storageRoot: File = File(context.filesDir, "receipts"),
    private val maxFileBytes: Long = 10L * 1024L * 1024L
) {
    fun observeForService(recordId: String): Flow<List<ReceiptAttachmentEntity>> = database.vehicleDao().observeAttachments(recordId)

    fun observeVehicleDocuments(vehicleId: String): Flow<List<ReceiptAttachmentEntity>> =
        database.vehicleDao().observeVehicleAttachments(vehicleId).map { attachments ->
            attachments.filter { it.documentType != VehicleDocumentType.RECEIPT.storedValue }
        }

    suspend fun addForOilChange(
        vehicleId: String,
        oilChangeId: String,
        sourceUri: Uri,
        displayName: String
    ): ReceiptAttachmentEntity {
        val mime = context.contentResolver.getType(sourceUri)
        val stream = context.contentResolver.openInputStream(sourceUri)
            ?: throw IOException("The selected receipt could not be opened.")
        return stream.use { addForOilChange(vehicleId, oilChangeId, displayName, mime, it) }
    }

    suspend fun addForOilChange(
        vehicleId: String,
        oilChangeId: String,
        displayName: String,
        declaredMimeType: String?,
        input: InputStream
    ): ReceiptAttachmentEntity = withContext(Dispatchers.IO) {
        require(displayName.isNotBlank()) { "Receipt filename is required." }
        val oilChange = requireNotNull(database.vehicleDao().getOilChange(oilChangeId)) { "Oil change was not found." }
        require(oilChange.vehicleId == vehicleId) { "Receipt vehicle does not match the service record." }
        val safeName = sanitizeOriginalName(displayName)
        val buffered = if (input.markSupported()) input else BufferedInputStream(input)
        buffered.mark(16)
        val signature = ByteArray(12)
        val read = buffered.read(signature)
        buffered.reset()
        val actualMime = detectMime(signature.copyOf(maxOf(0, read)))
        require(actualMime != null) { "Only PDF, JPEG, PNG, WebP, GIF, HEIC, HEIF or AVIF receipts are supported." }
        require(declaredMimeType.isNullOrBlank() || declaredMimeType == "application/octet-stream" || declaredMimeType == actualMime) {
            "Receipt file type does not match its content."
        }

        storageRoot.mkdirs()
        require(storageRoot.isDirectory) { "Receipt storage is not available." }
        val extension = when (actualMime) {
            "application/pdf" -> "pdf"
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/heic" -> "heic"
            "image/heif" -> "heif"
            "image/avif" -> "avif"
            else -> error("Unsupported receipt type.")
        }
        val internalName = "${UUID.randomUUID()}.$extension"
        val temp = File(storageRoot, ".$internalName.part")
        val destination = File(storageRoot, internalName)
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        try {
            FileOutputStream(temp).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = buffered.read(buffer)
                    if (count < 0) break
                    size = Math.addExact(size, count.toLong())
                    require(size <= maxFileBytes) { "Receipt exceeds the ${maxFileBytes / (1024 * 1024)} MB size limit." }
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            }
            require(size > 0) { "Receipt file is empty." }
            if (!temp.renameTo(destination)) throw IOException("Could not move the receipt into persistent storage.")
            val metadata = ReceiptAttachmentEntity(
                vehicleId = vehicleId,
                oilChangeId = oilChangeId,
                internalRelativePath = "receipts/$internalName",
                originalFileName = safeName,
                mimeType = actualMime,
                sizeBytes = size,
                sha256 = digest.digest().toHex(),
                addedAtUtc = System.currentTimeMillis()
            )
            try {
                database.vehicleDao().insertAttachment(metadata)
            } catch (error: Exception) {
                destination.delete()
                throw error
            }
            metadata
        } catch (error: Exception) {
            temp.delete()
            destination.delete()
            throw error
        }
    }

    suspend fun addForVehicle(
        vehicleId: String,
        documentType: String,
        sourceUri: Uri,
        displayName: String
    ): ReceiptAttachmentEntity {
        val mime = context.contentResolver.getType(sourceUri)
        val stream = context.contentResolver.openInputStream(sourceUri)
            ?: throw IOException("The selected document could not be opened.")
        return stream.use { addForVehicle(vehicleId, documentType, displayName, mime, it) }
    }

    suspend fun addForVehicle(
        vehicleId: String,
        documentType: String,
        displayName: String,
        declaredMimeType: String?,
        input: InputStream
    ): ReceiptAttachmentEntity = withContext(Dispatchers.IO) {
        val type = VehicleDocumentType.entries.firstOrNull { it.storedValue == documentType }
            ?: throw IllegalArgumentException("Choose a valid document type.")
        require(type != VehicleDocumentType.RECEIPT) { "Choose an insurance, service, registration, warranty, or other vehicle document type." }
        val vehicle = requireNotNull(database.vehicleDao().getVehicle(vehicleId)) { "Vehicle was not found." }
        require(vehicle.archivedAtUtc == null) { "Restore this vehicle before adding documents." }
        require(displayName.isNotBlank()) { "Document filename is required." }
        val safeName = sanitizeOriginalName(displayName)
        val buffered = if (input.markSupported()) input else BufferedInputStream(input)
        buffered.mark(16)
        val signature = ByteArray(12)
        val read = buffered.read(signature)
        buffered.reset()
        val actualMime = detectMime(signature.copyOf(maxOf(0, read)))
        require(actualMime != null) { "Only PDF, JPEG, PNG, WebP, GIF, HEIC, HEIF or AVIF documents are supported." }
        require(declaredMimeType.isNullOrBlank() || declaredMimeType == "application/octet-stream" || declaredMimeType == actualMime) {
            "Document file type does not match its content."
        }

        storageRoot.mkdirs()
        require(storageRoot.isDirectory) { "Document storage is not available." }
        val extension = when (actualMime) {
            "application/pdf" -> "pdf"
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/heic" -> "heic"
            "image/heif" -> "heif"
            "image/avif" -> "avif"
            else -> error("Unsupported document type.")
        }
        val internalName = "${UUID.randomUUID()}.$extension"
        val temp = File(storageRoot, ".$internalName.part")
        val destination = File(storageRoot, internalName)
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        try {
            FileOutputStream(temp).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = buffered.read(buffer)
                    if (count < 0) break
                    size = Math.addExact(size, count.toLong())
                    require(size <= maxFileBytes) { "Document exceeds the ${maxFileBytes / (1024 * 1024)} MB size limit." }
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            }
            require(size > 0) { "Document file is empty." }
            if (!temp.renameTo(destination)) throw IOException("Could not move the document into persistent storage.")
            val metadata = ReceiptAttachmentEntity(
                vehicleId = vehicleId,
                internalRelativePath = "receipts/$internalName",
                originalFileName = safeName,
                mimeType = actualMime,
                sizeBytes = size,
                sha256 = digest.digest().toHex(),
                addedAtUtc = System.currentTimeMillis(),
                documentType = type.storedValue
            )
            try {
                database.vehicleDao().insertAttachment(metadata)
            } catch (error: Exception) {
                destination.delete()
                throw error
            }
            metadata
        } catch (error: Exception) {
            temp.delete()
            destination.delete()
            throw error
        }
    }

    suspend fun getVerifiedFile(attachmentId: String): File = withContext(Dispatchers.IO) {
        val metadata = requireNotNull(database.vehicleDao().getAttachment(attachmentId)) { "Receipt was not found." }
        val file = resolveStoragePath(metadata.internalRelativePath)
        require(file.isFile) { "Receipt file is missing from storage." }
        require(file.length() == metadata.sizeBytes) { "Receipt size does not match its saved metadata." }
        require(sha256(file) == metadata.sha256) { "Receipt checksum verification failed." }
        file
    }

    suspend fun delete(attachmentId: String) = withContext(Dispatchers.IO) {
        val metadata = requireNotNull(database.vehicleDao().getAttachment(attachmentId)) { "Receipt was not found." }
        val file = resolveStoragePath(metadata.internalRelativePath)
        val staged = File(file.parentFile, ".${file.name}.${UUID.randomUUID()}.delete")
        if (file.exists() && !file.renameTo(staged)) throw IOException("Receipt could not be staged for removal.")
        try {
            database.withTransaction {
                check(database.vehicleDao().deleteAttachment(attachmentId) == 1) { "Receipt metadata could not be removed." }
            }
            staged.delete()
        } catch (error: Exception) {
            if (staged.exists()) staged.renameTo(file)
            throw error
        }
    }

    suspend fun deleteStoredFiles(attachments: List<ReceiptAttachmentEntity>): Int = withContext(Dispatchers.IO) {
        var failures = 0
        attachments.forEach { attachment ->
            try {
                val file = resolveStoragePath(attachment.internalRelativePath)
                if (file.exists() && !file.delete()) failures += 1
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                failures += 1
            }
        }
        failures
    }


    private fun resolveStoragePath(relativePath: String): File {
        require(relativePath.startsWith("receipts/") && !relativePath.startsWith('/') && !relativePath.contains('\\')) {
            "Receipt path is invalid."
        }
        val fileName = relativePath.removePrefix("receipts/")
        require(fileName.isNotBlank() && !fileName.contains('/') && fileName != "." && fileName != "..") {
            "Receipt path is invalid."
        }
        val root = storageRoot.canonicalFile
        val file = File(root, fileName).canonicalFile
        require(file.path.startsWith(root.path + File.separator)) { "Receipt path escapes private storage." }
        return file
    }

    private fun sanitizeOriginalName(name: String): String {
        val basename = name.substringAfterLast('/').substringAfterLast('\\')
        val cleaned = basename.filter { it.code >= 32 && it != '/' && it != '\\' }.trim().take(180)
        require(cleaned.isNotBlank() && cleaned != "." && cleaned != "..") { "Receipt filename is invalid." }
        return cleaned
    }

    private fun detectMime(bytes: ByteArray): String? {
        val ascii = bytes.toString(Charsets.US_ASCII)
        return when {
            bytes.size >= 5 && ascii.startsWith("%PDF-") -> "application/pdf"
            bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "image/jpeg"
            bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) -> "image/png"
            bytes.size >= 6 && (ascii.startsWith("GIF87a") || ascii.startsWith("GIF89a")) -> "image/gif"
            bytes.size >= 12 && ascii.startsWith("RIFF") && ascii.substring(8, 12) == "WEBP" -> "image/webp"
            bytes.size >= 12 && ascii.substring(4, 8) == "ftyp" -> when (ascii.substring(8, 12)) {
                "avif", "avis" -> "image/avif"
                "heic", "heix", "hevc", "hevx" -> "image/heic"
                "heif", "mif1", "msf1" -> "image/heif"
                else -> null
            }
            else -> null
        }
    }

    private fun sha256(file: File): String = FileInputStream(file).use { stream ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
