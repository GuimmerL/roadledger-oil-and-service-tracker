package ca.gllavoie.vehicletracker.ui.fuel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ca.gllavoie.vehicletracker.data.local.FuelFillUpEntity
import ca.gllavoie.vehicletracker.data.local.VehicleEntity
import ca.gllavoie.vehicletracker.data.repository.FuelRecordResult
import ca.gllavoie.vehicletracker.data.repository.FuelRepository
import ca.gllavoie.vehicletracker.data.repository.VehicleRepository
import ca.gllavoie.vehicletracker.domain.calculation.VolumeUnit
import ca.gllavoie.vehicletracker.domain.calculation.volumeToLitres
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FuelDraft(
    val date: String = LocalDate.now().toString(),
    val odometer: String = "",
    val volume: String = "",
    val volumeUnit: String = "L",
    val cost: String = "",
    val currency: String = "CAD",
    val fuelType: String = "Gasoline",
    val station: String = "",
    val isFullTank: Boolean = true,
    val notes: String = ""
)

data class FuelReviewDraft(val draft: FuelDraft, val reason: String)

data class FuelUiState(
    val vehicle: VehicleEntity? = null,
    val fillUps: List<FuelFillUpEntity> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val saveSuccessCount: Int = 0,
    val review: FuelReviewDraft? = null,
    val error: String? = null,
    val message: String? = null
)

class FuelViewModel(
    private val vehicleId: String,
    private val vehicleRepository: VehicleRepository,
    private val fuelRepository: FuelRepository,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val now: () -> Long = System::currentTimeMillis
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(FuelUiState())
    val uiState: StateFlow<FuelUiState> = mutableUiState.asStateFlow()
    private var isSaveInFlight = false

    init {
        viewModelScope.launch {
            try {
                val vehicle = vehicleRepository.getVehicle(vehicleId)
                mutableUiState.update { it.copy(vehicle = vehicle) }
                fuelRepository.observeFillUps(vehicleId).collect { fillUps ->
                    mutableUiState.update { it.copy(fillUps = fillUps, isLoading = false) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableUiState.update { it.copy(isLoading = false, error = error.message ?: "Unable to load fuel history.") }
            }
        }
    }

    fun save(draft: FuelDraft, confirmAnomaly: Boolean = false) {
        if (isSaveInFlight) return
        val vehicle = mutableUiState.value.vehicle
        if (vehicle == null) {
            mutableUiState.update { it.copy(error = "Vehicle is not available.") }
            return
        }
        isSaveInFlight = true
        mutableUiState.update { it.copy(isSaving = true, error = null, message = null) }
        viewModelScope.launch {
            try {
                val date = LocalDate.parse(draft.date.trim())
                val odometer = draft.odometer.trim().toLongOrNull()
                    ?: throw IllegalArgumentException("Enter the odometer reading using digits only.")
                val inputVolume = draft.volume.trim().toBigDecimalOrNull()
                    ?: throw IllegalArgumentException("Enter a valid fuel volume.")
                require(inputVolume > BigDecimal.ZERO) { "Fuel volume must be greater than zero." }
                val unit = when (draft.volumeUnit) {
                    "L" -> VolumeUnit.LITRE
                    "US_GAL" -> VolumeUnit.US_GALLON
                    "IMP_GAL" -> VolumeUnit.IMPERIAL_GALLON
                    else -> throw IllegalArgumentException("Choose litres, US gallons, or imperial gallons.")
                }
                val millilitres = BigDecimal.valueOf(volumeToLitres(inputVolume.toDouble(), unit))
                    .movePointRight(3).setScale(0, RoundingMode.HALF_UP).longValueExact()
                val costMinor = if (draft.cost.isBlank()) null else {
                    val cost = draft.cost.trim().toBigDecimalOrNull() ?: throw IllegalArgumentException("Enter a valid fuel cost.")
                    require(cost >= BigDecimal.ZERO) { "Fuel cost cannot be negative." }
                    cost.movePointRight(2).longValueExact()
                }
                val timestamp = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
                val currentTime = now()
                val fillUp = FuelFillUpEntity(
                    vehicleId = vehicleId,
                    filledAtUtc = timestamp,
                    odometerValue = odometer,
                    odometerUnit = vehicle.odometerUnit,
                    volumeMillilitres = millilitres,
                    totalCostMinor = costMinor,
                    currencyCode = draft.currency.trim().uppercase(),
                    fuelType = draft.fuelType.trim().ifEmpty { null },
                    isFullTank = draft.isFullTank,
                    stationName = draft.station.trim().ifEmpty { null },
                    notes = draft.notes.trim().ifEmpty { null },
                    createdAtUtc = currentTime,
                    updatedAtUtc = currentTime
                )
                when (val result = fuelRepository.addFillUp(fillUp, confirmAnomaly)) {
                    is FuelRecordResult.NeedsConfirmation -> mutableUiState.update { it.copy(review = FuelReviewDraft(draft, result.reason)) }
                    is FuelRecordResult.Saved -> mutableUiState.update {
                        it.copy(review = null, saveSuccessCount = it.saveSuccessCount + 1, message = "Fuel fill-up saved.")
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableUiState.update { it.copy(error = error.message?.takeIf(String::isNotBlank) ?: "Unable to save fuel fill-up.") }
            } finally {
                isSaveInFlight = false
                mutableUiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun confirmReview() {
        val draft = mutableUiState.value.review?.draft ?: return
        save(draft, confirmAnomaly = true)
    }

    fun dismissReview() { mutableUiState.update { it.copy(review = null) } }
    fun clearError() { mutableUiState.update { it.copy(error = null) } }
    fun clearMessage() { mutableUiState.update { it.copy(message = null) } }

    class Factory(
        private val vehicleId: String,
        private val vehicleRepository: VehicleRepository,
        private val fuelRepository: FuelRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(FuelViewModel::class.java))
            return FuelViewModel(vehicleId, vehicleRepository, fuelRepository) as T
        }
    }
}
