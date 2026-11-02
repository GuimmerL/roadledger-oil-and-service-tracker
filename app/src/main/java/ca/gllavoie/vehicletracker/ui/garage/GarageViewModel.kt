package ca.gllavoie.vehicletracker.ui.garage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ca.gllavoie.vehicletracker.data.local.VehicleEntity
import ca.gllavoie.vehicletracker.data.repository.ReceiptRepository
import ca.gllavoie.vehicletracker.data.repository.VehicleRepository
import ca.gllavoie.vehicletracker.domain.calculation.OilIntervalMode
import ca.gllavoie.vehicletracker.domain.vehicle.VehicleType
import ca.gllavoie.vehicletracker.preferences.convertDailyDistance
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VehicleDraft(
    val nickname: String = "",
    val modelYear: String = "",
    val make: String = "",
    val model: String = "",
    val trim: String = "",
    val engine: String = "",
    val engineCode: String = "",
    val fuelType: String = "",
    val transmission: String = "",
    val vin: String = "",
    val licensePlate: String = "",
    val notes: String = "",
    val odometerUnit: String = "KM",
    val defaultSpecificationId: String? = null,
    val startingDailyUsage: String = "",
    val vehicleType: String = "OTHER"
)

data class GarageUiState(
    val activeVehicles: List<VehicleEntity> = emptyList(),
    val archivedVehicles: List<VehicleEntity> = emptyList(),
    val showArchived: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccessCount: Int = 0,
    val saveError: String? = null,
    val archiveSuccessCount: Int = 0,
    val archiveError: String? = null,
    val deleteSuccessCount: Int = 0,
    val deleteError: String? = null,
    val pendingVehicleActionIds: Set<String> = emptySet(),
    val message: String? = null,
    val hasLoadedActiveVehicles: Boolean = false,
    val hasLoadedArchivedVehicles: Boolean = false
) {
    val visibleVehicles: List<VehicleEntity>
        get() = if (showArchived) archivedVehicles else activeVehicles
}

class GarageViewModel(
    private val repository: VehicleRepository,
    private val receiptRepository: ReceiptRepository? = null
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(GarageUiState())
    private val pendingVehicleActions = mutableSetOf<String>()
    private val pendingIntervalModeUpdates = mutableSetOf<String>()
    private var isSaveInFlight = false
    val uiState: StateFlow<GarageUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeActiveVehicles().collect { vehicles ->
                mutableUiState.update { it.copy(activeVehicles = vehicles, hasLoadedActiveVehicles = true) }
            }
        }
        viewModelScope.launch {
            repository.observeArchivedVehicles().collect { vehicles ->
                mutableUiState.update { it.copy(archivedVehicles = vehicles, hasLoadedArchivedVehicles = true) }
            }
        }
    }

    fun showArchived(show: Boolean) {
        mutableUiState.update { it.copy(showArchived = show, message = null) }
    }

    fun clearMessage() {
        mutableUiState.update { it.copy(message = null) }
    }

    fun clearSaveError() {
        mutableUiState.update { it.copy(saveError = null) }
    }

    fun clearArchiveError() {
        mutableUiState.update { it.copy(archiveError = null) }
    }

    fun clearDeleteError() {
        mutableUiState.update { it.copy(deleteError = null) }
    }

    fun saveVehicle(draft: VehicleDraft, existing: VehicleEntity? = null) {
        if (isSaveInFlight) return
        isSaveInFlight = true
        val vehicleId = existing?.id ?: UUID.randomUUID().toString()
        mutableUiState.update { it.copy(isSaving = true, saveError = null, message = null) }
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val year = draft.modelYear.trim().takeIf(String::isNotEmpty)?.toIntOrNull()
                    ?: if (draft.modelYear.isBlank()) null else throw IllegalArgumentException("Enter a valid four-digit model year.")
                if (year != null && year !in MIN_MODEL_YEAR..MAX_MODEL_YEAR) {
                    throw IllegalArgumentException("Model year must be between $MIN_MODEL_YEAR and $MAX_MODEL_YEAR.")
                }
                val odometerUnit = draft.odometerUnit.trim().uppercase()
                val vehicleType = VehicleType.entries.firstOrNull { it.storedValue == draft.vehicleType }
                    ?: throw IllegalArgumentException("Choose a valid vehicle type.")
                val dailyUsageText = draft.startingDailyUsage.trim()
                val dailyUsageInVehicleUnit = if (dailyUsageText.isBlank()) null else dailyUsageText.toDoubleOrNull()
                    ?: throw IllegalArgumentException("Enter a valid starting daily-use value.")
                if (dailyUsageInVehicleUnit != null && (!dailyUsageInVehicleUnit.isFinite() || dailyUsageInVehicleUnit <= 0.0 || dailyUsageInVehicleUnit > MAX_DAILY_DISTANCE_PER_DAY)) {
                    throw IllegalArgumentException("Starting daily use must be greater than zero and no more than $MAX_DAILY_DISTANCE_PER_DAY units per day.")
                }
                val dailyUsageKmPerDay = dailyUsageInVehicleUnit?.let {
                    convertDailyDistance(it, odometerUnit, "KM")
                        ?: throw IllegalArgumentException("Unable to convert starting daily use to the vehicle's stored unit.")
                }
                val vehicle = VehicleEntity(
                    id = vehicleId,
                    nickname = draft.nickname.trim().ifEmpty { null },
                    modelYear = year,
                    make = draft.make,
                    model = draft.model,
                    trim = draft.trim.trim().ifEmpty { null },
                    engine = draft.engine.trim().ifEmpty { null },
                    engineCode = draft.engineCode.trim().ifEmpty { null },
                    fuelType = draft.fuelType.trim().ifEmpty { null },
                    transmission = draft.transmission.trim().ifEmpty { null },
                    vin = draft.vin.trim().ifEmpty { null },
                    licensePlate = draft.licensePlate.trim().ifEmpty { null },
                    odometerUnit = odometerUnit,
                    acquiredOn = existing?.acquiredOn,
                    photoUri = existing?.photoUri,
                    notes = draft.notes.trim().ifEmpty { null },
                    defaultSpecificationId = draft.defaultSpecificationId,
                    archivedAtUtc = existing?.archivedAtUtc,
                    createdAtUtc = existing?.createdAtUtc ?: now,
                    updatedAtUtc = now,
                    startingDailyUsageKmPerDay = dailyUsageKmPerDay,
                    oilIntervalMode = existing?.oilIntervalMode ?: OilIntervalMode.NORMAL.storedValue,
                    vehicleType = vehicleType.storedValue
                )
                repository.save(vehicle)
                mutableUiState.update { it.copy(saveSuccessCount = it.saveSuccessCount + 1, message = "Vehicle saved.") }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                mutableUiState.update { it.copy(saveError = error.message?.takeIf(String::isNotBlank) ?: "Unable to save vehicle.") }
            } finally {
                isSaveInFlight = false
                mutableUiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun setOilIntervalMode(vehicleId: String, mode: String) {
        if (mode !in OilIntervalMode.entries.map(OilIntervalMode::storedValue)) return
        if (!pendingIntervalModeUpdates.add(vehicleId)) return
        viewModelScope.launch {
            try {
                val vehicle = requireNotNull(repository.getVehicle(vehicleId)) { "Vehicle was not found." }
                require(vehicle.archivedAtUtc == null) { "Restore this vehicle before changing its interval mode." }
                if (vehicle.oilIntervalMode == mode) return@launch
                repository.save(vehicle.copy(oilIntervalMode = mode, updatedAtUtc = System.currentTimeMillis()))
                mutableUiState.update {
                    it.copy(message = "${OilIntervalMode.fromStoredValue(mode).displayName} interval selected.")
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                mutableUiState.update {
                    it.copy(message = error.message?.takeIf(String::isNotBlank) ?: "Unable to change oil interval mode.")
                }
            } finally {
                pendingIntervalModeUpdates.remove(vehicleId)
            }
        }
    }

    fun archiveVehicle(vehicle: VehicleEntity) {
        if (!pendingVehicleActions.add(vehicle.id)) return
        mutableUiState.update { it.copy(pendingVehicleActionIds = pendingVehicleActions.toSet(), archiveError = null, message = null) }
        viewModelScope.launch {
            try {
                val archived = repository.archive(vehicle.id, System.currentTimeMillis())
                if (archived) {
                    mutableUiState.update {
                        it.copy(archiveSuccessCount = it.archiveSuccessCount + 1, message = "Vehicle archived. Its records are retained.")
                    }
                } else {
                    mutableUiState.update { it.copy(archiveError = "Vehicle was not found.") }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                mutableUiState.update { it.copy(archiveError = error.message?.takeIf(String::isNotBlank) ?: "Unable to archive vehicle.") }
            } finally {
                pendingVehicleActions.remove(vehicle.id)
                mutableUiState.update { it.copy(pendingVehicleActionIds = pendingVehicleActions.toSet()) }
            }
        }
    }

    fun restoreVehicle(vehicle: VehicleEntity) {
        if (!pendingVehicleActions.add(vehicle.id)) return
        mutableUiState.update { it.copy(pendingVehicleActionIds = pendingVehicleActions.toSet(), message = null) }
        viewModelScope.launch {
            try {
                val restored = repository.restore(vehicle.id, System.currentTimeMillis())
                mutableUiState.update {
                    it.copy(message = if (restored) "Vehicle restored." else "Vehicle was not found.")
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                mutableUiState.update { it.copy(message = error.message?.takeIf(String::isNotBlank) ?: "Unable to restore vehicle.") }
            } finally {
                pendingVehicleActions.remove(vehicle.id)
                mutableUiState.update { it.copy(pendingVehicleActionIds = pendingVehicleActions.toSet()) }
            }
        }
    }

    fun permanentlyDeleteVehicle(vehicle: VehicleEntity) {
        if (!pendingVehicleActions.add(vehicle.id)) return
        mutableUiState.update {
            it.copy(pendingVehicleActionIds = pendingVehicleActions.toSet(), deleteError = null, message = null)
        }
        viewModelScope.launch {
            try {
                val snapshot = repository.deletePermanently(vehicle.id)
                if (!snapshot.deleted) {
                    mutableUiState.update { it.copy(deleteError = "Vehicle was not found.") }
                    return@launch
                }
                val failedFiles = if (snapshot.attachments.isEmpty()) 0 else {
                    requireNotNull(receiptRepository) { "Receipt cleanup is unavailable." }
                        .deleteStoredFiles(snapshot.attachments)
                }
                val message = if (failedFiles == 0) {
                    "Vehicle permanently deleted with its service, fuel, odometer and receipt records."
                } else {
                    "Vehicle records were deleted, but $failedFiles receipt file(s) could not be removed from private storage."
                }
                mutableUiState.update {
                    it.copy(deleteSuccessCount = it.deleteSuccessCount + 1, message = message)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                mutableUiState.update { it.copy(deleteError = error.message?.takeIf(String::isNotBlank) ?: "Unable to delete vehicle.") }
            } finally {
                pendingVehicleActions.remove(vehicle.id)
                mutableUiState.update { it.copy(pendingVehicleActionIds = pendingVehicleActions.toSet()) }
            }
        }
    }

    class Factory(
        private val repository: VehicleRepository,
        private val receiptRepository: ReceiptRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(GarageViewModel::class.java))
            return GarageViewModel(repository, receiptRepository) as T
        }
    }

    private companion object {
        const val MIN_MODEL_YEAR = 1886
        const val MAX_MODEL_YEAR = 9999
        const val MAX_DAILY_DISTANCE_PER_DAY = 2_000.0
    }
}
