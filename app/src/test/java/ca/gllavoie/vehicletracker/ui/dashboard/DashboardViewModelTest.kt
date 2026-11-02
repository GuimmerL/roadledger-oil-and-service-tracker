package ca.gllavoie.vehicletracker.ui.dashboard

import ca.gllavoie.vehicletracker.data.local.OdometerReadingEntity
import ca.gllavoie.vehicletracker.data.local.OilChangeEntity
import ca.gllavoie.vehicletracker.data.local.VehicleDao
import ca.gllavoie.vehicletracker.data.local.VehicleEntity
import ca.gllavoie.vehicletracker.data.local.SpecificationSourceEntity
import ca.gllavoie.vehicletracker.data.local.VehicleSpecificationEntity
import ca.gllavoie.vehicletracker.data.local.VehicleSpecificationOverrideEntity
import ca.gllavoie.vehicletracker.data.repository.OdometerRecordResult
import ca.gllavoie.vehicletracker.data.repository.OdometerRepository
import ca.gllavoie.vehicletracker.data.repository.OilChangeRepository
import ca.gllavoie.vehicletracker.data.repository.VehicleRepository
import ca.gllavoie.vehicletracker.data.repository.VehicleSpecificationRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.parse("2026-10-22")
    private lateinit var vehicles: DashboardFakeVehicleRepository
    private lateinit var oils: DashboardFakeOilRepository
    private lateinit var odometers: DashboardFakeOdometerRepository
    private lateinit var specifications: DashboardFakeSpecificationRepository

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        vehicles = DashboardFakeVehicleRepository()
        oils = DashboardFakeOilRepository()
        odometers = DashboardFakeOdometerRepository()
        specifications = DashboardFakeSpecificationRepository()
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun oneVehicleAppearsWithUnavailableScheduleUntilAServiceIsLogged() = runTest(dispatcher) {
        vehicles.active.value = listOf(vehicle("one", "Honda"))
        val model = DashboardViewModel(vehicles, oils, odometers, specifications, today = { today })
        advanceUntilIdle()
        assertEquals(1, model.uiState.value.activeVehicleCount)
        assertEquals("one", model.uiState.value.vehicles.single().vehicle.id)
        assertNull(model.uiState.value.vehicles.single().schedule)
        assertEquals(0, model.uiState.value.attentionCount)
    }

    @Test fun dueCountsAndSchedulesRemainIndependentAcrossVehicles() = runTest(dispatcher) {
        val first = vehicle("overdue", "Honda")
        val second = vehicle("current", "Toyota")
        vehicles.active.value = listOf(first, second)
        oils.records[first.id] = MutableStateFlow(listOf(service(first.id, "2025-01-01", 10_000, 6)))
        oils.records[second.id] = MutableStateFlow(listOf(service(second.id, "2026-10-01", null, null)))
        specifications.overrides[second.id] = MutableStateFlow(
            VehicleSpecificationOverrideEntity(vehicleId = second.id, distanceInterval = 10_000, distanceUnit = "KM", timeIntervalMonths = 12, updatedAtUtc = 1)
        )
        odometers.readings[first.id] = MutableStateFlow(listOf(reading(first.id, 15_000)))
        odometers.readings[second.id] = MutableStateFlow(listOf(reading(second.id, 2_000)))

        val model = DashboardViewModel(vehicles, oils, odometers, specifications, today = { today })
        advanceUntilIdle()
        assertEquals(2, model.uiState.value.activeVehicleCount)
        assertEquals(1, model.uiState.value.overdueCount)
        assertEquals(1, model.uiState.value.attentionCount)
        assertEquals("OVERDUE", model.uiState.value.vehicles.first { it.vehicle.id == first.id }.schedule?.status?.name)
        assertEquals("UP_TO_DATE", model.uiState.value.vehicles.first { it.vehicle.id == second.id }.schedule?.status?.name)
    }

    private fun vehicle(id: String, make: String) = VehicleEntity(id = id, make = make, model = "Test", createdAtUtc = 1, updatedAtUtc = 1)
    private fun service(vehicleId: String, date: String, distance: Int?, months: Int?) = OilChangeEntity(
        id = "service-$vehicleId", vehicleId = vehicleId, serviceDate = date, odometerValue = 1_000,
        odometerUnit = "KM", distanceIntervalOverride = distance, timeIntervalMonthsOverride = months,
        createdAtUtc = 1, updatedAtUtc = 1
    )
    private fun reading(vehicleId: String, value: Long) = OdometerReadingEntity(
        id = "odo-$vehicleId", vehicleId = vehicleId, value = value, unit = "KM",
        recordedAtUtc = today.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(), sourceType = "MANUAL",
        createdAtUtc = 1, updatedAtUtc = 1
    )
}

private class DashboardFakeVehicleRepository : VehicleRepository {
    val active = MutableStateFlow<List<VehicleEntity>>(emptyList())
    override fun observeActiveVehicles(): Flow<List<VehicleEntity>> = active
    override fun observeArchivedVehicles(): Flow<List<VehicleEntity>> = MutableStateFlow(emptyList())
    override suspend fun getVehicle(vehicleId: String) = active.value.firstOrNull { it.id == vehicleId }
    override suspend fun save(vehicle: VehicleEntity) = Unit
    override suspend fun archive(vehicleId: String, nowUtc: Long) = false
    override suspend fun restore(vehicleId: String, nowUtc: Long) = false
    override suspend fun deletePermanently(vehicleId: String) = VehicleDao.VehicleDeletionSnapshot(false, emptyList())
}

private class DashboardFakeOilRepository : OilChangeRepository {
    val records = mutableMapOf<String, MutableStateFlow<List<OilChangeEntity>>>()
    override fun observeRecords(vehicleId: String): Flow<List<OilChangeEntity>> = records.getOrPut(vehicleId) { MutableStateFlow(emptyList()) }
    override suspend fun save(record: OilChangeEntity) = Unit
}

private class DashboardFakeOdometerRepository : OdometerRepository {
    val readings = mutableMapOf<String, MutableStateFlow<List<OdometerReadingEntity>>>()
    override fun observeReadings(vehicleId: String): Flow<List<OdometerReadingEntity>> = readings.getOrPut(vehicleId) { MutableStateFlow(emptyList()) }
    override suspend fun addReading(vehicleId: String, value: Long, date: LocalDate, notes: String?, confirmAnomaly: Boolean): OdometerRecordResult =
        error("No odometer write expected in dashboard tests")
}

private class DashboardFakeSpecificationRepository : VehicleSpecificationRepository {
    val overrides = mutableMapOf<String, MutableStateFlow<VehicleSpecificationOverrideEntity?>>()
    private val catalog = MutableStateFlow<List<VehicleSpecificationEntity>>(emptyList())
    override fun observeCatalog(): Flow<List<VehicleSpecificationEntity>> = catalog
    override fun observeCatalogEntry(specificationId: String): Flow<VehicleSpecificationEntity?> = MutableStateFlow(catalog.value.firstOrNull { it.id == specificationId })
    override suspend fun getCatalogEntry(specificationId: String) = catalog.value.firstOrNull { it.id == specificationId }
    override suspend fun getSources(specificationId: String): List<SpecificationSourceEntity> = emptyList()
    override suspend fun saveCatalogEntry(specification: VehicleSpecificationEntity, verifiedSource: SpecificationSourceEntity?) = Unit
    override suspend fun seedCatalogIfMissing(specifications: List<VehicleSpecificationEntity>, sources: List<SpecificationSourceEntity>) = Unit
    override fun observeOverride(vehicleId: String): Flow<VehicleSpecificationOverrideEntity?> = overrides.getOrPut(vehicleId) { MutableStateFlow(null) }
    override suspend fun saveOverride(override: VehicleSpecificationOverrideEntity) = Unit
}
