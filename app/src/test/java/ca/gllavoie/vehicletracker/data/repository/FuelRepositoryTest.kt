package ca.gllavoie.vehicletracker.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ca.gllavoie.vehicletracker.data.local.FuelFillUpEntity
import ca.gllavoie.vehicletracker.data.local.VehicleDatabase
import ca.gllavoie.vehicletracker.data.local.VehicleEntity
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
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
class FuelRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: VehicleDatabase
    private lateinit var dbFile: File
    private lateinit var vehicleRepository: RoomVehicleRepository
    private lateinit var repository: RoomFuelRepository
    private val vehicleId = "fuel-car"
    private val today = LocalDate.parse("2026-10-22")
    private val now = today.plusDays(1).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()

    @Before fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        dbFile = File(context.cacheDir, "fuel-repository-${System.nanoTime()}.db")
        database = Room.databaseBuilder(context, VehicleDatabase::class.java, dbFile.absolutePath).build()
        vehicleRepository = RoomVehicleRepository(database.vehicleDao())
        vehicleRepository.save(VehicleEntity(id = vehicleId, make = "Toyota", model = "Prius", createdAtUtc = 1, updatedAtUtc = 1))
        repository = RoomFuelRepository(database.vehicleDao(), vehicleRepository, ZoneId.of("UTC")) { now }
    }

    @After fun tearDown() {
        database.close()
        dbFile.delete()
        File(dbFile.path + "-shm").delete()
        File(dbFile.path + "-wal").delete()
    }

    @Test fun fillUpAndItsOdometerAreCommittedTogether() = runBlocking {
        val result = repository.addFillUp(fillUp(id = "fill-1", odometer = 80_000, date = today))
        assertTrue(result is FuelRecordResult.Saved)
        val saved = (result as FuelRecordResult.Saved).fillUp
        assertEquals(40_000L, saved.volumeMillilitres)
        assertEquals("KM", saved.odometerUnit)
        val reading = database.vehicleDao().observeOdometerReadings(vehicleId).first().single()
        assertEquals(saved.odometerReadingId, reading.id)
        assertEquals(saved.id, reading.sourceRecordId)
        assertEquals(80_000L, reading.value)
    }

    @Test fun lowerOdometerRequiresConfirmationAndDoesNotWriteEarly() = runBlocking {
        repository.addFillUp(fillUp(id = "fill-first", odometer = 80_000, date = today.minusDays(1)))
        val proposed = repository.addFillUp(fillUp(id = "fill-reset", odometer = 500, date = today))
        assertTrue(proposed is FuelRecordResult.NeedsConfirmation)
        assertEquals(1, repository.observeFillUps(vehicleId).first().size)
        assertEquals(1, database.vehicleDao().observeOdometerReadings(vehicleId).first().size)
        val saved = repository.addFillUp(fillUp(id = "fill-reset", odometer = 500, date = today), confirmAnomaly = true)
        assertTrue(saved is FuelRecordResult.Saved)
        val latest = database.vehicleDao().observeOdometerReadings(vehicleId).first().first()
        assertEquals("FUEL_FILL_UP_CONFIRMED", latest.sourceType)
    }

    @Test fun invalidUnitsDatesAndVolumesAreRejected() = runBlocking {
        for (invalid in listOf(
            fillUp(id = "negative", volumeMl = -1),
            fillUp(id = "wrong-unit", unit = "MILE"),
            fillUp(id = "future", date = today.plusDays(2))
        )) {
            try {
                repository.addFillUp(invalid)
                error("Invalid fuel record must be rejected")
            } catch (_: IllegalArgumentException) {
                assertTrue(repository.observeFillUps(vehicleId).first().isEmpty())
                assertTrue(database.vehicleDao().observeOdometerReadings(vehicleId).first().isEmpty())
            }
        }
    }

    @Test fun archivedVehicleCannotReceiveFillUps() = runBlocking {
        vehicleRepository.archive(vehicleId, 100)
        try {
            repository.addFillUp(fillUp())
            error("Archived vehicle must be rejected")
        } catch (_: IllegalArgumentException) {
            assertTrue(repository.observeFillUps(vehicleId).first().isEmpty())
        }
    }

    private fun fillUp(
        id: String = "fill-default",
        odometer: Long = 80_000,
        date: LocalDate = today,
        volumeMl: Long = 40_000,
        unit: String = "KM"
    ) = FuelFillUpEntity(
        id = id,
        vehicleId = vehicleId,
        filledAtUtc = date.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
        odometerValue = odometer,
        odometerUnit = unit,
        volumeMillilitres = volumeMl,
        totalCostMinor = 5_000,
        currencyCode = "CAD",
        isFullTank = true,
        createdAtUtc = 10,
        updatedAtUtc = 10
    )
}
