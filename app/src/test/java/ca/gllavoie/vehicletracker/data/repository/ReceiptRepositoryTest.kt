package ca.gllavoie.vehicletracker.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ca.gllavoie.vehicletracker.data.local.OilChangeEntity
import ca.gllavoie.vehicletracker.data.local.VehicleDatabase
import ca.gllavoie.vehicletracker.data.local.VehicleEntity
import java.io.ByteArrayInputStream
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReceiptRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: VehicleDatabase
    private lateinit var dbFile: File
    private lateinit var storageRoot: File
    private lateinit var repository: ReceiptRepository
    private lateinit var oilRepository: RoomOilChangeRepository
    private val vehicleId = "receipt-vehicle"
    private val serviceId = "receipt-service"

    @Before fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        dbFile = File(context.cacheDir, "receipt-test-${System.nanoTime()}.db")
        storageRoot = File(context.cacheDir, "receipts-${System.nanoTime()}")
        database = Room.databaseBuilder(context, VehicleDatabase::class.java, dbFile.absolutePath).build()
        val vehicleRepository = RoomVehicleRepository(database.vehicleDao())
        vehicleRepository.save(VehicleEntity(id = vehicleId, make = "Honda", model = "Civic", createdAtUtc = 1, updatedAtUtc = 1))
        oilRepository = RoomOilChangeRepository(database.vehicleDao(), vehicleRepository)
        oilRepository.save(OilChangeEntity(
            id = serviceId, vehicleId = vehicleId, serviceDate = LocalDate.now().minusDays(2).toString(),
            odometerValue = 50_000, odometerUnit = "KM", createdAtUtc = 10, updatedAtUtc = 10
        ))
        repository = ReceiptRepository(context, database, storageRoot)
    }

    @After fun tearDown() {
        database.close()
        dbFile.delete()
        File(dbFile.path + "-shm").delete()
        File(dbFile.path + "-wal").delete()
        storageRoot.deleteRecursively()
    }

    @Test fun storesChecksumsAndRetrievesPrivateFilesWithoutRemovingService() = runBlocking {
        val bytes = "%PDF-1.4\nreceipt data".toByteArray()
        val saved = repository.addForOilChange(vehicleId, serviceId, "../../invoice.pdf", "application/pdf", ByteArrayInputStream(bytes))
        assertEquals("invoice.pdf", saved.originalFileName)
        assertEquals("application/pdf", saved.mimeType)
        assertEquals(bytes.size.toLong(), saved.sizeBytes)
        assertTrue(saved.internalRelativePath.startsWith("receipts/"))
        val verified = repository.getVerifiedFile(saved.id)
        assertArrayEquals(bytes, verified.readBytes())

        repository.delete(saved.id)
        assertTrue(database.vehicleDao().observeAttachments(serviceId).first().isEmpty())
        assertTrue(oilRepository.observeRecords(vehicleId).first().any { it.id == serviceId })
        assertFalse(verified.exists())
    }

    @Test fun vehicleDocumentsArePrivateUnlinkedAndFilteredFromServiceReceipts() = runBlocking {
        val bytes = "%PDF-1.4\\ninsurance policy".toByteArray()
        val saved = repository.addForVehicle(vehicleId, "INSURANCE", "policy.pdf", "application/pdf", ByteArrayInputStream(bytes))

        assertEquals("INSURANCE", saved.documentType)
        assertNull(saved.oilChangeId)
        assertNull(saved.fuelFillUpId)
        assertEquals(listOf(saved.id), repository.observeVehicleDocuments(vehicleId).first().map { it.id })
        assertTrue(repository.observeForService(serviceId).first().isEmpty())
        assertArrayEquals(bytes, repository.getVerifiedFile(saved.id).readBytes())
    }

    @Test fun acceptsCommonPhotoPickerImageSignatures() = runBlocking {
        val pngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val fixtures = listOf(
            Triple("photo.jpg", "image/jpeg", byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00)),
            Triple("photo.png", "image/png", pngSignature),
            Triple("photo.webp", "image/webp", "RIFF0000WEBP".toByteArray()),
            Triple("photo.gif", "image/gif", "GIF89a".toByteArray()),
            Triple("photo.heic", "image/heic", byteArrayOf(0, 0, 0, 0) + "ftypheic".toByteArray()),
            Triple("photo.heif", "image/heif", byteArrayOf(0, 0, 0, 0) + "ftypmif1".toByteArray()),
            Triple("photo.avif", "image/avif", byteArrayOf(0, 0, 0, 0) + "ftypavif".toByteArray())
        )

        fixtures.forEach { (name, mime, bytes) ->
            val saved = repository.addForVehicle(vehicleId, "OTHER", name, mime, ByteArrayInputStream(bytes))
            assertEquals(mime, saved.mimeType)
            assertTrue(saved.internalRelativePath.endsWith(".${name.substringAfterLast('.')}"))
            assertArrayEquals(bytes, repository.getVerifiedFile(saved.id).readBytes())
        }
    }

    @Test fun rejectsUnsupportedContentMimeMismatchAndOversizeFiles() = runBlocking {
        assertRejected("text.txt", "text/plain", "not a receipt".toByteArray())
        assertRejected("invoice.pdf", "image/png", "%PDF-1.4\ndata".toByteArray())
        val limited = ReceiptRepository(context, database, storageRoot, maxFileBytes = 8)
        try {
            limited.addForOilChange(vehicleId, serviceId, "large.pdf", "application/pdf", ByteArrayInputStream("%PDF-1.4 too much".toByteArray()))
            error("Oversized receipt must be rejected")
        } catch (_: IllegalArgumentException) {
            assertTrue(database.vehicleDao().observeAttachments(serviceId).first().isEmpty())
        }
    }

    @Test fun receiptMustBelongToTheSpecifiedVehicleAndService() = runBlocking {
        try {
            repository.addForOilChange("other-vehicle", serviceId, "invoice.pdf", "application/pdf", ByteArrayInputStream("%PDF-1.4".toByteArray()))
            error("Cross-vehicle receipt must be rejected")
        } catch (_: IllegalArgumentException) {
            assertTrue(database.vehicleDao().observeAttachments(serviceId).first().isEmpty())
        }
    }

    private suspend fun assertRejected(name: String, mime: String, bytes: ByteArray) {
        try {
            repository.addForOilChange(vehicleId, serviceId, name, mime, ByteArrayInputStream(bytes))
            error("Invalid file must be rejected")
        } catch (_: IllegalArgumentException) {
            assertTrue(database.vehicleDao().observeAttachments(serviceId).first().isEmpty())
        }
    }
}
