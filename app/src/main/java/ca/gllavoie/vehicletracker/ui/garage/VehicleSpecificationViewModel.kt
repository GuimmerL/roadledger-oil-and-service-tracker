package ca.gllavoie.vehicletracker.ui.garage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ca.gllavoie.vehicletracker.data.local.VehicleSpecificationOverrideEntity
import ca.gllavoie.vehicletracker.data.repository.VehicleSpecificationRepository
import java.math.BigDecimal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VehicleSpecificationDraft(
    val viscosityGrade: String = "",
    val manufacturerApproval: String = "",
    val capacityLitres: String = "",
    val distanceInterval: String = "",
    val timeIntervalMonths: String = "",
    val oilFilterPartNumber: String = "",
    val sourceDescription: String = ""
)

data class VehicleSpecificationUiState(
    val override: VehicleSpecificationOverrideEntity? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val saveSuccessCount: Int = 0,
    val error: String? = null
)

class VehicleSpecificationViewModel(
    private val vehicleId: String,
    private val repository: VehicleSpecificationRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(VehicleSpecificationUiState())
    val uiState: StateFlow<VehicleSpecificationUiState> = mutableUiState.asStateFlow()
    private var isSaveInFlight = false

    init {
        viewModelScope.launch {
            try {
                repository.observeOverride(vehicleId).collect { value ->
                    mutableUiState.update { it.copy(override = value, isLoading = false) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableUiState.update { it.copy(isLoading = false, error = error.message ?: "Unable to load vehicle specifications.") }
            }
        }
    }

    fun save(draft: VehicleSpecificationDraft, distanceUnit: String) {
        if (isSaveInFlight) return
        isSaveInFlight = true
        mutableUiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                val existing = mutableUiState.value.override
                val capacity = draft.capacityLitres.toScaledInt(1_000, "Oil capacity")
                val distance = draft.distanceInterval.toOptionalPositiveInt("Distance interval")
                val months = draft.timeIntervalMonths.toOptionalPositiveInt("Time interval")
                val value = VehicleSpecificationOverrideEntity(
                    id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                    vehicleId = vehicleId,
                    viscosityGrade = draft.viscosityGrade,
                    manufacturerApproval = draft.manufacturerApproval,
                    capacityWithFilterMl = capacity,
                    distanceInterval = distance,
                    distanceUnit = distance?.let { distanceUnit },
                    timeIntervalMonths = months,
                    oilFilterPartNumber = draft.oilFilterPartNumber,
                    sourceDescription = draft.sourceDescription,
                    updatedAtUtc = System.currentTimeMillis()
                )
                repository.saveOverride(value)
                mutableUiState.update { it.copy(saveSuccessCount = it.saveSuccessCount + 1) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableUiState.update { it.copy(error = error.message?.takeIf(String::isNotBlank) ?: "Unable to save vehicle specifications.") }
            } finally {
                isSaveInFlight = false
                mutableUiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun clearError() { mutableUiState.update { it.copy(error = null) } }

    private fun String.toOptionalPositiveInt(field: String): Int? {
        if (isBlank()) return null
        val value = trim().toIntOrNull() ?: throw IllegalArgumentException("Enter a valid $field.")
        require(value > 0) { "$field must be greater than zero." }
        return value
    }

    private fun String.toScaledInt(multiplier: Int, field: String): Int? {
        if (isBlank()) return null
        val value = runCatching { BigDecimal(trim()).multiply(BigDecimal(multiplier)).intValueExact() }
            .getOrElse { throw IllegalArgumentException("Enter a valid $field in litres (up to 3 decimal places).") }
        require(value > 0) { "$field must be greater than zero." }
        return value
    }

    class Factory(private val vehicleId: String, private val repository: VehicleSpecificationRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(VehicleSpecificationViewModel::class.java))
            return VehicleSpecificationViewModel(vehicleId, repository) as T
        }
    }
}
