package ca.gllavoie.vehicletracker.ui.garage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ca.gllavoie.vehicletracker.data.local.OdometerReadingEntity
import ca.gllavoie.vehicletracker.data.local.VehicleEntity
import ca.gllavoie.vehicletracker.data.repository.OdometerRecordResult
import ca.gllavoie.vehicletracker.data.repository.OdometerRepository
import ca.gllavoie.vehicletracker.data.repository.VehicleRepository
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OdometerReviewDraft(
    val value: Long,
    val date: LocalDate,
    val notes: String?,
    val reason: String
)

internal fun shouldSaveOdometerDraftOnDone(value: String, date: String, notes: String, defaultDate: String): Boolean =
    value.isNotBlank() || notes.isNotBlank() || date != defaultDate

data class OdometerUiState(
    val vehicle: VehicleEntity? = null,
    val readings: List<OdometerReadingEntity> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val saveSuccessCount: Int = 0,
    val reviewDraft: OdometerReviewDraft? = null,
    val error: String? = null,
    val message: String? = null
)

class OdometerViewModel(
    private val vehicleId: String,
    private val vehicleRepository: VehicleRepository,
    private val odometerRepository: OdometerRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(OdometerUiState())
    val uiState: StateFlow<OdometerUiState> = mutableUiState.asStateFlow()
    private var isSaveInFlight = false

    init {
        viewModelScope.launch {
            try {
                val vehicle = vehicleRepository.getVehicle(vehicleId)
                mutableUiState.update { it.copy(vehicle = vehicle) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableUiState.update { it.copy(error = error.message ?: "Unable to load vehicle.") }
            }
            odometerRepository.observeReadings(vehicleId).collect { readings ->
                mutableUiState.update { it.copy(readings = readings, isLoading = false) }
            }
        }
    }

    fun record(valueText: String, dateText: String, notes: String, confirmAnomaly: Boolean = false) {
        if (isSaveInFlight) return
        val value = valueText.trim().toLongOrNull()
        val date = runCatching { LocalDate.parse(dateText.trim()) }.getOrNull()
        if (value == null) {
            mutableUiState.update { it.copy(error = "Enter an odometer reading using digits only.") }
            return
        }
        if (date == null) {
            mutableUiState.update { it.copy(error = "Enter a valid date as YYYY-MM-DD.") }
            return
        }
        isSaveInFlight = true
        mutableUiState.update { it.copy(isSaving = true, error = null, message = null) }
        viewModelScope.launch {
            try {
                when (val result = odometerRepository.addReading(vehicleId, value, date, notes, confirmAnomaly)) {
                    is OdometerRecordResult.NeedsConfirmation -> {
                        mutableUiState.update {
                            it.copy(reviewDraft = OdometerReviewDraft(value, date, notes, result.reason))
                        }
                    }
                    is OdometerRecordResult.Saved -> {
                        mutableUiState.update {
                            it.copy(reviewDraft = null, saveSuccessCount = it.saveSuccessCount + 1, message = "Odometer reading saved.")
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableUiState.update { it.copy(error = error.message?.takeIf(String::isNotBlank) ?: "Unable to save odometer reading.") }
            } finally {
                isSaveInFlight = false
                mutableUiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun confirmReview() {
        val draft = mutableUiState.value.reviewDraft ?: return
        record(draft.value.toString(), draft.date.toString(), draft.notes.orEmpty(), confirmAnomaly = true)
    }

    fun dismissReview() {
        mutableUiState.update { it.copy(reviewDraft = null) }
    }

    fun clearError() {
        mutableUiState.update { it.copy(error = null) }
    }

    fun clearMessage() {
        mutableUiState.update { it.copy(message = null) }
    }

    class Factory(
        private val vehicleId: String,
        private val vehicleRepository: VehicleRepository,
        private val odometerRepository: OdometerRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(OdometerViewModel::class.java))
            return OdometerViewModel(vehicleId, vehicleRepository, odometerRepository) as T
        }
    }
}
