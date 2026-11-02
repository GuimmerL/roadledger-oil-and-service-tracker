package ca.gllavoie.vehicletracker.ui.garage

import ca.gllavoie.vehicletracker.data.local.OdometerReadingEntity
import ca.gllavoie.vehicletracker.data.local.SpecificationSourceEntity
import ca.gllavoie.vehicletracker.data.local.VehicleSpecificationEntity
import ca.gllavoie.vehicletracker.data.local.OilChangeEntity
import ca.gllavoie.vehicletracker.data.local.VehicleDao
import ca.gllavoie.vehicletracker.data.local.VehicleEntity
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OilChangeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val vehicleId = "vehicle-1"
    private val today = LocalDate.parse("2026-10-22")
    private lateinit var vehicleRepository: StubVehicleRepository
    private lateinit var oilRepository: StubOilRepository
    private lateinit var odometerRepository: StubOdometerRepository
    private lateinit var specificationRepository: StubSpecificationRepository
    private lateinit var viewModel: OilChangeViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        vehicleRepository = StubVehicleRepository(VehicleEntity(id = vehicleId, make = "Honda", model = "Civic", createdAtUtc = 1, updatedAtUtc = 1))
        oilRepository = StubOilRepository()
        odometerRepository = StubOdometerRepository()
        specificationRepository = StubSpecificationRepository()
        viewModel = OilChangeViewModel(vehicleId, vehicleRepository, oilRepository, odometerRepository, specificationRepository, today = { today })
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun savedRecordRetainsActualOilAddedAndCurrencyMinorUnits() = runTest(dispatcher) {
        advanceUntilIdle()
        viewModel.save(
            OilChangeDraft(
                serviceDate = today.toString(), odometerValue = "80000", oilBrand = "Brand", oilProduct = "Product",
                viscosityGrade = "5W-30", manufacturerApproval = "OEM spec X", quantityLitres = "4.25",
                totalCost = "123.45", partsCost = "90.00", laborCost = "33.45", distanceInterval = "8000", timeIntervalMonths = "6"
            )
        )
        advanceUntilIdle()

        val record = oilRepository.records.value.single()
        assertEquals(4_250, record.quantityUsedMl)
        assertEquals(12_345L, record.totalCostMinor)
        assertEquals(9_000L, record.partsCostMinor)
        assertEquals(3_345L, record.laborCostMinor)
        assertEquals("OEM spec X", record.manufacturerApproval)
        assertEquals(1, viewModel.uiState.value.saveSuccessCount)
        assertEquals(8_000, viewModel.uiState.value.schedule?.nextDueOdometer?.minus(80_000)?.toInt())
        assertNotNull(viewModel.uiState.value.schedule?.timeDeadlineDate)
    }

    @Test fun editKeepsSameRecordAndUpdatedScheduleUsesEditedOverrides() = runTest(dispatcher) {
        advanceUntilIdle()
        val record = OilChangeEntity(
            id = "service-1", vehicleId = vehicleId, serviceDate = "2026-01-01", odometerValue = 10_000,
            odometerUnit = "KM", distanceIntervalOverride = 5_000, timeIntervalMonthsOverride = 6,
            createdAtUtc = 100, updatedAtUtc = 100
        )
        oilRepository.save(record)
        odometerRepository.readings.value = listOf(
            OdometerReadingEntity(id = "odo-current", vehicleId = vehicleId, value = 14_000, unit = "KM", recordedAtUtc = today.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(), sourceType = "MANUAL", createdAtUtc = 10, updatedAtUtc = 10)
        )
        advanceUntilIdle()
        assertEquals(1_000L, viewModel.uiState.value.schedule?.distanceRemaining)
        assertEquals("OVERDUE", viewModel.uiState.value.schedule?.status?.name)

        viewModel.save(OilChangeDraft(serviceDate = "2026-01-01", odometerValue = "10000", distanceInterval = "6000", timeIntervalMonths = "12"), record)
        advanceUntilIdle()
        val edited = oilRepository.records.value.single()
        assertEquals(record.id, edited.id)
        assertEquals(record.createdAtUtc, edited.createdAtUtc)
        assertEquals(6_000, edited.distanceIntervalOverride)
        assertEquals(12, edited.timeIntervalMonthsOverride)
        assertEquals(1, viewModel.uiState.value.saveSuccessCount)
    }

    @Test fun currentVehicleSpecificationProvidesIntervalsWhenServiceHasNoOverride() = runTest(dispatcher) {
        specificationRepository.value.value = VehicleSpecificationOverrideEntity(
            vehicleId = vehicleId, distanceInterval = 8_000, distanceUnit = "KM", timeIntervalMonths = 6, updatedAtUtc = 1
        )
        advanceUntilIdle()
        viewModel.save(OilChangeDraft(serviceDate = today.toString(), odometerValue = "100000"))
        advanceUntilIdle()
        assertEquals(108_000L, viewModel.uiState.value.schedule?.nextDueOdometer)
        assertEquals(today.plusMonths(6), viewModel.uiState.value.schedule?.timeDeadlineDate)
        assertNull(oilRepository.records.value.single().distanceIntervalOverride)
    }

    @Test fun invalidAmountsOrIntervalsShowErrorAndDoNotSave() = runTest(dispatcher) {
        advanceUntilIdle()
        viewModel.save(OilChangeDraft(serviceDate = today.toString(), odometerValue = "100", quantityLitres = "0", distanceInterval = "-1"))
        advanceUntilIdle()
        assertTrue(oilRepository.records.value.isEmpty())
        assertFalse(viewModel.uiState.value.isSaving)
        assertNotNull(viewModel.uiState.value.error)
        assertEquals(0, viewModel.uiState.value.saveSuccessCount)
    }
}

private class StubVehicleRepository(private val vehicle: VehicleEntity) : VehicleRepository {
    override fun observeActiveVehicles(): Flow<List<VehicleEntity>> = MutableStateFlow(listOf(vehicle))
    override fun observeArchivedVehicles(): Flow<List<VehicleEntity>> = MutableStateFlow(emptyList())
    override suspend fun getVehicle(vehicleId: String): VehicleEntity? = vehicle.takeIf { it.id == vehicleId }
    override suspend fun save(vehicle: VehicleEntity) = Unit
    override suspend fun archive(vehicleId: String, nowUtc: Long) = false
    override suspend fun restore(vehicleId: String, nowUtc: Long) = false
    override suspend fun deletePermanently(vehicleId: String) = VehicleDao.VehicleDeletionSnapshot(false, emptyList())
}

private class StubOilRepository : OilChangeRepository {
    val records = MutableStateFlow<List<OilChangeEntity>>(emptyList())
    override fun observeRecords(vehicleId: String): Flow<List<OilChangeEntity>> = records
    override suspend fun save(record: OilChangeEntity) {
        records.value = records.value.filterNot { it.id == record.id } + record
    }
}

private class StubOdometerRepository : OdometerRepository {
    val readings = MutableStateFlow<List<OdometerReadingEntity>>(emptyList())
    override fun observeReadings(vehicleId: String): Flow<List<OdometerReadingEntity>> = readings
    override suspend fun addReading(vehicleId: String, value: Long, date: LocalDate, notes: String?, confirmAnomaly: Boolean): OdometerRecordResult =
        error("No odometer entry expected in this test")
}

private class StubSpecificationRepository : VehicleSpecificationRepository {
    val value = MutableStateFlow<VehicleSpecificationOverrideEntity?>(null)
    private val catalog = MutableStateFlow<List<VehicleSpecificationEntity>>(emptyList())
    override fun observeCatalog(): Flow<List<VehicleSpecificationEntity>> = catalog
    override fun observeCatalogEntry(specificationId: String): Flow<VehicleSpecificationEntity?> = MutableStateFlow(catalog.value.firstOrNull { it.id == specificationId })
    override suspend fun getCatalogEntry(specificationId: String) = catalog.value.firstOrNull { it.id == specificationId }
    override suspend fun getSources(specificationId: String): List<SpecificationSourceEntity> = emptyList()
    override suspend fun saveCatalogEntry(specification: VehicleSpecificationEntity, verifiedSource: SpecificationSourceEntity?) = Unit
    override suspend fun seedCatalogIfMissing(specifications: List<VehicleSpecificationEntity>, sources: List<SpecificationSourceEntity>) = Unit
    override fun observeOverride(vehicleId: String): Flow<VehicleSpecificationOverrideEntity?> = value
    override suspend fun saveOverride(override: VehicleSpecificationOverrideEntity) { value.value = override }
}
