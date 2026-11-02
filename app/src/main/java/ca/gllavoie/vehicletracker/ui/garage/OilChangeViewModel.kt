package ca.gllavoie.vehicletracker.ui.garage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ca.gllavoie.vehicletracker.data.local.OdometerReadingEntity
import ca.gllavoie.vehicletracker.data.local.OilChangeEntity
import ca.gllavoie.vehicletracker.data.local.VehicleEntity
import ca.gllavoie.vehicletracker.data.local.VehicleSpecificationEntity
import ca.gllavoie.vehicletracker.data.local.VehicleSpecificationOverrideEntity
import ca.gllavoie.vehicletracker.data.repository.OdometerRepository
import ca.gllavoie.vehicletracker.data.repository.OilChangeRepository
import ca.gllavoie.vehicletracker.data.repository.VehicleRepository
import ca.gllavoie.vehicletracker.data.repository.VehicleSpecificationRepository
import ca.gllavoie.vehicletracker.data.repository.calculateVehicleOilSchedule
import ca.gllavoie.vehicletracker.domain.calculation.OilSchedule
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OilChangeDraft(
    val serviceDate: String = LocalDate.now().toString(),
    val odometerValue: String = "",
    val oilBrand: String = "",
    val oilProduct: String = "",
    val viscosityGrade: String = "",
    val manufacturerApproval: String = "",
    val quantityLitres: String = "",
    val filterBrand: String = "",
    val filterPartNumber: String = "",
    val serviceProvider: String = "",
    val serviceType: String = "DIY",
    val totalCost: String = "",
    val partsCost: String = "",
    val laborCost: String = "",
    val currencyCode: String = "CAD",
    val distanceInterval: String = "",
    val timeIntervalMonths: String = "",
    val notes: String = ""
)

data class OilChangeUiState(
    val vehicle: VehicleEntity? = null,
    val records: List<OilChangeEntity> = emptyList(),
    val odometerReadings: List<OdometerReadingEntity> = emptyList(),
    val specificationOverride: VehicleSpecificationOverrideEntity? = null,
    val catalogSpecification: VehicleSpecificationEntity? = null,
    val schedule: OilSchedule? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val saveSuccessCount: Int = 0,
    val error: String? = null
)

class OilChangeViewModel(
    private val vehicleId: String,
    private val vehicleRepository: VehicleRepository,
    private val oilChangeRepository: OilChangeRepository,
    odometerRepository: OdometerRepository,
    specificationRepository: VehicleSpecificationRepository,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val today: () -> LocalDate = { LocalDate.now(zoneId) }
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(OilChangeUiState())
    val uiState: StateFlow<OilChangeUiState> = mutableUiState.asStateFlow()
    private var isSaveInFlight = false

    init {
        viewModelScope.launch {
            try {
                val vehicle = vehicleRepository.getVehicle(vehicleId)
                mutableUiState.update { it.copy(vehicle = vehicle) }
                val catalogSpecificationFlow = vehicle?.defaultSpecificationId?.let(specificationRepository::observeCatalogEntry) ?: flowOf(null)
                combine(
                    oilChangeRepository.observeRecords(vehicleId),
                    odometerRepository.observeReadings(vehicleId),
                    specificationRepository.observeOverride(vehicleId),
                    catalogSpecificationFlow
                ) { records, readings, manualOverride, catalogSpecification ->
                    Triple(records, readings, manualOverride to catalogSpecification)
                }.collect { (records, readings, specifications) ->
                    val (manualOverride, catalogSpecification) = specifications
                    val schedule = vehicle?.let { currentVehicle ->
                        records.firstOrNull()?.let { latestService ->
                            calculateVehicleOilSchedule(currentVehicle, latestService, readings, manualOverride, catalogSpecification, zoneId, today())
                        }
                    }
                    mutableUiState.update {
                        it.copy(
                            records = records,
                            odometerReadings = readings,
                            specificationOverride = manualOverride,
                            catalogSpecification = catalogSpecification,
                            schedule = schedule,
                            isLoading = false
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableUiState.update { it.copy(isLoading = false, error = error.message ?: "Unable to load service history.") }
            }
        }
    }

    fun save(draft: OilChangeDraft, existing: OilChangeEntity? = null) {
        if (isSaveInFlight) return
        val vehicle = mutableUiState.value.vehicle
        if (vehicle == null) {
            mutableUiState.update { it.copy(error = "Vehicle is not available.") }
            return
        }
        isSaveInFlight = true
        mutableUiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                val serviceDate = LocalDate.parse(draft.serviceDate.trim())
                val odometer = draft.odometerValue.trim().toLongOrNull()
                    ?: throw IllegalArgumentException("Enter the odometer reading using digits only.")
                val quantityMl = draft.quantityLitres.toMinorUnits(1000, "Oil quantity")?.let(Math::toIntExact)
                val totalCost = draft.totalCost.toMinorUnits(100, "Total cost", allowZero = true)
                val partsCost = draft.partsCost.toMinorUnits(100, "Parts cost", allowZero = true)
                val laborCost = draft.laborCost.toMinorUnits(100, "Labor cost", allowZero = true)
                val distanceInterval = draft.distanceInterval.toOptionalPositiveInt("Distance interval")
                val timeInterval = draft.timeIntervalMonths.toOptionalPositiveInt("Time interval")
                val now = System.currentTimeMillis()
                val record = OilChangeEntity(
                    id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                    vehicleId = vehicleId,
                    serviceDate = serviceDate.toString(),
                    odometerValue = odometer,
                    odometerUnit = vehicle.odometerUnit,
                    oilBrand = draft.oilBrand,
                    oilProduct = draft.oilProduct,
                    viscosityGrade = draft.viscosityGrade,
                    manufacturerApproval = draft.manufacturerApproval,
                    quantityUsedMl = quantityMl,
                    filterBrand = draft.filterBrand,
                    filterPartNumber = draft.filterPartNumber,
                    serviceProvider = draft.serviceProvider,
                    serviceType = draft.serviceType.ifBlank { "OTHER" },
                    totalCostMinor = totalCost,
                    partsCostMinor = partsCost,
                    laborCostMinor = laborCost,
                    currencyCode = draft.currencyCode.ifBlank { "CAD" },
                    distanceIntervalOverride = distanceInterval,
                    timeIntervalMonthsOverride = timeInterval,
                    notes = draft.notes,
                    createdAtUtc = existing?.createdAtUtc ?: now,
                    updatedAtUtc = now
                )
                oilChangeRepository.save(record)
                mutableUiState.update { it.copy(saveSuccessCount = it.saveSuccessCount + 1, error = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableUiState.update { it.copy(error = error.message?.takeIf(String::isNotBlank) ?: "Unable to save oil change.") }
            } finally {
                isSaveInFlight = false
                mutableUiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun clearError() = mutableUiState.update { it.copy(error = null) }


    private fun String.toOptionalPositiveInt(field: String): Int? {
        if (isBlank()) return null
        val value = trim().toIntOrNull() ?: throw IllegalArgumentException("Enter a valid $field.")
        require(value > 0) { "$field must be greater than zero." }
        return value
    }

    private fun String.toMinorUnits(multiplier: Int, field: String, allowZero: Boolean = false): Long? {
        if (isBlank()) return null
        val amount = runCatching { BigDecimal(trim()) }.getOrElse { throw IllegalArgumentException("Enter a valid $field.") }
        val result = runCatching { amount.multiply(BigDecimal(multiplier)).longValueExact() }
            .getOrElse { throw IllegalArgumentException("$field is outside the supported range.") }
        require(if (allowZero) result >= 0L else result > 0L) {
            "$field must be ${if (allowZero) "zero or greater" else "greater than zero"}."
        }
        return result
    }

    class Factory(
        private val vehicleId: String,
        private val vehicleRepository: VehicleRepository,
        private val oilChangeRepository: OilChangeRepository,
        private val odometerRepository: OdometerRepository,
        private val specificationRepository: VehicleSpecificationRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(OilChangeViewModel::class.java))
            return OilChangeViewModel(vehicleId, vehicleRepository, oilChangeRepository, odometerRepository, specificationRepository) as T
        }
    }
}
