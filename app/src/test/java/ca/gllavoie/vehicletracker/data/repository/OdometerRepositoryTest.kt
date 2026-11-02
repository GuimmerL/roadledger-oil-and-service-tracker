package ca.gllavoie.vehicletracker.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OdometerRepositoryTest {
    private lateinit var context: Context
    private lateinit var dbFile: File
    private lateinit var database: ca.gllavoie.vehicletracker.data.local.VehicleDatabase
    private lateinit var vehicleRepository: RoomVehicleRepository
    private lateinit var odometerRepository: RoomOdometerRepository
    private val vehicleId = "odometer-vehicle"
    private val today = LocalDate.parse("2026-10-12")

    @Before fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        dbFile = File(context.cacheDir, "odometer-test-${System.nanoTime()}.db")
        database = Room.databaseBuilder(context, ca.gllavoie.vehicletracker.data.local.VehicleDatabase::class.java, dbFile.absolutePath).build()
        vehicleRepository = RoomVehicleRepository(database.vehicleDao())
        vehicleRepository.save(
            ca.gllavoie.vehicletracker.data.local.VehicleEntity(
                id = vehicleId, make = "Toyota", model = "Prius", odometerUnit = "KM",
                createdAtUtc = 1L, updatedAtUtc = 1L
            )
        )
        odometerRepository = RoomOdometerRepository(database.vehicleDao(), vehicleRepository, ZoneId.of("UTC")) { today }
    }

    @After fun tearDown() {
        database.close()
        dbFile.delete()
        File(dbFile.path + "-shm").delete()
        File(dbFile.path + "-wal").delete()
    }

    @Test fun recordsDatedReadingsInVehicleUnits() = runBlocking {
        val result = odometerRepository.addReading(vehicleId, 52_000, today, "Oil shop")
        assertTrue(result is OdometerRecordResult.Saved)
        val reading = (result as OdometerRecordResult.Saved).reading
        assertEquals(52_000L, reading.value)
        assertEquals("KM", reading.unit)
        assertEquals("MANUAL", reading.sourceType)
        assertEquals(today, LocalDate.ofEpochDay(reading.recordedAtUtc / 86_400_000L))
        assertEquals(1, odometerRepository.observeReadings(vehicleId).first().size)
    }

    @Test fun rejectsNegativeAndDuplicateEntries() = runBlocking {
        try {
            odometerRepository.addReading(vehicleId, -1, today, null)
            error("Negative readings must be rejected")
        } catch (_: IllegalArgumentException) {
            assertTrue(odometerRepository.observeReadings(vehicleId).first().isEmpty())
        }
        odometerRepository.addReading(vehicleId, 10_000, today, null)
        try {
            odometerRepository.addReading(vehicleId, 10_000, today, null)
            error("Duplicate date/value must be rejected")
        } catch (_: IllegalArgumentException) {
            assertEquals(1, odometerRepository.observeReadings(vehicleId).first().size)
        }
    }

    @Test fun lowerValueRequiresReviewBeforeConfirmedSave() = runBlocking {
        odometerRepository.addReading(vehicleId, 100_000, today.minusDays(1), null)
        val proposed = odometerRepository.addReading(vehicleId, 500, today, "Possible rollover")
        assertTrue(proposed is OdometerRecordResult.NeedsConfirmation)
        assertEquals(1, odometerRepository.observeReadings(vehicleId).first().size)

        val saved = odometerRepository.addReading(vehicleId, 500, today, "Possible rollover", confirmAnomaly = true)
        assertTrue(saved is OdometerRecordResult.Saved)
        val record = (saved as OdometerRecordResult.Saved).reading
        assertEquals("MANUAL_CONFIRMED", record.sourceType)
        assertTrue(record.notes!!.contains("odometer reset or rollover"))
        assertEquals(2, odometerRepository.observeReadings(vehicleId).first().size)
    }

    @Test fun outOfOrderDateRequiresExplicitConfirmation() = runBlocking {
        odometerRepository.addReading(vehicleId, 10_000, today, null)
        val proposed = odometerRepository.addReading(vehicleId, 9_000, today.minusDays(2), "Late entry")
        assertTrue(proposed is OdometerRecordResult.NeedsConfirmation)
        assertFalse((proposed as OdometerRecordResult.NeedsConfirmation).reason.isBlank())
        assertEquals(1, odometerRepository.observeReadings(vehicleId).first().size)
        val saved = odometerRepository.addReading(vehicleId, 9_000, today.minusDays(2), "Late entry", confirmAnomaly = true)
        assertTrue(saved is OdometerRecordResult.Saved)
    }

    @Test fun futureDateAndArchivedVehicleAreRejected() = runBlocking {
        try {
            odometerRepository.addReading(vehicleId, 10_000, today.plusDays(1), null)
            error("Future dates must be rejected")
        } catch (_: IllegalArgumentException) {
            assertTrue(odometerRepository.observeReadings(vehicleId).first().isEmpty())
        }
        vehicleRepository.archive(vehicleId, 100L)
        try {
            odometerRepository.addReading(vehicleId, 10_000, today, null)
            error("Archived vehicles must reject readings")
        } catch (_: IllegalArgumentException) {
            assertTrue(odometerRepository.observeReadings(vehicleId).first().isEmpty())
        }
    }
}
