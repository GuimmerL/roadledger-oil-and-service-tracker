package ca.gllavoie.vehicletracker.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ca.gllavoie.vehicletracker.data.local.SpecificationSourceEntity
import ca.gllavoie.vehicletracker.data.local.VehicleDatabase
import ca.gllavoie.vehicletracker.data.local.VehicleEntity
import ca.gllavoie.vehicletracker.data.local.VehicleSpecificationEntity
import ca.gllavoie.vehicletracker.data.local.VehicleSpecificationOverrideEntity
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VehicleSpecificationRepositoryTest {
    private lateinit var context: Context
    private lateinit var dbFile: File
    private lateinit var database: VehicleDatabase
    private lateinit var vehicleRepository: RoomVehicleRepository
    private lateinit var repository: RoomVehicleSpecificationRepository

    @Before fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        dbFile = File(context.cacheDir, "specification-test-${System.nanoTime()}.db")
        database = Room.databaseBuilder(context, VehicleDatabase::class.java, dbFile.absolutePath).build()
        vehicleRepository = RoomVehicleRepository(database.vehicleDao())
        vehicleRepository.save(VehicleEntity(id = "spec-car", make = "Mazda", model = "3", createdAtUtc = 1, updatedAtUtc = 1))
        repository = RoomVehicleSpecificationRepository(database.vehicleDao(), vehicleRepository)
    }

    @After fun tearDown() {
        database.close()
        dbFile.delete()
        File(dbFile.path + "-shm").delete()
        File(dbFile.path + "-wal").delete()
    }

    @Test fun catalogEditsStayUnverifiedUntilAValidOfficialSourceIsEntered() = runBlocking {
        val aiEntry = VehicleSpecificationEntity(
            id = "catalog-entry", make = "Mazda", model = "3", modelYear = 2020, engine = "2.0L I4",
            serviceNotes = "Original AI row; verify this.", verificationStatus = "AI_GENERATED_UNVERIFIED",
            createdAtUtc = 1, updatedAtUtc = 1
        )
        repository.saveCatalogEntry(aiEntry)
        val userEdit = requireNotNull(repository.getCatalogEntry(aiEntry.id)).copy(
            viscosityGrade = "0W-20", verificationStatus = "USER_ENTERED_UNVERIFIED", updatedAtUtc = 2
        )
        repository.saveCatalogEntry(userEdit)
        assertEquals("USER_ENTERED_UNVERIFIED", repository.getCatalogEntry(aiEntry.id)?.verificationStatus)
        assertEquals("Original AI row; verify this.", repository.getCatalogEntry(aiEntry.id)?.serviceNotes)

        try {
            repository.saveCatalogEntry(userEdit.copy(verificationStatus = "USER_VERIFIED", updatedAtUtc = 3))
            error("Verified data must require a source")
        } catch (_: IllegalArgumentException) {
            assertEquals("USER_ENTERED_UNVERIFIED", repository.getCatalogEntry(aiEntry.id)?.verificationStatus)
        }

        val verifiedSource = SpecificationSourceEntity(
            id = "catalog-source", specificationId = aiEntry.id, sourceName = "OEM service manual",
            sourceUrl = "https://manufacturer.example/service", verifiedOn = "2026-10-22",
            verificationStatus = "USER_VERIFIED"
        )
        repository.saveCatalogEntry(
            userEdit.copy(verificationStatus = "USER_VERIFIED", verifiedOn = "2026-10-22", updatedAtUtc = 4),
            verifiedSource
        )
        assertEquals("USER_VERIFIED", repository.getCatalogEntry(aiEntry.id)?.verificationStatus)
        assertEquals(verifiedSource.sourceUrl, repository.getSources(aiEntry.id).first { it.id == verifiedSource.id }.sourceUrl)
    }

    @Test fun savesSourceNotedVehicleSpecificRecommendations() = runBlocking {
        repository.saveOverride(
            VehicleSpecificationOverrideEntity(
                vehicleId = "spec-car", viscosityGrade = "0w-20", manufacturerApproval = "OEM A",
                capacityWithFilterMl = 4_400, distanceInterval = 8_000, distanceUnit = "KM",
                timeIntervalMonths = 6, oilFilterPartNumber = "ABC-123", sourceDescription = "2024 owner's manual · page 312",
                updatedAtUtc = 10
            )
        )
        val saved = repository.observeOverride("spec-car").first()!!
        assertEquals("0W-20", saved.viscosityGrade)
        assertEquals("OEM A", saved.manufacturerApproval)
        assertEquals(4_400, saved.capacityWithFilterMl)
        assertEquals("KM", saved.distanceUnit)
        assertEquals("2024 owner's manual · page 312", saved.sourceDescription)
    }

    @Test fun updatesTheSameOverrideAndRejectsNonPositiveIntervals() = runBlocking {
        repository.saveOverride(VehicleSpecificationOverrideEntity(vehicleId = "spec-car", distanceInterval = 8_000, distanceUnit = "KM", updatedAtUtc = 10))
        val first = repository.observeOverride("spec-car").first()!!
        repository.saveOverride(first.copy(timeIntervalMonths = 6, updatedAtUtc = 20))
        val updated = repository.observeOverride("spec-car").first()!!
        assertEquals(first.id, updated.id)
        assertEquals(8_000, updated.distanceInterval)
        assertEquals(6, updated.timeIntervalMonths)
        try {
            repository.saveOverride(updated.copy(distanceInterval = 0, updatedAtUtc = 30))
            error("Invalid intervals must be rejected")
        } catch (_: IllegalArgumentException) {
            assertEquals(updated, repository.observeOverride("spec-car").first())
        }
    }

    @Test fun rejectsUnitMismatchAndArchivedVehicle() = runBlocking {
        try {
            repository.saveOverride(VehicleSpecificationOverrideEntity(vehicleId = "spec-car", distanceInterval = 5_000, distanceUnit = "MILE", updatedAtUtc = 1))
            error("Distance unit mismatch must be rejected")
        } catch (_: IllegalArgumentException) {
            assertTrue(repository.observeOverride("spec-car").first() == null)
        }
        vehicleRepository.archive("spec-car", 10)
        try {
            repository.saveOverride(VehicleSpecificationOverrideEntity(vehicleId = "spec-car", viscosityGrade = "5W-30", updatedAtUtc = 2))
            error("Archived vehicle override must be rejected")
        } catch (_: IllegalArgumentException) {
            assertTrue(repository.observeOverride("spec-car").first() == null)
        }
    }
}
