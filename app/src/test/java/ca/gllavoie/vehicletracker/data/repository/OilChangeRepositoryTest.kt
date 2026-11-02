package ca.gllavoie.vehicletracker.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ca.gllavoie.vehicletracker.data.local.OilChangeEntity
import ca.gllavoie.vehicletracker.data.local.VehicleDatabase
import ca.gllavoie.vehicletracker.data.local.VehicleEntity
import java.io.File
import java.time.LocalDate
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
class OilChangeRepositoryTest {
    private lateinit var context: Context
    private lateinit var dbFile: File
    private lateinit var database: VehicleDatabase
    private lateinit var vehicleRepository: RoomVehicleRepository
    private lateinit var repository: RoomOilChangeRepository
    private val vehicleId = "oil-change-vehicle"
    private val today = LocalDate.parse("2026-10-12")

    @Before fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        dbFile = File(context.cacheDir, "oil-test-${System.nanoTime()}.db")
        database = Room.databaseBuilder(context, VehicleDatabase::class.java, dbFile.absolutePath).build()
        vehicleRepository = RoomVehicleRepository(database.vehicleDao())
        vehicleRepository.save(VehicleEntity(id = vehicleId, make = "Honda", model = "Civic", createdAtUtc = 1, updatedAtUtc = 1))
        repository = RoomOilChangeRepository(database.vehicleDao(), vehicleRepository) { today }
    }

    @After fun tearDown() {
        database.close()
        dbFile.delete()
        File(dbFile.path + "-shm").delete()
        File(dbFile.path + "-wal").delete()
    }

    @Test fun savesOilServiceSnapshotAndIntervalOverrides() = runBlocking {
        repository.save(service(id = "oil-1", oilBrand = " Example ", viscosityGrade = "5w-30", distanceIntervalOverride = 8_000, timeIntervalMonthsOverride = 6))
        val saved = repository.observeRecords(vehicleId).first().single()
        assertEquals("Example", saved.oilBrand)
        assertEquals("5W-30", saved.viscosityGrade)
        assertEquals(8_000, saved.distanceIntervalOverride)
        assertEquals(6, saved.timeIntervalMonthsOverride)
        assertEquals("CAD", saved.currencyCode)
        assertEquals(7_500, saved.quantityUsedMl)
        assertTrue(!saved.odometerReadingId.isNullOrBlank())
        val reading = database.vehicleDao().observeOdometerReadings(vehicleId).first().single()
        assertEquals(saved.odometerReadingId, reading.id)
        assertEquals(saved.id, reading.sourceRecordId)
        assertEquals(saved.odometerValue, reading.value)
    }

    @Test fun editsExistingServiceWithoutChangingIdentityOrCreationTime() = runBlocking {
        repository.save(service(id = "oil-1", createdAtUtc = 10))
        repository.save(service(id = "oil-1", notes = "Corrected invoice", createdAtUtc = 10, updatedAtUtc = 20))
        val saved = repository.observeRecords(vehicleId).first().single()
        assertEquals("oil-1", saved.id)
        assertEquals(10L, saved.createdAtUtc)
        assertEquals(20L, saved.updatedAtUtc)
        assertEquals("Corrected invoice", saved.notes)
        val linkedReading = database.vehicleDao().observeOdometerReadings(vehicleId).first().single()
        assertEquals(saved.id, linkedReading.sourceRecordId)
        assertEquals(saved.odometerValue, linkedReading.value)
    }

    @Test fun rejectsInvalidDateNegativeCostAndWrongDistanceUnit() = runBlocking {
        for (record in listOf(
            service(serviceDate = "2026-02-30"),
            service(totalCostMinor = -1),
            service(odometerUnit = "MILE")
        )) {
            try {
                repository.save(record)
                error("Invalid record must be rejected")
            } catch (_: IllegalArgumentException) {
                assertTrue(repository.observeRecords(vehicleId).first().isEmpty())
            }
        }
    }

    @Test fun rejectsFutureServiceAndArchivedVehicle() = runBlocking {
        try {
            repository.save(service(serviceDate = today.plusDays(1).toString()))
            error("Future service date must be rejected")
        } catch (_: IllegalArgumentException) {
            assertTrue(repository.observeRecords(vehicleId).first().isEmpty())
        }
        vehicleRepository.archive(vehicleId, 100)
        try {
            repository.save(service())
            error("Archived vehicle service must be rejected")
        } catch (_: IllegalArgumentException) {
            assertTrue(repository.observeRecords(vehicleId).first().isEmpty())
        }
    }

    private fun service(
        id: String = "oil-test",
        serviceDate: String = today.toString(),
        odometerValue: Long = 80_000,
        odometerUnit: String = "KM",
        oilBrand: String? = "Example",
        viscosityGrade: String? = "5W-30",
        quantityUsedMl: Int? = 7_500,
        totalCostMinor: Long? = 12_345,
        distanceIntervalOverride: Int? = null,
        timeIntervalMonthsOverride: Int? = null,
        notes: String? = null,
        createdAtUtc: Long = 10,
        updatedAtUtc: Long = 10
    ) = OilChangeEntity(
        id = id,
        vehicleId = vehicleId,
        serviceDate = serviceDate,
        odometerValue = odometerValue,
        odometerUnit = odometerUnit,
        oilBrand = oilBrand,
        viscosityGrade = viscosityGrade,
        quantityUsedMl = quantityUsedMl,
        totalCostMinor = totalCostMinor,
        distanceIntervalOverride = distanceIntervalOverride,
        timeIntervalMonthsOverride = timeIntervalMonthsOverride,
        notes = notes,
        createdAtUtc = createdAtUtc,
        updatedAtUtc = updatedAtUtc
    )
}
