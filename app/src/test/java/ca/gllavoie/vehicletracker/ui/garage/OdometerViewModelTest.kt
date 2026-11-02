package ca.gllavoie.vehicletracker.ui.garage

import ca.gllavoie.vehicletracker.data.local.OdometerReadingEntity
import ca.gllavoie.vehicletracker.data.local.VehicleDao
import ca.gllavoie.vehicletracker.data.local.VehicleEntity
import ca.gllavoie.vehicletracker.data.repository.OdometerRecordResult
import ca.gllavoie.vehicletracker.data.repository.OdometerRepository
import ca.gllavoie.vehicletracker.data.repository.VehicleRepository
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OdometerViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun doneSubmitsEnteredMileageOrNotesButClosesAnUntouchedForm() {
        val today = "2026-10-28"
        assertTrue(shouldSaveOdometerDraftOnDone("12345", today, "", today))
        assertTrue(shouldSaveOdometerDraftOnDone("", today, "tire change", today))
        assertTrue(shouldSaveOdometerDraftOnDone("", "2026-10-27", "", today))
        assertFalse(shouldSaveOdometerDraftOnDone("", today, "", today))
    }

    @Test fun recordingMileagePersistsTheEnteredValueDateAndNotes() = runTest(dispatcher) {
        val vehicle = VehicleEntity(id = "mileage-car", make = "Honda", model = "Civic", createdAtUtc = 1L, updatedAtUtc = 1L)
        val vehicleRepository = OdometerFakeVehicleRepository(vehicle)
        val odometerRepository = FakeOdometerRepository()
        val viewModel = OdometerViewModel(vehicle.id, vehicleRepository, odometerRepository)
        advanceUntilIdle()

        viewModel.record("12345", "2026-10-27", "Oil checked")
        advanceUntilIdle()

        val saved = odometerRepository.readings.value.single()
        assertEquals(12_345L, saved.value)
        assertEquals("KM", saved.unit)
        assertEquals("Oil checked", saved.notes)
        assertEquals(1, viewModel.uiState.value.saveSuccessCount)
    }
}

private class OdometerFakeVehicleRepository(private val vehicle: VehicleEntity) : VehicleRepository {
    override fun observeActiveVehicles(): Flow<List<VehicleEntity>> = MutableStateFlow(listOf(vehicle))
    override fun observeArchivedVehicles(): Flow<List<VehicleEntity>> = MutableStateFlow(emptyList())
    override suspend fun getVehicle(vehicleId: String): VehicleEntity? = vehicle.takeIf { it.id == vehicleId }
    override suspend fun save(vehicle: VehicleEntity) = Unit
    override suspend fun archive(vehicleId: String, nowUtc: Long) = false
    override suspend fun restore(vehicleId: String, nowUtc: Long) = false
    override suspend fun deletePermanently(vehicleId: String) = VehicleDao.VehicleDeletionSnapshot(false, emptyList())
}

private class FakeOdometerRepository : OdometerRepository {
    val readings = MutableStateFlow<List<OdometerReadingEntity>>(emptyList())
    override fun observeReadings(vehicleId: String): Flow<List<OdometerReadingEntity>> = readings

    override suspend fun addReading(vehicleId: String, value: Long, date: LocalDate, notes: String?, confirmAnomaly: Boolean): OdometerRecordResult {
        val row = OdometerReadingEntity(
            id = "reading-${readings.value.size + 1}", vehicleId = vehicleId, value = value, unit = "KM",
            recordedAtUtc = date.toEpochDay(), sourceType = "MANUAL", notes = notes,
            createdAtUtc = 1L, updatedAtUtc = 1L
        )
        readings.value = readings.value + row
        return OdometerRecordResult.Saved(row)
    }
}
