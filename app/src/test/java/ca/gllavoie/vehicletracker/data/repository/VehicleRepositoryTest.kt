package ca.gllavoie.vehicletracker.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import ca.gllavoie.vehicletracker.data.local.VehicleDatabase
import ca.gllavoie.vehicletracker.data.local.FuelFillUpEntity
import ca.gllavoie.vehicletracker.data.local.OdometerReadingEntity
import ca.gllavoie.vehicletracker.data.local.OilChangeEntity
import ca.gllavoie.vehicletracker.data.local.ReceiptAttachmentEntity
import ca.gllavoie.vehicletracker.data.local.VehicleEntity
import ca.gllavoie.vehicletracker.data.local.VehicleSpecificationEntity
import ca.gllavoie.vehicletracker.data.local.VehicleSpecificationOverrideEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VehicleRepositoryTest {
    private lateinit var context: Context
    private lateinit var databaseFile: File
    private lateinit var database: VehicleDatabase
    private lateinit var repository: RoomVehicleRepository

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseFile = File(context.cacheDir, "vehicle-repository-test-${System.nanoTime()}.db")
        database = openDatabase()
        repository = RoomVehicleRepository(database.vehicleDao())
    }

    @After fun tearDown() {
        database.close()
        databaseFile.delete()
        File(databaseFile.path + "-shm").delete()
        File(databaseFile.path + "-wal").delete()
    }

    @Test fun savesAndPersistsVehiclesAcrossDatabaseRestart() = runBlocking {
        val vehicle = vehicle(id = "vehicle-a")
        repository.save(vehicle)
        assertEquals(vehicle, repository.getVehicle(vehicle.id))
        database.close()
        database = openDatabase()
        repository = RoomVehicleRepository(database.vehicleDao())
        assertNotNull(repository.getVehicle(vehicle.id))
        assertEquals(1, repository.observeActiveVehicles().first().size)
    }

    @Test fun vehiclesRemainIsolatedAndArchiveCanBeRestored() = runBlocking {
        repository.save(vehicle(id = "one", make = "Toyota", model = "Prius"))
        repository.save(vehicle(id = "two", make = "Lexus", model = "CT 200h"))
        assertEquals(listOf("one", "two"), repository.observeActiveVehicles().first().map { it.id }.sorted())
        assertTrue(repository.archive("one", 50L))
        assertEquals(listOf("two"), repository.observeActiveVehicles().first().map { it.id })
        assertEquals(listOf("one"), repository.observeArchivedVehicles().first().map { it.id })
        assertTrue(repository.restore("one", 60L))
        assertEquals(2, repository.observeActiveVehicles().first().size)
        assertFalse(repository.archive("missing", 70L))
        assertFalse(repository.restore("one", 70L))
    }

    @Test fun editsNormalizeMakeAndModelWithoutChangingIdentity() = runBlocking {
        repository.save(vehicle(id = "editable"))
        val original = requireNotNull(repository.getVehicle("editable"))
        repository.save(original.copy(make = "  HONDA ", model = " Civic ", updatedAtUtc = 101L))
        val updated = requireNotNull(repository.getVehicle("editable"))
        assertEquals("editable", updated.id)
        assertEquals("HONDA", updated.make)
        assertEquals("Civic", updated.model)
        assertEquals(101L, updated.updatedAtUtc)
    }

    @Test fun invalidVehiclesAreRejectedBeforeWriting() = runBlocking {
        assertRejected(vehicle(id = "blank-make", make = " "))
        assertRejected(vehicle(id = "blank-model", model = "\t"))
        assertRejected(vehicle(id = "invalid-year", modelYear = 1885))
        assertRejected(vehicle(id = "invalid-distance-unit").copy(odometerUnit = "YARDS"))
        assertRejected(vehicle(id = "invalid-interval-mode").copy(oilIntervalMode = "EXTREME"))
        assertNull(repository.getVehicle("blank-make"))
    }

    @Test fun severeIntervalModePersistsPerVehicle() = runBlocking {
        repository.save(vehicle(id = "severe-mode").copy(oilIntervalMode = "SEVERE"))

        assertEquals("SEVERE", repository.getVehicle("severe-mode")?.oilIntervalMode)
    }

    @Test fun invalidUpdateDoesNotOverwriteExistingVehicle() = runBlocking {
        repository.save(vehicle(id = "valid"))
        val original = requireNotNull(repository.getVehicle("valid"))
        try {
            repository.save(original.copy(model = ""))
            error("Expected validation failure")
        } catch (_: IllegalArgumentException) {
            assertEquals(original, repository.getVehicle("valid"))
        }
    }

    @Test fun permanentDeletionRemovesVehicleHistoryAndReceiptFilesButKeepsSharedCatalog() = runBlocking {
        val specification = VehicleSpecificationEntity(
            id = "shared-spec", make = "Toyota", model = "Prius", createdAtUtc = 1L, updatedAtUtc = 1L
        )
        database.vehicleDao().insertSpecification(specification)
        repository.save(vehicle(id = "delete-me").copy(defaultSpecificationId = specification.id))
        database.vehicleDao().insertOdometerReading(
            OdometerReadingEntity(id = "delete-odo", vehicleId = "delete-me", value = 12_000L, unit = "KM", recordedAtUtc = 10L, sourceType = "MANUAL", createdAtUtc = 10L, updatedAtUtc = 10L)
        )
        database.vehicleDao().insertOilChange(
            OilChangeEntity(id = "delete-oil", vehicleId = "delete-me", serviceDate = "2026-01-01", odometerValue = 12_000L, odometerUnit = "KM", createdAtUtc = 10L, updatedAtUtc = 10L)
        )
        database.vehicleDao().insertFuelFillUp(
            FuelFillUpEntity(id = "delete-fuel", vehicleId = "delete-me", filledAtUtc = 10L, odometerValue = 12_000L, odometerUnit = "KM", volumeMillilitres = 40_000L, isFullTank = true, createdAtUtc = 10L, updatedAtUtc = 10L)
        )
        database.vehicleDao().upsertSpecificationOverride(
            VehicleSpecificationOverrideEntity(id = "delete-override", vehicleId = "delete-me", viscosityGrade = "5W-30", updatedAtUtc = 10L)
        )
        val storageRoot = File(context.cacheDir, "delete-receipts-${System.nanoTime()}").apply { mkdirs() }
        val receiptFile = File(storageRoot, "receipt.pdf").apply { writeBytes("receipt".toByteArray()) }
        val insuranceFile = File(storageRoot, "insurance.pdf").apply { writeBytes("policy".toByteArray()) }
        database.vehicleDao().insertAttachment(
            ReceiptAttachmentEntity(
                id = "delete-receipt", vehicleId = "delete-me", oilChangeId = "delete-oil",
                internalRelativePath = "receipts/receipt.pdf", originalFileName = "receipt.pdf",
                mimeType = "application/pdf", sizeBytes = receiptFile.length(), sha256 = "unused", addedAtUtc = 10L
            )
        )
        database.vehicleDao().insertAttachment(
            ReceiptAttachmentEntity(
                id = "delete-document", vehicleId = "delete-me",
                internalRelativePath = "receipts/insurance.pdf", originalFileName = "insurance.pdf",
                mimeType = "application/pdf", sizeBytes = insuranceFile.length(), sha256 = "unused",
                addedAtUtc = 11L, documentType = "INSURANCE"
            )
        )
        val receipts = ReceiptRepository(context, database, storageRoot)

        val result = repository.deletePermanently("delete-me")

        assertTrue(result.deleted)
        assertEquals(setOf("delete-receipt", "delete-document"), result.attachments.map { it.id }.toSet())
        assertEquals(0, receipts.deleteStoredFiles(result.attachments))
        assertFalse(receiptFile.exists())
        assertFalse(insuranceFile.exists())
        assertNull(repository.getVehicle("delete-me"))
        assertTrue(database.vehicleDao().getAllOdometerReadings().isEmpty())
        assertTrue(database.vehicleDao().getAllOilChanges().isEmpty())
        assertTrue(database.vehicleDao().getAllFuelFillUps().isEmpty())
        assertTrue(database.vehicleDao().getAllSpecificationOverrides().isEmpty())
        assertTrue(database.vehicleDao().getAllAttachments().isEmpty())
        assertNotNull(database.vehicleDao().getSpecification("shared-spec"))
        storageRoot.deleteRecursively()
        Unit
    }

    @Test fun specificationForeignKeyRejectsUnknownReference() = runBlocking {
        try {
            repository.save(vehicle(id = "bad-spec", specificationId = "missing-spec"))
            error("Expected foreign-key failure")
        } catch (_: android.database.sqlite.SQLiteConstraintException) {
            assertNull(repository.getVehicle("bad-spec"))
        }
    }

    private suspend fun assertRejected(value: VehicleEntity) {
        try {
            repository.save(value)
            error("Expected invalid vehicle to be rejected")
        } catch (_: IllegalArgumentException) {
            assertNull(repository.getVehicle(value.id))
        }
    }

    private fun openDatabase(): VehicleDatabase = Room.databaseBuilder(
        context,
        VehicleDatabase::class.java,
        databaseFile.absolutePath
    ).build()

    private fun vehicle(
        id: String,
        make: String = "Toyota",
        model: String = "Prius",
        modelYear: Int? = 2020,
        specificationId: String? = null
    ) = VehicleEntity(
        id = id,
        modelYear = modelYear,
        make = make,
        model = model,
        defaultSpecificationId = specificationId,
        createdAtUtc = 100L,
        updatedAtUtc = 100L
    )
}
