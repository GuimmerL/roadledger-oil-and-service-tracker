package ca.gllavoie.vehicletracker.ui.garage

import ca.gllavoie.vehicletracker.data.local.VehicleDao
import ca.gllavoie.vehicletracker.data.local.VehicleEntity
import ca.gllavoie.vehicletracker.data.repository.VehicleRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class GarageViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeVehicleRepository
    private lateinit var viewModel: GarageViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeVehicleRepository()
        viewModel = GarageViewModel(repository)
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun addingVehicleSurfacesRepositoryAssignedStableId() = runTest(dispatcher) {
        viewModel.saveVehicle(VehicleDraft(modelYear = "2020", make = "Toyota", model = "Prius"))
        advanceUntilIdle()

        val surfaced = viewModel.uiState.value.activeVehicles.single()
        val persisted = repository.getVehicle(surfaced.id)
        assertTrue(UUID.fromString(surfaced.id).toString() == surfaced.id)
        assertEquals(surfaced.id, persisted?.id)
        assertEquals("Toyota", surfaced.make)
        assertEquals("Prius", surfaced.model)
        assertEquals(1, viewModel.uiState.value.saveSuccessCount)
    }

    @Test fun addingVehicleKeepsSelectedCatalogLinkForItsOilInterval() = runTest(dispatcher) {
        val catalogId = "vehicle-catalog-v2-7752bc54"
        viewModel.saveVehicle(
            VehicleDraft(
                modelYear = "2023", make = "Acura", model = "Integra",
                engine = "2.0L I4", defaultSpecificationId = catalogId, vehicleType = "SUV"
            )
        )
        advanceUntilIdle()

        val saved = viewModel.uiState.value.activeVehicles.single()
        assertEquals(catalogId, saved.defaultSpecificationId)
        assertEquals("SUV", saved.vehicleType)
        assertNull(saved.trim)
        assertEquals(catalogId, repository.getVehicle(saved.id)?.defaultSpecificationId)
    }

    @Test fun rapidDuplicateSaveCreatesOnlyOneVehicle() = runTest(dispatcher) {
        viewModel.saveVehicle(VehicleDraft(modelYear = "2020", make = "Toyota", model = "Prius"))
        viewModel.saveVehicle(VehicleDraft(modelYear = "2021", make = "Honda", model = "Civic"))
        runCurrent()

        assertEquals(1, repository.saveAttempts)
        assertEquals(1, viewModel.uiState.value.activeVehicles.size)
        assertEquals("Toyota", viewModel.uiState.value.activeVehicles.single().make)
        assertEquals(1, viewModel.uiState.value.saveSuccessCount)
    }

    @Test fun cancellationFromRepositoryPropagatesAndDoesNotShowFailure() = runTest(dispatcher) {
        repository.saveFailure = CancellationException("cancelled")
        viewModel.saveVehicle(VehicleDraft(modelYear = "2020", make = "Toyota", model = "Prius"))
        advanceUntilIdle()

        assertEquals(1, repository.saveAttempts)
        assertEquals(0, viewModel.uiState.value.saveSuccessCount)
        assertNull(viewModel.uiState.value.message)
        assertTrue(!viewModel.uiState.value.isSaving)
    }

    @Test fun blankRepositoryErrorUsesNonblankFallback() = runTest(dispatcher) {
        repository.saveFailure = IllegalStateException("   ")
        viewModel.saveVehicle(VehicleDraft(modelYear = "2020", make = "Toyota", model = "Prius"))
        advanceUntilIdle()

        assertEquals("Unable to save vehicle.", viewModel.uiState.value.saveError)
        assertNull(viewModel.uiState.value.message)
        assertTrue(!viewModel.uiState.value.isSaving)
    }

    @Test fun saveValidationFailureDoesNotIncrementSuccessCount() = runTest(dispatcher) {
        viewModel.saveVehicle(VehicleDraft(modelYear = "2022", make = "Honda", model = "Civic"))
        advanceUntilIdle()
        val successCountBeforeInvalidSave = viewModel.uiState.value.saveSuccessCount

        viewModel.saveVehicle(VehicleDraft(modelYear = "1800", make = "Ford", model = "Focus"))
        advanceUntilIdle()

        assertEquals(successCountBeforeInvalidSave, viewModel.uiState.value.saveSuccessCount)
        assertEquals(1, viewModel.uiState.value.activeVehicles.size)
        assertTrue(viewModel.uiState.value.saveError.orEmpty().contains("between"))
        assertNull(viewModel.uiState.value.message)
    }

    @Test fun editingUpdatesTheExistingRecordWithoutChangingItsIdentity() = runTest(dispatcher) {
        viewModel.saveVehicle(VehicleDraft(modelYear = "2022", make = "Honda", model = "Civic"))
        advanceUntilIdle()
        val original = viewModel.uiState.value.activeVehicles.single()

        viewModel.saveVehicle(VehicleDraft(modelYear = "2022", make = "Honda", model = "Corolla"), original)
        advanceUntilIdle()

        val updated = viewModel.uiState.value.activeVehicles.single()
        assertEquals(1, viewModel.uiState.value.activeVehicles.size)
        assertEquals(original.id, updated.id)
        assertEquals(original.createdAtUtc, updated.createdAtUtc)
        assertEquals("Corolla", updated.model)
        assertEquals(updated, repository.getVehicle(original.id))
    }

    @Test fun vehicleDistanceUnitCanBeMiles() = runTest(dispatcher) {
        viewModel.saveVehicle(VehicleDraft(modelYear = "2020", make = "Ford", model = "Focus", odometerUnit = "MILE"))
        advanceUntilIdle()
        assertEquals("MILE", viewModel.uiState.value.activeVehicles.single().odometerUnit)
    }

    @Test fun changingIntervalModePersistsForThatVehicle() = runTest(dispatcher) {
        viewModel.saveVehicle(VehicleDraft(modelYear = "2020", make = "Ford", model = "Focus"))
        advanceUntilIdle()
        val vehicle = viewModel.uiState.value.activeVehicles.single()

        viewModel.setOilIntervalMode(vehicle.id, "SEVERE")
        advanceUntilIdle()

        assertEquals("SEVERE", repository.getVehicle(vehicle.id)?.oilIntervalMode)
        assertEquals("SEVERE", viewModel.uiState.value.activeVehicles.single().oilIntervalMode)
        assertTrue(viewModel.uiState.value.message.orEmpty().contains("Severe / heavy use"))
    }

    @Test fun startingDailyUsageIsStoredInCanonicalKilometresPerDay() = runTest(dispatcher) {
        viewModel.saveVehicle(VehicleDraft(
            modelYear = "2020", make = "Ford", model = "Focus", odometerUnit = "MILE", startingDailyUsage = "50"
        ))
        advanceUntilIdle()

        assertEquals(80.4672, repository.getVehicle(viewModel.uiState.value.activeVehicles.single().id)?.startingDailyUsageKmPerDay!!, 0.0001)
    }

    @Test fun invalidStartingDailyUsageDoesNotSaveVehicle() = runTest(dispatcher) {
        viewModel.saveVehicle(VehicleDraft(
            modelYear = "2020", make = "Ford", model = "Focus", startingDailyUsage = "0"
        ))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.activeVehicles.isEmpty())
        assertTrue(viewModel.uiState.value.saveError.orEmpty().contains("greater than zero"))
    }

    @Test fun permanentDeleteRemovesVehicleWithoutArchiving() = runTest(dispatcher) {
        viewModel.saveVehicle(VehicleDraft(modelYear = "2022", make = "Honda", model = "Civic"))
        advanceUntilIdle()
        val original = viewModel.uiState.value.activeVehicles.single()

        viewModel.permanentlyDeleteVehicle(original)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.activeVehicles.isEmpty())
        assertEquals(1, viewModel.uiState.value.deleteSuccessCount)
        assertTrue(viewModel.uiState.value.message.orEmpty().contains("permanently deleted"))
        assertNull(repository.getVehicle(original.id))
    }

    @Test fun archiveAndRestoreRetainTheSameVehicleIdentity() = runTest(dispatcher) {
        viewModel.saveVehicle(VehicleDraft(modelYear = "2022", make = "Honda", model = "Civic"))
        advanceUntilIdle()
        val original = viewModel.uiState.value.activeVehicles.single()

        viewModel.archiveVehicle(original)
        advanceUntilIdle()
        val archived = viewModel.uiState.value.archivedVehicles.single()
        assertTrue(viewModel.uiState.value.activeVehicles.isEmpty())
        assertEquals(original.id, archived.id)
        assertEquals(original.id, repository.getVehicle(original.id)?.id)

        viewModel.restoreVehicle(archived)
        advanceUntilIdle()
        val restored = viewModel.uiState.value.activeVehicles.single()
        assertEquals(original.id, restored.id)
        assertNull(restored.archivedAtUtc)
        assertTrue(viewModel.uiState.value.archivedVehicles.isEmpty())
        assertEquals(restored, repository.getVehicle(original.id))
    }

}

private class FakeVehicleRepository : VehicleRepository {
    private val active = MutableStateFlow<List<VehicleEntity>>(emptyList())
    private val archived = MutableStateFlow<List<VehicleEntity>>(emptyList())
    var saveAttempts = 0
        private set
    var saveFailure: Exception? = null

    override fun observeActiveVehicles(): Flow<List<VehicleEntity>> = active
    override fun observeArchivedVehicles(): Flow<List<VehicleEntity>> = archived
    override suspend fun getVehicle(vehicleId: String): VehicleEntity? = (active.value + archived.value).find { it.id == vehicleId }

    override suspend fun save(vehicle: VehicleEntity) {
        saveAttempts += 1
        saveFailure?.let { throw it }
        require(vehicle.make.isNotBlank()) { "Vehicle make is required." }
        require(vehicle.model.isNotBlank()) { "Vehicle model is required." }
        require(vehicle.modelYear == null || vehicle.modelYear in 1886..9999) { "Enter a valid model year." }
        val previous = getVehicle(vehicle.id)
        val normalized = vehicle.copy(
            make = vehicle.make.trim(),
            model = vehicle.model.trim(),
            createdAtUtc = previous?.createdAtUtc ?: vehicle.createdAtUtc
        )
        active.value = active.value.filterNot { it.id == vehicle.id } + normalized
        archived.value = archived.value.filterNot { it.id == vehicle.id }
    }

    override suspend fun archive(vehicleId: String, nowUtc: Long): Boolean {
        val vehicle = active.value.find { it.id == vehicleId } ?: return false
        active.value = active.value.filterNot { it.id == vehicleId }
        archived.value = archived.value + vehicle.copy(archivedAtUtc = nowUtc, updatedAtUtc = nowUtc)
        return true
    }

    override suspend fun restore(vehicleId: String, nowUtc: Long): Boolean {
        val vehicle = archived.value.find { it.id == vehicleId } ?: return false
        archived.value = archived.value.filterNot { it.id == vehicleId }
        active.value = active.value + vehicle.copy(archivedAtUtc = null, updatedAtUtc = nowUtc)
        return true
    }

    override suspend fun deletePermanently(vehicleId: String): VehicleDao.VehicleDeletionSnapshot {
        val vehicle = getVehicle(vehicleId) ?: return VehicleDao.VehicleDeletionSnapshot(false, emptyList())
        active.value = active.value.filterNot { it.id == vehicleId }
        archived.value = archived.value.filterNot { it.id == vehicleId }
        return VehicleDao.VehicleDeletionSnapshot(true, emptyList())
    }
}
