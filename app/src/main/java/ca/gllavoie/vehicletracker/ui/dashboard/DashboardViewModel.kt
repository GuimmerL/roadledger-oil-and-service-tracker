package ca.gllavoie.vehicletracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ca.gllavoie.vehicletracker.data.local.OdometerReadingEntity
import ca.gllavoie.vehicletracker.data.local.OilChangeEntity
import ca.gllavoie.vehicletracker.data.local.VehicleEntity
import ca.gllavoie.vehicletracker.data.local.VehicleSpecificationEntity
import ca.gllavoie.vehicletracker.data.repository.OdometerRepository
import ca.gllavoie.vehicletracker.data.repository.OilChangeRepository
import ca.gllavoie.vehicletracker.data.repository.VehicleRepository
import ca.gllavoie.vehicletracker.data.repository.VehicleSpecificationRepository
import ca.gllavoie.vehicletracker.data.repository.calculateVehicleOilSchedule
import ca.gllavoie.vehicletracker.domain.calculation.OilSchedule
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardVehicleSummary(
    val vehicle: VehicleEntity,
    val latestService: OilChangeEntity?,
    val currentOdometer: OdometerReadingEntity?,
    val catalogSpecification: VehicleSpecificationEntity? = null,
    val schedule: OilSchedule?
)

data class DashboardUiState(
    val vehicles: List<DashboardVehicleSummary> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
) {
    val activeVehicleCount: Int get() = vehicles.size
    val dueSoonCount: Int get() = vehicles.count { it.schedule?.status?.name == "DUE_SOON" }
    val dueCount: Int get() = vehicles.count { it.schedule?.status?.name == "DUE" }
    val overdueCount: Int get() = vehicles.count { it.schedule?.status?.name == "OVERDUE" }
    val attentionCount: Int get() = dueSoonCount + dueCount + overdueCount
}

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val vehicleRepository: VehicleRepository,
    private val oilChangeRepository: OilChangeRepository,
    private val odometerRepository: OdometerRepository,
    private val specificationRepository: VehicleSpecificationRepository,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val today: () -> LocalDate = { LocalDate.now(zoneId) }
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                vehicleRepository.observeActiveVehicles().collectLatest { vehicles ->
                    if (vehicles.isEmpty()) {
                        mutableUiState.update { it.copy(vehicles = emptyList(), isLoading = false, error = null) }
                    } else {
                        val summaries: List<Flow<DashboardVehicleSummary>> = vehicles.map { vehicle ->
                            val catalogFlow = vehicle.defaultSpecificationId?.let(specificationRepository::observeCatalogEntry) ?: flowOf(null)
                            combine(
                                oilChangeRepository.observeRecords(vehicle.id),
                                odometerRepository.observeReadings(vehicle.id),
                                specificationRepository.observeOverride(vehicle.id),
                                catalogFlow
                            ) { records, readings, vehicleOverride, catalogSpecification ->
                                val latestService = records.firstOrNull()
                                val currentOdometer = readings.firstOrNull { it.unit == vehicle.odometerUnit }
                                DashboardVehicleSummary(
                                    vehicle = vehicle,
                                    latestService = latestService,
                                    currentOdometer = currentOdometer,
                                    catalogSpecification = catalogSpecification,
                                    schedule = latestService?.let {
                                        calculateVehicleOilSchedule(vehicle, it, readings, vehicleOverride, catalogSpecification, zoneId, today())
                                    }
                                )
                            }
                        }
                        combine(summaries) { current -> current.toList() }.collect { values ->
                            mutableUiState.update { it.copy(vehicles = values, isLoading = false, error = null) }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableUiState.update { it.copy(isLoading = false, error = error.message ?: "Unable to load dashboard.") }
            }
        }
    }


    class Factory(
        private val vehicleRepository: VehicleRepository,
        private val oilChangeRepository: OilChangeRepository,
        private val odometerRepository: OdometerRepository,
        private val specificationRepository: VehicleSpecificationRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(DashboardViewModel::class.java))
            return DashboardViewModel(vehicleRepository, oilChangeRepository, odometerRepository, specificationRepository) as T
        }
    }
}
