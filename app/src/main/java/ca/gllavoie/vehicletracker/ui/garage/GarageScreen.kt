package ca.gllavoie.vehicletracker.ui.garage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.gllavoie.vehicletracker.R
import ca.gllavoie.vehicletracker.domain.calculation.MaintenanceStatus
import ca.gllavoie.vehicletracker.data.local.VehicleEntity
import ca.gllavoie.vehicletracker.data.local.VehicleSpecificationEntity
import ca.gllavoie.vehicletracker.data.repository.OdometerRepository
import ca.gllavoie.vehicletracker.data.repository.OilChangeRepository
import ca.gllavoie.vehicletracker.data.repository.ReceiptRepository
import ca.gllavoie.vehicletracker.data.repository.VehicleRepository
import ca.gllavoie.vehicletracker.data.repository.VehicleSpecificationRepository
import ca.gllavoie.vehicletracker.data.repository.calculateVehicleOilSchedule
import ca.gllavoie.vehicletracker.data.repository.estimateDailyDistancePerDay
import ca.gllavoie.vehicletracker.data.repository.FuelRepository
import ca.gllavoie.vehicletracker.data.repository.VehicleCatalogOptions
import ca.gllavoie.vehicletracker.data.repository.VehicleCatalogSeeder
import ca.gllavoie.vehicletracker.ui.fuel.FuelTrackingDialog
import ca.gllavoie.vehicletracker.preferences.MeasurementSystemPreferences
import ca.gllavoie.vehicletracker.preferences.GarageSortPreferences
import ca.gllavoie.vehicletracker.domain.calculation.OilIntervalMode
import ca.gllavoie.vehicletracker.domain.vehicle.VehicleType
import ca.gllavoie.vehicletracker.preferences.convertDailyDistance
import ca.gllavoie.vehicletracker.preferences.convertDistanceIntervalToUnit
import ca.gllavoie.vehicletracker.preferences.formatDailyDistance
import kotlinx.coroutines.flow.flowOf

@Composable
fun GarageRoute(
    repository: VehicleRepository,
    odometerRepository: OdometerRepository,
    oilChangeRepository: OilChangeRepository,
    receiptRepository: ReceiptRepository,
    specificationRepository: VehicleSpecificationRepository,
    fuelRepository: FuelRepository,
    selectedVehicleId: String? = null,
    onVehicleSelected: (String) -> Unit = {},
    onBackToVehicles: () -> Unit = {}
) {
    val garageViewModel: GarageViewModel = viewModel(factory = GarageViewModel.Factory(repository, receiptRepository))
    val state by garageViewModel.uiState.collectAsStateWithLifecycle()
    var odometerVehicleId by rememberSaveable { mutableStateOf<String?>(null) }
    var oilChangeVehicleId by rememberSaveable { mutableStateOf<String?>(null) }
    var fuelVehicleId by rememberSaveable { mutableStateOf<String?>(null) }
    var specificationVehicleId by rememberSaveable { mutableStateOf<String?>(null) }
    GarageScreen(
        state = state,
        onShowArchived = garageViewModel::showArchived,
        onSave = garageViewModel::saveVehicle,
        onArchive = garageViewModel::archiveVehicle,
        onRestore = garageViewModel::restoreVehicle,
        onMessageShown = garageViewModel::clearMessage,
        onClearSaveError = garageViewModel::clearSaveError,
        onClearArchiveError = garageViewModel::clearArchiveError,
        onDelete = garageViewModel::permanentlyDeleteVehicle,
        onClearDeleteError = garageViewModel::clearDeleteError,
        onOpenOdometer = { odometerVehicleId = it },
        onOpenOilChanges = { oilChangeVehicleId = it },
        onOpenFuel = { fuelVehicleId = it },
        onOpenSpecifications = { specificationVehicleId = it },
        selectedVehicleId = selectedVehicleId,
        onVehicleSelected = onVehicleSelected,
        onBackToVehicles = onBackToVehicles,
        onSetOilIntervalMode = garageViewModel::setOilIntervalMode,
        odometerRepository = odometerRepository,
        oilChangeRepository = oilChangeRepository,
        receiptRepository = receiptRepository,
        specificationRepository = specificationRepository
    )
    odometerVehicleId?.let { vehicleId ->
        OdometerRoute(vehicleId, repository, odometerRepository) { odometerVehicleId = null }
    }
    oilChangeVehicleId?.let { vehicleId ->
        OilChangeRoute(vehicleId, repository, oilChangeRepository, odometerRepository, receiptRepository, specificationRepository) { oilChangeVehicleId = null }
    }
    fuelVehicleId?.let { vehicleId ->
        FuelTrackingDialog(vehicleId, repository, fuelRepository) { fuelVehicleId = null }
    }
    specificationVehicleId?.let { vehicleId ->
        val target = state.activeVehicles.firstOrNull { it.id == vehicleId }
        if (target != null) VehicleSpecificationRoute(target, specificationRepository) { specificationVehicleId = null }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun GarageScreen(
    state: GarageUiState,
    onShowArchived: (Boolean) -> Unit,
    onSave: (VehicleDraft, VehicleEntity?) -> Unit,
    onArchive: (VehicleEntity) -> Unit,
    onRestore: (VehicleEntity) -> Unit,
    onMessageShown: () -> Unit,
    onClearSaveError: () -> Unit,
    onClearArchiveError: () -> Unit,
    onDelete: (VehicleEntity) -> Unit,
    onClearDeleteError: () -> Unit,
    onOpenOdometer: (String) -> Unit,
    onOpenOilChanges: (String) -> Unit,
    onOpenFuel: (String) -> Unit,
    onOpenSpecifications: (String) -> Unit,
    selectedVehicleId: String?,
    onVehicleSelected: (String) -> Unit,
    onBackToVehicles: () -> Unit,
    onSetOilIntervalMode: (String, String) -> Unit,
    odometerRepository: OdometerRepository,
    oilChangeRepository: OilChangeRepository,
    receiptRepository: ReceiptRepository,
    specificationRepository: VehicleSpecificationRepository
) {
    val context = LocalContext.current
    var sortOrder by rememberSaveable { mutableStateOf(GarageSortPreferences.get(context)) }
    var sortMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val sortedVisibleVehicles = remember(state.visibleVehicles, sortOrder) {
        sortOrder.sort(state.visibleVehicles)
    }
    val sortOrderLabel = stringResource(sortOrder.labelRes)
    val sortVehiclesCd = stringResource(R.string.sort_vehicles_cd, sortOrderLabel)
    val showArchivedVehiclesCd = stringResource(R.string.show_archived_vehicles_cd)
    val loadingVehiclesCd = stringResource(R.string.loading_vehicles)
    val archivingVehicleCd = stringResource(R.string.archiving_vehicle_cd)
    val deletingVehicleCd = stringResource(R.string.deleting_vehicle_cd)
    val loadingVehicleDetailsCd = stringResource(R.string.loading_vehicle_details)
    val savingVehicleCd = stringResource(R.string.saving_vehicle_cd)
    var editorTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var archiveTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteTargetId by rememberSaveable { mutableStateOf<String?>(null) }

    var saveCountAtEditorOpen by rememberSaveable { mutableIntStateOf(state.saveSuccessCount) }
    var archiveCountAtTarget by rememberSaveable { mutableIntStateOf(state.archiveSuccessCount) }
    var deleteCountAtTarget by rememberSaveable { mutableIntStateOf(state.deleteSuccessCount) }
    var creatingVehicle by rememberSaveable { mutableStateOf(false) }
    val editorTarget = state.activeVehicles.firstOrNull { it.id == editorTargetId }
    val selectedVehicle = (state.activeVehicles + state.archivedVehicles).firstOrNull { it.id == selectedVehicleId }
    val archiveTarget = (state.activeVehicles + state.archivedVehicles).firstOrNull { it.id == archiveTargetId }
    val deleteTarget = (state.activeVehicles + state.archivedVehicles).firstOrNull { it.id == deleteTargetId }
    LaunchedEffect(selectedVehicleId, selectedVehicle, state.hasLoadedActiveVehicles, state.hasLoadedArchivedVehicles) {
        if (selectedVehicleId != null && state.hasLoadedActiveVehicles && state.hasLoadedArchivedVehicles && selectedVehicle == null) {
            onBackToVehicles()
        }
    }
    LaunchedEffect(state.deleteSuccessCount) {
        if (deleteTargetId != null && state.deleteSuccessCount > deleteCountAtTarget) {
            deleteTargetId = null
            onClearDeleteError()
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val catalogEntries by remember(specificationRepository) {
        specificationRepository.observeSpecificationsForDataset(VehicleCatalogSeeder.CATALOG_ID_PREFIX)
    }.collectAsStateWithLifecycle(emptyList())
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            onMessageShown()
        }
    }
    LaunchedEffect(state.saveSuccessCount) {
        if ((creatingVehicle || editorTargetId != null) && state.saveSuccessCount > saveCountAtEditorOpen) {
            creatingVehicle = false
            editorTargetId = null
            onClearSaveError()
        }
    }
    LaunchedEffect(state.archiveSuccessCount) {
        if (archiveTargetId != null && state.archiveSuccessCount > archiveCountAtTarget) {
            archiveTargetId = null
            onClearArchiveError()
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        if (selectedVehicleId != null && selectedVehicle != null) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                VehicleCard(
                    vehicle = selectedVehicle,
                    archived = selectedVehicle.archivedAtUtc != null,
                    pending = selectedVehicle.id in state.pendingVehicleActionIds,
                    detailMode = true,
                    odometerRepository = odometerRepository,
                    oilChangeRepository = oilChangeRepository,
                    receiptRepository = receiptRepository,
                    specificationRepository = specificationRepository,
                    onOpenDetails = {},
                    onChangeIntervalMode = { mode -> onSetOilIntervalMode(selectedVehicle.id, mode) },
                    onEdit = {
                        editorTargetId = selectedVehicle.id
                        saveCountAtEditorOpen = state.saveSuccessCount
                    },
                    onArchive = {
                        archiveTargetId = selectedVehicle.id
                        archiveCountAtTarget = state.archiveSuccessCount
                        onClearArchiveError()
                    },
                    onRestore = { onRestore(selectedVehicle) },
                    onDelete = {
                        deleteTargetId = selectedVehicle.id
                        deleteCountAtTarget = state.deleteSuccessCount
                        onClearDeleteError()
                    },
                    onOpenOdometer = { onOpenOdometer(selectedVehicle.id) },
                    onOpenOilChanges = { onOpenOilChanges(selectedVehicle.id) },
                    onOpenFuel = { onOpenFuel(selectedVehicle.id) },
                    onOpenSpecifications = { onOpenSpecifications(selectedVehicle.id) }
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        if (state.showArchived) stringResource(R.string.vehicles_archived_title)
                        else stringResource(R.string.vehicles_active_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.show_archived_label), style = MaterialTheme.typography.labelMedium)
                        Switch(
                            checked = state.showArchived,
                            onCheckedChange = onShowArchived,
                            modifier = Modifier.semantics { contentDescription = showArchivedVehiclesCd }
                        )
                    }
                }
                if (!state.showArchived && state.hasLoadedActiveVehicles) {
                    Button(
                        onClick = {
                            editorTargetId = null
                            saveCountAtEditorOpen = state.saveSuccessCount
                            creatingVehicle = true
                        },
                        enabled = !state.isSaving
                    ) {
                        Text(stringResource(R.string.add_vehicle))
                    }
                }
                if (state.visibleVehicles.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.sort_vehicles_label), style = MaterialTheme.typography.bodyMedium)
                        Box {
                            TextButton(
                                onClick = { sortMenuExpanded = true },
                                modifier = Modifier.semantics { contentDescription = sortVehiclesCd }
                            ) { Text(sortOrderLabel) }
                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false }
                            ) {
                                GarageSortOrder.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                        Text(
                                            if (option == sortOrder) stringResource(R.string.sort_option_selected, stringResource(option.labelRes))
                                            else stringResource(option.labelRes)
                                        )
                                    },
                                        onClick = {
                                            sortOrder = option
                                            GarageSortPreferences.set(context, option)
                                            sortMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                val hasLoadedVisibleVehicles = if (state.showArchived) state.hasLoadedArchivedVehicles else state.hasLoadedActiveVehicles
                when {
                    !hasLoadedVisibleVehicles -> Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.semantics { contentDescription = loadingVehiclesCd })
                    }
                    state.visibleVehicles.isEmpty() -> EmptyGarage(
                        archived = state.showArchived,
                        onAdd = {
                            editorTargetId = null
                            saveCountAtEditorOpen = state.saveSuccessCount
                            creatingVehicle = true
                        }
                    )
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(sortedVisibleVehicles, key = VehicleEntity::id) { vehicle ->
                            VehicleCard(
                                vehicle = vehicle,
                                archived = state.showArchived,
                                pending = vehicle.id in state.pendingVehicleActionIds,
                                detailMode = false,
                                odometerRepository = odometerRepository,
                                oilChangeRepository = oilChangeRepository,
                                receiptRepository = receiptRepository,
                                specificationRepository = specificationRepository,
                                onOpenDetails = { onVehicleSelected(vehicle.id) },
                                onChangeIntervalMode = {},
                                onEdit = {
                                    editorTargetId = vehicle.id
                                    saveCountAtEditorOpen = state.saveSuccessCount
                                },
                                onArchive = {
                                    archiveTargetId = vehicle.id
                                    archiveCountAtTarget = state.archiveSuccessCount
                                    onClearArchiveError()
                                },
                                onRestore = { onRestore(vehicle) },
                                onDelete = {
                                    deleteTargetId = vehicle.id
                                    deleteCountAtTarget = state.deleteSuccessCount
                                    onClearDeleteError()
                                },
                                onOpenOdometer = { onOpenOdometer(vehicle.id) },
                                onOpenOilChanges = { onOpenOilChanges(vehicle.id) },
                                onOpenFuel = { onOpenFuel(vehicle.id) },
                                onOpenSpecifications = { onOpenSpecifications(vehicle.id) }
                            )
                        }
                    }
                }
            }
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
    if (creatingVehicle || editorTargetId != null) {
        if (editorTargetId != null && editorTarget == null) {
            AlertDialog(
                onDismissRequest = { editorTargetId = null },
                title = { Text(stringResource(R.string.loading_vehicle)) },
                text = { CircularProgressIndicator(Modifier.semantics { contentDescription = loadingVehicleDetailsCd }) },
                confirmButton = { TextButton(onClick = { editorTargetId = null }) { Text(stringResource(R.string.cancel)) } }
            )
        } else {
            VehicleEditorDialog(
                vehicle = editorTarget,
                busy = state.isSaving,
                errorMessage = state.saveError,
                catalogEntries = catalogEntries,
                specificationRepository = specificationRepository,
                onDismiss = {
                    creatingVehicle = false
                    editorTargetId = null
                    onClearSaveError()
                },
                onSaveErrorDismiss = onClearSaveError,
                onSave = onSave
            )
        }
    }
    archiveTarget?.let { target ->
        val actionPending = target.id in state.pendingVehicleActionIds
        AlertDialog(
            onDismissRequest = {
                if (!actionPending) {
                    archiveTargetId = null
                    onClearArchiveError()
                }
            },
            title = { Text(stringResource(R.string.archive_vehicle_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(
                            R.string.archive_vehicle_message,
                            listOfNotNull(target.modelYear?.toString(), target.make, target.model).joinToString(" ")
                        )
                    )
                    state.archiveError?.let { error ->
                        Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onArchive(target) }, enabled = !actionPending) {
                    if (actionPending) CircularProgressIndicator(Modifier.semantics { contentDescription = archivingVehicleCd }) else Text(stringResource(R.string.archive))
                }
            },
            dismissButton = {
                TextButton(onClick = { archiveTargetId = null; onClearArchiveError() }, enabled = !actionPending) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
    deleteTarget?.let { target ->
        val actionPending = target.id in state.pendingVehicleActionIds
        AlertDialog(
            onDismissRequest = {
                if (!actionPending) {
                    deleteTargetId = null
                    onClearDeleteError()
                }
            },
            title = { Text(stringResource(R.string.delete_vehicle_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(
                            R.string.delete_vehicle_message,
                            listOfNotNull(target.modelYear?.toString(), target.make, target.model).joinToString(" ")
                        )
                    )
                    state.deleteError?.let { error ->
                        Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onDelete(target) }, enabled = !actionPending) {
                    if (actionPending) CircularProgressIndicator(Modifier.semantics { contentDescription = deletingVehicleCd })
                    else Text(stringResource(R.string.delete_permanently), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetId = null; onClearDeleteError() }, enabled = !actionPending) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun EmptyGarage(archived: Boolean, onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            if (archived) stringResource(R.string.no_archived_vehicles) else stringResource(R.string.garage_empty_title),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            if (archived) stringResource(R.string.archived_vehicles_hint) else stringResource(R.string.garage_empty_hint),
            style = MaterialTheme.typography.bodyMedium
        )
        if (!archived) Button(onClick = onAdd) { Text(stringResource(R.string.add_first_vehicle)) }
    }
}

@Composable
private fun VehicleCard(
    vehicle: VehicleEntity,
    archived: Boolean,
    pending: Boolean,
    detailMode: Boolean,
    odometerRepository: OdometerRepository,
    oilChangeRepository: OilChangeRepository,
    receiptRepository: ReceiptRepository,
    specificationRepository: VehicleSpecificationRepository,
    onOpenDetails: () -> Unit,
    onChangeIntervalMode: (String) -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onOpenOdometer: () -> Unit,
    onOpenOilChanges: () -> Unit,
    onOpenFuel: () -> Unit,
    onOpenSpecifications: () -> Unit
) {
    var actionsExpanded by rememberSaveable(vehicle.id) { mutableStateOf(false) }
    val odometerReadings by remember(vehicle.id) { odometerRepository.observeReadings(vehicle.id) }.collectAsStateWithLifecycle(emptyList())
    val oilChanges by remember(vehicle.id) { oilChangeRepository.observeRecords(vehicle.id) }.collectAsStateWithLifecycle(emptyList())
    val specification by remember(vehicle.id) { specificationRepository.observeOverride(vehicle.id) }.collectAsStateWithLifecycle(null)
    val catalogSpecification by remember(vehicle.defaultSpecificationId) {
        vehicle.defaultSpecificationId?.let(specificationRepository::observeCatalogEntry) ?: flowOf(null)
    }.collectAsStateWithLifecycle(null)
    val latestReading = odometerReadings.firstOrNull()
    val latestService = oilChanges.firstOrNull()
    val schedule = remember(vehicle, latestService, odometerReadings, specification, catalogSpecification) {
        latestService?.let { calculateVehicleOilSchedule(vehicle, it, odometerReadings, specification, catalogSpecification) }
    }
    val odometerUnitLabel = stringResource(if (vehicle.odometerUnit == "MILE") R.string.unit_mi else R.string.unit_km)
    val vehicleActionsCd = stringResource(R.string.vehicle_actions_cd)
    val intervalUnitLabel = stringResource(if (vehicle.odometerUnit == "MILE") R.string.unit_mi else R.string.unit_km)
    val observedDailyUsage = remember(odometerReadings, vehicle.odometerUnit) {
        estimateDailyDistancePerDay(odometerReadings, vehicle.odometerUnit)
    }
    val startingDailyUsage = vehicle.startingDailyUsageKmPerDay?.let { convertDailyDistance(it, "KM", vehicle.odometerUnit) }
    val dailyUsageLabel = when {
        observedDailyUsage != null -> stringResource(R.string.daily_use_updated, formatDailyDistance(observedDailyUsage), odometerUnitLabel)
        startingDailyUsage != null -> stringResource(R.string.daily_use_starting, formatDailyDistance(startingDailyUsage), odometerUnitLabel)
        else -> stringResource(R.string.daily_use_estimate_hint)
    }
    val serviceDistance = latestService?.distanceIntervalOverride
    val manualDistance = specification?.takeIf { it.distanceUnit == vehicle.odometerUnit }?.distanceInterval
    val intervalMode = OilIntervalMode.fromStoredValue(vehicle.oilIntervalMode)
    val catalogDistance = catalogSpecification?.let {
        if (intervalMode == OilIntervalMode.SEVERE) it.severeDistanceInterval else it.distanceInterval
    }
    val serviceMonths = latestService?.timeIntervalMonthsOverride
    val manualMonths = specification?.timeIntervalMonths
    val catalogMonths = catalogSpecification?.let {
        if (intervalMode == OilIntervalMode.SEVERE) it.severeTimeIntervalMonths else it.timeIntervalMonths
    }
    val recommendedDistance = serviceDistance ?: manualDistance ?: catalogDistance
    val intervalSourceUnit = when {
        serviceDistance != null -> latestService?.odometerUnit
        manualDistance != null -> specification?.distanceUnit
        catalogDistance != null -> catalogSpecification?.distanceUnit
        else -> null
    }
    val displayedDistance = recommendedDistance?.let { convertDistanceIntervalToUnit(it, intervalSourceUnit, vehicle.odometerUnit) }
    val recommendedMonths = serviceMonths ?: manualMonths ?: catalogMonths
    val rawCatalogPrefix = if (intervalMode == OilIntervalMode.SEVERE) "Raw severe-service interval:" else "Raw normal-service interval:"
    val rawCatalogInterval = catalogSpecification?.serviceNotes.orEmpty().lineSequence()
        .firstOrNull { it.startsWith(rawCatalogPrefix) }
        ?.substringAfter(":")?.trim()
    val intervalDistanceText = displayedDistance?.let {
        stringResource(R.string.two_part_format, NumberFormat.getIntegerInstance().format(it), intervalUnitLabel)
    }
    val intervalMonthsText = recommendedMonths?.let { stringResource(R.string.months_format, it) }
    val intervalLabel = listOfNotNull(intervalDistanceText, intervalMonthsText)
        .joinToString(" · ").ifBlank { rawCatalogInterval ?: stringResource(R.string.not_set) }
    val relyingOnUnverifiedCatalogInterval = serviceDistance == null && manualDistance == null &&
        serviceMonths == null && manualMonths == null && !rawCatalogInterval.isNullOrBlank() &&
        !VehicleCatalogOptions.isOilIntervalVerified(catalogSpecification?.verificationStatus)
    val oilIntervalText = stringResource(R.string.oil_interval)
    val intervalHeading = when {
        serviceDistance != null || serviceMonths != null -> stringResource(R.string.interval_heading_latest_service)
        manualDistance != null || manualMonths != null -> stringResource(R.string.interval_heading_manual)
        catalogSpecification?.verificationStatus == "OFFICIAL_INTERVAL_RESEARCHED" -> stringResource(R.string.interval_heading_catalog_confirm, intervalMode.displayName)
        relyingOnUnverifiedCatalogInterval -> stringResource(R.string.interval_heading_catalog_unverified, intervalMode.displayName)
        catalogSpecification?.let { VehicleCatalogOptions.isOilIntervalVerified(it.verificationStatus) } == true -> stringResource(R.string.interval_heading_catalog_verified, intervalMode.displayName)
        else -> oilIntervalText
    }
    val statusColor = when (schedule?.status) {
        MaintenanceStatus.DUE, MaintenanceStatus.OVERDUE -> MaterialTheme.colorScheme.error
        MaintenanceStatus.DUE_SOON -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val title = listOfNotNull(vehicle.modelYear?.toString(), vehicle.make, vehicle.model).joinToString(" ")
    Card(
        modifier = Modifier.fillMaxWidth().then(
            if (detailMode) Modifier else Modifier.clickable(enabled = !pending, onClick = onOpenDetails)
        ).semantics(mergeDescendants = true) { }
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VehicleTypeIcon(
                        VehicleType.fromStoredValue(vehicle.vehicleType),
                        description = stringResource(
                            R.string.vehicle_type_icon_description,
                            VehicleType.fromStoredValue(vehicle.vehicleType).label
                        )
                    )
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(vehicle.nickname?.takeIf(String::isNotBlank) ?: title, style = MaterialTheme.typography.titleLarge)
                    if (!vehicle.nickname.isNullOrBlank()) Text(title, style = MaterialTheme.typography.bodyMedium)
                }
                Box {
                    IconButton(
                        onClick = { actionsExpanded = true },
                        enabled = !pending,
                        modifier = Modifier.semantics { contentDescription = vehicleActionsCd }
                    ) { Icon(Icons.Default.MoreVert, contentDescription = null) }
                    DropdownMenu(expanded = actionsExpanded, onDismissRequest = { actionsExpanded = false }) {
                        if (archived) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.restore)) },
                                onClick = { actionsExpanded = false; onRestore() },
                                enabled = !pending
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit_vehicle_daily_use)) },
                                onClick = { actionsExpanded = false; onEdit() },
                                enabled = !pending
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.archive)) },
                                onClick = { actionsExpanded = false; onArchive() },
                                enabled = !pending
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete_permanently), color = MaterialTheme.colorScheme.error) },
                            onClick = { actionsExpanded = false; onDelete() },
                            enabled = !pending
                        )
                    }
                }
            }
            val odometerLabel = latestReading?.let { reading ->
                val readingDate = Instant.ofEpochMilli(reading.recordedAtUtc).atZone(ZoneId.systemDefault()).toLocalDate()
                stringResource(
                    R.string.three_part_dot_format,
                    NumberFormat.getIntegerInstance().format(reading.value),
                    stringResource(if (reading.unit == "MILE") R.string.unit_mi else R.string.unit_km),
                    readingDate.toString()
                )
            } ?: stringResource(R.string.not_recorded)
            if (!detailMode) {
                Text(
                    stringResource(
                        R.string.oil_change_status_format,
                        schedule?.status?.displayLabel() ?: stringResource(R.string.no_service_logged)
                    ),
                    color = statusColor,
                    style = MaterialTheme.typography.titleMedium
                )
                val remaining = schedule?.distanceRemaining
                val remainingText = when {
                    latestService == null -> stringResource(R.string.log_an_oil_change)
                    remaining == null && schedule?.status == MaintenanceStatus.UNAVAILABLE -> stringResource(R.string.set_an_oil_interval)
                    remaining == null && schedule?.nextDueOdometer == null && schedule?.estimatedDueDate != null -> stringResource(R.string.time_based_interval)
                    remaining == null -> stringResource(R.string.record_current_mileage)
                    remaining < 0L -> stringResource(R.string.distance_overdue_format, NumberFormat.getIntegerInstance().format(kotlin.math.abs(remaining)), odometerUnitLabel)
                    else -> stringResource(R.string.distance_remaining_format, NumberFormat.getIntegerInstance().format(remaining), odometerUnitLabel)
                }
                Text(remainingText, style = MaterialTheme.typography.headlineMedium, color = statusColor)
                schedule?.nextDueOdometer?.let { due ->
                    Text(stringResource(R.string.next_oil_change_at, NumberFormat.getIntegerInstance().format(due), odometerUnitLabel), style = MaterialTheme.typography.bodySmall)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.estimated_date_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(schedule?.estimatedDueDate?.toString() ?: stringResource(R.string.not_available), style = MaterialTheme.typography.bodySmall)
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.oil_interval_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.label_value_format, intervalMode.displayName, intervalLabel), style = MaterialTheme.typography.bodySmall)
                        if (intervalHeading != oilIntervalText) {
                            Text(intervalHeading, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text(stringResource(R.string.tap_to_view_details), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(
                    stringResource(
                        R.string.oil_change_status_format,
                        schedule?.status?.displayLabel() ?: stringResource(R.string.no_service_logged)
                    ),
                    color = statusColor,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(stringResource(R.string.oil_change_interval), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = intervalMode == OilIntervalMode.NORMAL,
                        onClick = { if (!archived) onChangeIntervalMode(OilIntervalMode.NORMAL.storedValue) },
                        enabled = !archived && !pending,
                        label = { Text(stringResource(R.string.normal)) }
                    )
                    FilterChip(
                        selected = intervalMode == OilIntervalMode.SEVERE,
                        onClick = { if (!archived) onChangeIntervalMode(OilIntervalMode.SEVERE.storedValue) },
                        enabled = !archived && !pending,
                        label = { Text(stringResource(R.string.severe_heavy_use)) }
                    )
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.distance_remaining_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val remaining = schedule?.distanceRemaining
                        val remainingText = when {
                            latestService == null -> stringResource(R.string.log_an_oil_change)
                            remaining == null && schedule?.status == MaintenanceStatus.UNAVAILABLE -> stringResource(R.string.set_an_oil_interval)
                            remaining == null && schedule?.nextDueOdometer == null && schedule?.estimatedDueDate != null -> stringResource(R.string.time_based_interval)
                            remaining == null -> stringResource(R.string.record_current_mileage)
                            remaining < 0L -> stringResource(R.string.distance_overdue_format, NumberFormat.getIntegerInstance().format(kotlin.math.abs(remaining)), odometerUnitLabel)
                            else -> stringResource(R.string.distance_remaining_format, NumberFormat.getIntegerInstance().format(remaining), odometerUnitLabel)
                        }
                        Text(remainingText, style = MaterialTheme.typography.displaySmall, color = statusColor)
                        schedule?.nextDueOdometer?.let { due ->
                            Text(stringResource(R.string.next_oil_change_at, NumberFormat.getIntegerInstance().format(due), odometerUnitLabel), style = MaterialTheme.typography.bodyMedium)
                        }
                        HorizontalDivider()
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(stringResource(R.string.estimated_date_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(schedule?.estimatedDueDate?.toString() ?: stringResource(R.string.not_available), style = MaterialTheme.typography.bodySmall)
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(stringResource(R.string.oil_interval_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(intervalLabel, style = MaterialTheme.typography.bodySmall)
                                if (intervalHeading != oilIntervalText) {
                                    Text(intervalHeading, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                Text(stringResource(R.string.last_odometer_format, odometerLabel))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(dailyUsageLabel, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    if (!archived) TextButton(onClick = onEdit, enabled = !pending) { Text(stringResource(R.string.edit_daily_use)) }
                }
                val lastServiceDetail = latestService?.let { service ->
                    stringResource(
                        R.string.dot_three_part_format,
                        service.serviceDate,
                        NumberFormat.getIntegerInstance().format(service.odometerValue),
                        stringResource(if (service.odometerUnit == "MILE") R.string.unit_mi else R.string.unit_km)
                    )
                } ?: stringResource(R.string.not_recorded)
                Text(stringResource(R.string.last_service_format, lastServiceDetail))
                when {
                    latestService == null -> Text(stringResource(R.string.log_oil_change_to_start), style = MaterialTheme.typography.bodySmall)
                    serviceDistance != null || serviceMonths != null -> Text(stringResource(R.string.latest_service_interval_priority), style = MaterialTheme.typography.bodySmall)
                    manualDistance != null || manualMonths != null -> Text(stringResource(R.string.manual_interval_priority, intervalMode.displayName.lowercase()), style = MaterialTheme.typography.bodySmall)
                    intervalMode == OilIntervalMode.SEVERE && catalogSpecification?.let { it.severeDistanceInterval == null && it.severeTimeIntervalMonths == null } == true -> Text(stringResource(R.string.no_severe_interval_warning), style = MaterialTheme.typography.bodySmall)
                    schedule?.status == MaintenanceStatus.UNAVAILABLE -> Text(stringResource(R.string.unavailable_interval_hint), style = MaterialTheme.typography.bodySmall)
                    schedule?.nextDueOdometer != null && schedule.estimatedDueDate == null -> Text(stringResource(R.string.date_estimate_hint), style = MaterialTheme.typography.bodySmall)
                }
                if (!archived) {
                    OutlinedButton(onClick = onOpenOdometer, enabled = !pending, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.update_current_mileage))
                    }
                    OutlinedButton(onClick = onOpenSpecifications, enabled = !pending, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.edit_oil_interval_specs))
                    }
                }
                HorizontalDivider()
                val viscosity = catalogSpecification?.viscosityGrade ?: specification?.viscosityGrade ?: latestService?.viscosityGrade
                val approval = catalogSpecification?.manufacturerApproval ?: specification?.manufacturerApproval ?: latestService?.manufacturerApproval
                val filter = catalogSpecification?.oilFilterPartNumber ?: specification?.oilFilterPartNumber ?: latestService?.filterPartNumber
                Text(stringResource(R.string.vehicle_information), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.fuel_type_format, vehicle.fuelType?.takeIf(String::isNotBlank) ?: stringResource(R.string.not_specified)))
                val engineText = listOfNotNull(
                    vehicle.engine,
                    vehicle.engineCode?.let { stringResource(R.string.engine_code_format, it) }
                ).joinToString(" · ").ifBlank { stringResource(R.string.not_specified) }
                Text(stringResource(R.string.engine_format, engineText))
                Text(stringResource(R.string.transmission_format, vehicle.transmission?.takeIf(String::isNotBlank) ?: stringResource(R.string.not_specified)))
                Text(stringResource(R.string.recommended_oil_type_format, viscosity ?: stringResource(R.string.not_specified)))
                Text(stringResource(R.string.oil_approval_format, approval ?: stringResource(R.string.not_specified)))
                Text(stringResource(R.string.oil_filter_format, filter ?: stringResource(R.string.not_specified)))
                val capacityMl = catalogSpecification?.capacityWithFilterMl ?: specification?.capacityWithFilterMl
                Text(stringResource(R.string.oil_capacity_format, capacityMl?.let { stringResource(R.string.milliliters_format, it) } ?: stringResource(R.string.not_specified)))
                catalogSpecification?.let { catalog ->
                    val csvOilRecommendation = catalogNoteValue(catalog, "Raw oil recommendation")
                        ?.let { stringResource(R.string.csv_oil_recommendation_format, it) }
                    val csvFilterDescription = catalogNoteValue(catalog, "Raw oil filter type/form factor")
                        ?.let { stringResource(R.string.csv_filter_description_format, it) }
                    val oilLifeMonitor = catalogNoteValue(catalog, "Uses oil-life monitor")
                        ?.let { stringResource(R.string.oil_life_monitor_format, it) }
                    val resetProcedure = catalogNoteValue(catalog, "Oil reset procedure")
                        ?.let { stringResource(R.string.reset_procedure_format, it) }
                    val generation = catalogNoteValue(catalog, "Generation code")
                        ?.let { stringResource(R.string.generation_format, it) }
                    val extraCatalogDetails = listOfNotNull(
                        csvOilRecommendation,
                        csvFilterDescription,
                        oilLifeMonitor,
                        resetProcedure,
                        generation
                    )
                    if (extraCatalogDetails.isNotEmpty()) {
                        Text(stringResource(R.string.additional_catalog_data), style = MaterialTheme.typography.titleSmall)
                        if (!VehicleCatalogOptions.isOilIntervalVerified(catalog.verificationStatus)) {
                            Text(stringResource(R.string.unverified_catalog_warning), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        extraCatalogDetails.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                if (!vehicle.vin.isNullOrBlank()) Text(stringResource(R.string.vin_format, vehicle.vin))
                if (!vehicle.licensePlate.isNullOrBlank()) Text(stringResource(R.string.license_plate_format, vehicle.licensePlate))
                vehicle.notes?.takeIf(String::isNotBlank)?.let { Text(stringResource(R.string.notes_format, it), style = MaterialTheme.typography.bodySmall) }
                HorizontalDivider()
                VehicleDocumentsSection(vehicle, receiptRepository, enabled = !archived && !pending)
                if (!archived) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onOpenOilChanges, enabled = !pending, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.oil_changes)) }
                        Button(onClick = onOpenFuel, enabled = !pending, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.fuel_tracking)) }
                    }
                } else {
                    Text(stringResource(R.string.archived_read_only), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
internal fun MaintenanceStatus.displayLabel(): String = when (this) {
    MaintenanceStatus.UNAVAILABLE -> stringResource(R.string.status_schedule_unavailable)
    MaintenanceStatus.UP_TO_DATE -> stringResource(R.string.status_up_to_date)
    MaintenanceStatus.DUE_SOON -> stringResource(R.string.status_due_soon)
    MaintenanceStatus.DUE -> stringResource(R.string.status_due)
    MaintenanceStatus.OVERDUE -> stringResource(R.string.status_overdue)
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun VehicleEditorDialog(
    vehicle: VehicleEntity?,
    busy: Boolean,
    errorMessage: String?,
    catalogEntries: List<VehicleSpecificationEntity>,
    specificationRepository: VehicleSpecificationRepository,
    onDismiss: () -> Unit,
    onSaveErrorDismiss: () -> Unit,
    onSave: (VehicleDraft, VehicleEntity?) -> Unit
) {
    val context = LocalContext.current
    val savingVehicleCd = stringResource(R.string.saving_vehicle_cd)
    val catalogLoadErrorFallback = stringResource(R.string.vehicle_catalog_load_error)
    val measurementSystem = remember { MeasurementSystemPreferences.get(context) }
    val initialDraft = vehicle?.toVehicleDraft() ?: VehicleDraft().copy(odometerUnit = measurementSystem.distanceUnit)
    var nickname by rememberSaveable(vehicle?.id) { mutableStateOf(initialDraft.nickname) }
    var vehicleType by rememberSaveable(vehicle?.id) { mutableStateOf(initialDraft.vehicleType) }
    var year by rememberSaveable(vehicle?.id) { mutableStateOf(initialDraft.modelYear) }
    var make by rememberSaveable(vehicle?.id) { mutableStateOf(initialDraft.make) }
    var model by rememberSaveable(vehicle?.id) { mutableStateOf(initialDraft.model) }
    var trim by rememberSaveable(vehicle?.id) { mutableStateOf(initialDraft.trim) }
    var engine by rememberSaveable(vehicle?.id) { mutableStateOf(initialDraft.engine) }
    var engineCode by rememberSaveable(vehicle?.id) { mutableStateOf(initialDraft.engineCode) }
    var fuelType by rememberSaveable(vehicle?.id) { mutableStateOf(initialDraft.fuelType) }
    var transmission by rememberSaveable(vehicle?.id) { mutableStateOf(initialDraft.transmission) }
    var vin by rememberSaveable(vehicle?.id) { mutableStateOf(initialDraft.vin) }
    var plate by rememberSaveable(vehicle?.id) { mutableStateOf(initialDraft.licensePlate) }
    var notes by rememberSaveable(vehicle?.id) { mutableStateOf(initialDraft.notes) }
    var odometerUnit by rememberSaveable(vehicle?.id) { mutableStateOf(initialDraft.odometerUnit) }
    var startingDailyUsage by rememberSaveable(vehicle?.id) { mutableStateOf(initialDraft.startingDailyUsage) }
    var selectedSpecificationId by rememberSaveable(vehicle?.id) { mutableStateOf(initialDraft.defaultSpecificationId) }
    var yearManual by rememberSaveable(vehicle?.id) { mutableStateOf(false) }
    var makeManual by rememberSaveable(vehicle?.id) { mutableStateOf(false) }
    var modelManual by rememberSaveable(vehicle?.id) { mutableStateOf(false) }
    var engineManual by rememberSaveable(vehicle?.id) { mutableStateOf(false) }
    var selectorStateInitialized by rememberSaveable(vehicle?.id) { mutableStateOf(false) }
    var catalogLoadError by rememberSaveable(vehicle?.id) { mutableStateOf<String?>(null) }
    var showCatalogManager by rememberSaveable(vehicle?.id) { mutableStateOf(false) }
    var discardConfirmation by rememberSaveable(vehicle?.id) { mutableStateOf(false) }
    var pendingAISelectionId by rememberSaveable(vehicle?.id) { mutableStateOf<String?>(null) }

    fun setOdometerUnit(unit: String) {
        if (unit != odometerUnit) {
            startingDailyUsage.toDoubleOrNull()?.let { current ->
                startingDailyUsage = convertDailyDistance(current, odometerUnit, unit)?.let(::formatDailyDistance).orEmpty()
            }
            odometerUnit = unit
        }
    }

    LaunchedEffect(vehicle?.id) {
        runCatching { VehicleCatalogSeeder(context.applicationContext, specificationRepository).seedIfMissing() }
            .onFailure { catalogLoadError = it.message ?: catalogLoadErrorFallback }
    }

    val yearOptions = remember(catalogEntries) { VehicleCatalogOptions.modelYears(catalogEntries) }
    val selectedYear = year.toIntOrNull()
    val manufacturerOptions = remember(catalogEntries, selectedYear) {
        selectedYear?.let { VehicleCatalogOptions.makesForYear(catalogEntries, it) }.orEmpty()
    }
    val modelOptions = remember(catalogEntries, selectedYear, make) {
        if (selectedYear != null && make.isNotBlank()) {
            VehicleCatalogOptions.modelsForYearAndMake(catalogEntries, selectedYear, make)
        } else emptyList()
    }
    val configurationOptions = remember(catalogEntries, selectedYear, make, model) {
        if (selectedYear != null && make.isNotBlank() && model.isNotBlank()) {
            VehicleCatalogOptions.configurationsForVehicle(catalogEntries, selectedYear, make, model)
        } else emptyList()
    }
    val selectedSpecification = catalogEntries.firstOrNull { it.id == selectedSpecificationId }

    LaunchedEffect(catalogEntries.isNotEmpty(), selectorStateInitialized) {
        if (catalogEntries.isNotEmpty() && !selectorStateInitialized) {
            val initialYear = year.toIntOrNull()
            yearManual = year.isNotBlank() && (initialYear == null || initialYear !in yearOptions)
            val initialManufacturers = initialYear?.let { VehicleCatalogOptions.makesForYear(catalogEntries, it) }.orEmpty()
            makeManual = make.isNotBlank() && initialManufacturers.none { it.equals(make, ignoreCase = true) }
            val initialModels = if (initialYear != null && make.isNotBlank()) {
                VehicleCatalogOptions.modelsForYearAndMake(catalogEntries, initialYear, make)
            } else emptyList()
            modelManual = model.isNotBlank() && initialModels.none { it.equals(model, ignoreCase = true) }
            val initialConfigurations = if (initialYear != null && make.isNotBlank() && model.isNotBlank()) {
                VehicleCatalogOptions.configurationsForVehicle(catalogEntries, initialYear, make, model)
            } else emptyList()
            val selectedCatalogRow = catalogEntries.firstOrNull { it.id == selectedSpecificationId }
            engineManual = engine.isNotBlank() && initialConfigurations.none { it.engine.equals(engine, ignoreCase = true) } &&
                selectedCatalogRow?.engine?.equals(engine, ignoreCase = true) != true
            selectorStateInitialized = true
        }
    }

    fun clearAfterYearChange() {
        make = ""
        model = ""
        engine = ""
        trim = ""
        makeManual = false
        modelManual = false
        engineManual = false
        selectedSpecificationId = null
    }

    fun clearAfterMakeChange() {
        model = ""
        engine = ""
        trim = ""
        modelManual = false
        engineManual = false
        selectedSpecificationId = null
    }

    fun clearAfterModelChange() {
        engine = ""
        trim = ""
        engineManual = false
        selectedSpecificationId = null
    }

    fun applyCatalogSelection(entry: VehicleSpecificationEntity?) {
        if (entry == null) {
            engineManual = true
            engine = ""
            trim = ""
            selectedSpecificationId = null
        } else {
            engineManual = false
            engine = entry.engine.orEmpty()
            selectedSpecificationId = entry.id
        }
    }

    val selectedYearRange = selectedSpecification?.let { VehicleCatalogOptions.yearRange(it) }
    val yearOutOfCatalogRange = selectedYearRange != null && selectedYear?.let { it !in selectedYearRange } == true
    val selectedCatalogConfigurationMatches = VehicleCatalogOptions.matchesSelectedConfiguration(
        selectedSpecification,
        configurationOptions,
        engine
    )
    val catalogSelectionMismatch = selectedSpecification != null && (
        !make.trim().equals(selectedSpecification.make, ignoreCase = true) ||
            !model.trim().equals(selectedSpecification.model, ignoreCase = true) ||
            !selectedCatalogConfigurationMatches ||
            (selectedSpecification.modelYear != null && selectedYear != selectedSpecification.modelYear)
        )
    val draft = VehicleDraft(nickname, year, make, model, trim, engine, engineCode, fuelType, transmission, vin, plate, notes, odometerUnit, selectedSpecificationId, startingDailyUsage, vehicleType)
    val requestDismiss = {
        if (!busy) {
            if (draft != initialDraft) discardConfirmation = true else onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = requestDismiss,
        title = { Text(if (vehicle == null) stringResource(R.string.add_vehicle) else stringResource(R.string.edit_vehicle)) },
        text = {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val availableHeight = if (maxHeight == Dp.Infinity) 520.dp else (maxHeight - 64.dp).coerceAtLeast(180.dp)
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = availableHeight).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    errorMessage?.let { error ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
                            TextButton(onClick = onSaveErrorDismiss, enabled = !busy) { Text(stringResource(R.string.dismiss_error)) }
                        }
                    }
                    Text(stringResource(R.string.vehicle_selection), style = MaterialTheme.typography.titleSmall)
                    if (vehicle == null) Text(
                        stringResource(R.string.catalog_ai_warning),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    CatalogDropdownField(
                        label = stringResource(R.string.year_label),
                        value = if (yearManual) "" else year,
                        options = yearOptions.map { it.toString() },
                        enabled = !busy,
                        onSelect = { value ->
                            if (value == OTHER_CATALOG_OPTION) {
                                yearManual = true
                                year = ""
                            } else {
                                yearManual = false
                                year = value
                            }
                            clearAfterYearChange()
                        }
                    )
                    if (yearManual) {
                        OutlinedTextField(
                            value = year,
                            onValueChange = { year = it.filter(Char::isDigit).take(4); clearAfterYearChange() },
                            label = { Text(stringResource(R.string.enter_model_year)) }, singleLine = true, enabled = !busy
                        )
                        TextButton(onClick = { yearManual = false; year = ""; clearAfterYearChange() }, enabled = !busy) { Text(stringResource(R.string.choose_listed_year)) }
                    }
                    catalogLoadError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                    CatalogDropdownField(
                        label = stringResource(R.string.manufacturer_label),
                        value = if (makeManual) "" else make,
                        options = manufacturerOptions,
                        enabled = !busy && selectedYear != null,
                        onSelect = { value ->
                            if (value == OTHER_CATALOG_OPTION) {
                                makeManual = true
                                make = ""
                            } else {
                                makeManual = false
                                make = value
                            }
                            clearAfterMakeChange()
                        }
                    )
                    if (makeManual) {
                        OutlinedTextField(value = make, onValueChange = { make = it; clearAfterMakeChange() }, label = { Text(stringResource(R.string.enter_manufacturer)) }, singleLine = true, enabled = !busy)
                        TextButton(onClick = { makeManual = false; make = ""; clearAfterMakeChange() }, enabled = !busy) { Text(stringResource(R.string.choose_listed_manufacturer)) }
                    }

                    CatalogDropdownField(
                        label = stringResource(R.string.model_label),
                        value = if (modelManual) "" else model,
                        options = modelOptions,
                        enabled = !busy && selectedYear != null && (make.isNotBlank() || makeManual),
                        onSelect = { value ->
                            if (value == OTHER_CATALOG_OPTION) {
                                modelManual = true
                                model = ""
                            } else {
                                modelManual = false
                                model = value
                            }
                            clearAfterModelChange()
                        }
                    )
                    if (modelManual) {
                        OutlinedTextField(value = model, onValueChange = { model = it; clearAfterModelChange() }, label = { Text(stringResource(R.string.enter_model)) }, singleLine = true, enabled = !busy)
                        TextButton(onClick = { modelManual = false; model = ""; clearAfterModelChange() }, enabled = !busy) { Text(stringResource(R.string.choose_listed_model)) }
                    }

                    Text(stringResource(R.string.engine_configuration_label), style = MaterialTheme.typography.labelLarge)
                    CatalogConfigurationDropdown(
                        value = if (engineManual) "" else selectedSpecification?.let {
                            configurationLabel(it.copy(engine = engine.ifBlank { it.engine.orEmpty() }), odometerUnit)
                        } ?: engine,
                        options = configurationOptions,
                        targetDistanceUnit = odometerUnit,
                        enabled = !busy && selectedYear != null && (make.isNotBlank() || makeManual) && (model.isNotBlank() || modelManual),
                        onSelect = { entry ->
                            if (entry != null && VehicleCatalogOptions.requiresSelectionWarning(entry.verificationStatus)) {
                                pendingAISelectionId = entry.id
                            } else {
                                applyCatalogSelection(entry)
                            }
                        }
                    )
                    if (engineManual) {
                        OutlinedTextField(value = engine, onValueChange = { engine = it; selectedSpecificationId = null }, label = { Text(stringResource(R.string.enter_engine_configuration)) }, singleLine = true, enabled = !busy)
                        TextButton(onClick = { engineManual = false; engine = "" }, enabled = !busy) { Text(stringResource(R.string.choose_catalog_configuration)) }
                    }
                    if (selectedSpecification != null) {
                        val intervalUnit = stringResource(if (odometerUnit == "MILE") R.string.unit_mi else R.string.unit_km)
                        val intervalDistance = selectedSpecification.distanceInterval?.let { distance ->
                            convertDistanceIntervalToUnit(distance, selectedSpecification.distanceUnit, odometerUnit)
                        }?.let { stringResource(R.string.two_part_format, it, intervalUnit) }
                        val intervalMonths = selectedSpecification.timeIntervalMonths
                            ?.let { stringResource(R.string.months_format, it) }
                        val interval = listOfNotNull(intervalDistance, intervalMonths)
                            .joinToString(" · ").ifBlank { rawIntervalText(selectedSpecification) ?: stringResource(R.string.not_specified) }
                        val intervalSourceLabel = when (selectedSpecification.verificationStatus) {
                            "USER_VERIFIED" -> stringResource(R.string.interval_source_user_verified)
                            "OFFICIAL_INTERVAL_RESEARCHED" -> stringResource(R.string.interval_source_official)
                            else -> stringResource(R.string.unverified)
                        }
                        Text(stringResource(R.string.imported_oil_interval_format, interval, intervalSourceLabel), style = MaterialTheme.typography.bodySmall)
                        if (!VehicleCatalogOptions.isOilIntervalVerified(selectedSpecification.verificationStatus)) {
                            Text(stringResource(R.string.interval_not_verified_warning), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    TextButton(onClick = { engineManual = true; engine = ""; trim = ""; selectedSpecificationId = null }, enabled = !busy) { Text(stringResource(R.string.enter_engine_configuration_manually)) }

                    TextButton(onClick = { showCatalogManager = true }, enabled = !busy) { Text(stringResource(R.string.modify_catalog_data)) }
                    HorizontalDivider()
                    Text(stringResource(R.string.vehicle_details_title), style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(value = nickname, onValueChange = { nickname = it }, label = { Text(stringResource(R.string.nickname_optional)) }, singleLine = true, enabled = !busy)
                    Text(stringResource(R.string.vehicle_icon), style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(VehicleType.entries, key = { it.storedValue }) { type ->
                            FilterChip(
                                selected = VehicleType.fromStoredValue(vehicleType) == type,
                                onClick = { vehicleType = type.storedValue },
                                enabled = !busy,
                                label = { Text(type.label) },
                                leadingIcon = { VehicleTypeIcon(type, iconSize = 24.dp, description = null) }
                            )
                        }
                    }
                    Text(stringResource(R.string.vehicle_icon_hint), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.odometer_units), style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = odometerUnit == "KM", onClick = { setOdometerUnit("KM") }, label = { Text(stringResource(R.string.kilometres)) }, enabled = !busy)
                        FilterChip(selected = odometerUnit == "MILE", onClick = { setOdometerUnit("MILE") }, label = { Text(stringResource(R.string.miles)) }, enabled = !busy)
                    }
                    OutlinedTextField(
                        value = startingDailyUsage,
                        onValueChange = { startingDailyUsage = normalizeDecimalInput(it) },
                        label = { Text(stringResource(R.string.starting_daily_use_label, stringResource(if (odometerUnit == "MILE") R.string.unit_mi else R.string.unit_km))) },
                        supportingText = { Text(stringResource(R.string.starting_daily_use_hint)) },
                        singleLine = true,
                        enabled = !busy
                    )
                    OutlinedTextField(value = engineCode, onValueChange = { engineCode = it }, label = { Text(stringResource(R.string.engine_code_optional)) }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = fuelType, onValueChange = { fuelType = it }, label = { Text(stringResource(R.string.fuel_type_optional)) }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = transmission, onValueChange = { transmission = it }, label = { Text(stringResource(R.string.transmission_optional)) }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = vin, onValueChange = { vin = it.uppercase().take(17) }, label = { Text(stringResource(R.string.vin_optional)) }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = plate, onValueChange = { plate = it.uppercase().take(16) }, label = { Text(stringResource(R.string.license_plate_optional)) }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(stringResource(R.string.notes_label)) }, minLines = 2, enabled = !busy)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(draft, vehicle) },
                enabled = !busy && year.isBlank().not() && make.isNotBlank() && model.isNotBlank() &&
                    !yearOutOfCatalogRange && !catalogSelectionMismatch
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.semantics { contentDescription = savingVehicleCd })
                    Text(stringResource(R.string.saving_ellipsis), modifier = Modifier.padding(start = 8.dp))
                } else Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = requestDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) } }
    )
    if (discardConfirmation) {
        AlertDialog(
            onDismissRequest = { discardConfirmation = false },
            title = { Text(stringResource(R.string.discard_changes_title)) },
            text = { Text(stringResource(R.string.discard_changes_message)) },
            confirmButton = { TextButton(onClick = { discardConfirmation = false; onDismiss() }) { Text(stringResource(R.string.discard)) } },
            dismissButton = { TextButton(onClick = { discardConfirmation = false }) { Text(stringResource(R.string.keep_editing)) } }
        )
    }
    if (showCatalogManager) {
        VehicleCatalogPickerDialog(
            entries = catalogEntries,
            isLoading = catalogEntries.isEmpty(),
            repository = specificationRepository,
            onDismiss = { showCatalogManager = false }
        )
    }
    pendingAISelectionId?.let { pendingId ->
        val entry = catalogEntries.firstOrNull { it.id == pendingId }
        if (entry != null) {
            AlertDialog(
                onDismissRequest = { pendingAISelectionId = null },
                title = { Text(stringResource(R.string.double_check_catalog_title)) },
                text = {
                    Text(
                        stringResource(R.string.double_check_catalog_message)
                    )
                },
                confirmButton = {
                    TextButton(onClick = { pendingAISelectionId = null; applyCatalogSelection(entry) }) {
                        Text(stringResource(R.string.select_unverified_data))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingAISelectionId = null }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
    }
}

private const val OTHER_CATALOG_OPTION = "__other_enter_manually__"

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CatalogDropdownField(
    label: String,
    value: String,
    options: List<String>,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    var expanded by rememberSaveable(label) { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            placeholder = { Text(stringResource(R.string.select_label_format, label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = enabled).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { expanded = false; onSelect(option) })
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.other_enter_manually)) }, onClick = { expanded = false; onSelect(OTHER_CATALOG_OPTION) })
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CatalogConfigurationDropdown(
    value: String,
    options: List<VehicleSpecificationEntity>,
    targetDistanceUnit: String,
    enabled: Boolean,
    onSelect: (VehicleSpecificationEntity?) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(stringResource(R.string.engine_configuration_as_supplied)) },
            placeholder = { Text(stringResource(R.string.select_engine_configuration)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = enabled).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(configurationLabel(entry, targetDistanceUnit)) },
                    onClick = { expanded = false; onSelect(entry) }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.other_enter_manually)) },
                onClick = { expanded = false; onSelect(null) }
            )
        }
    }
}

@Composable
private fun configurationLabel(entry: VehicleSpecificationEntity, targetDistanceUnit: String): String {
    val unitLabel = stringResource(if (targetDistanceUnit == "MILE") R.string.unit_mi else R.string.unit_km)
    val intervalDistance = entry.distanceInterval?.let { distance ->
        convertDistanceIntervalToUnit(distance, entry.distanceUnit, targetDistanceUnit)
    }?.let { stringResource(R.string.two_part_format, it, unitLabel) }
    val intervalMonths = entry.timeIntervalMonths?.let { stringResource(R.string.months_format, it) }
    val interval = listOfNotNull(intervalDistance, intervalMonths)
        .joinToString(" · ").ifBlank { VehicleCatalogOptions.rawNormalInterval(entry) }
    val verificationLabel = when (entry.verificationStatus) {
        "USER_VERIFIED" -> stringResource(R.string.user_verified)
        "OFFICIAL_INTERVAL_RESEARCHED" -> stringResource(R.string.oem_source_found)
        else -> stringResource(R.string.unverified)
    }
    return listOfNotNull(
        entry.engine?.takeIf(String::isNotBlank),
        VehicleCatalogOptions.marketRegion(entry),
        interval?.let { stringResource(R.string.oil_interval_format, it) },
        verificationLabel
    ).joinToString(" · ").ifBlank { stringResource(R.string.engine_configuration_not_specified) }
}

private fun normalizeDecimalInput(value: String): String {
    val filtered = value.filter { it.isDigit() || it == '.' }
    val firstDot = filtered.indexOf('.')
    return if (firstDot < 0) filtered else filtered.take(firstDot + 1) + filtered.substring(firstDot + 1).replace(".", "")
}

private fun rawIntervalText(entry: VehicleSpecificationEntity): String? =
    entry.serviceNotes.orEmpty().lineSequence()
        .firstOrNull { it.startsWith("Raw normal-service interval:") }
        ?.substringAfter(":")
        ?.trim()
        ?.takeIf(String::isNotBlank)

private fun catalogNoteValue(entry: VehicleSpecificationEntity, label: String): String? = entry.serviceNotes.orEmpty().lineSequence()
    .firstOrNull { it.startsWith("$label:") }
    ?.substringAfter(":")
    ?.trim()
    ?.takeIf(String::isNotBlank)

private fun VehicleEntity.toVehicleDraft() = VehicleDraft(
    nickname = nickname.orEmpty(),
    modelYear = modelYear?.toString().orEmpty(),
    make = make,
    model = model,
    trim = trim.orEmpty(),
    engine = engine.orEmpty(),
    engineCode = engineCode.orEmpty(),
    fuelType = fuelType.orEmpty(),
    transmission = transmission.orEmpty(),
    vin = vin.orEmpty(),
    licensePlate = licensePlate.orEmpty(),
    notes = notes.orEmpty(),
    odometerUnit = odometerUnit,
    defaultSpecificationId = defaultSpecificationId,
    startingDailyUsage = startingDailyUsageKmPerDay
        ?.let { convertDailyDistance(it, "KM", odometerUnit)?.let(::formatDailyDistance) }
        .orEmpty(),
    vehicleType = vehicleType
)
