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
    val catalogEntries by specificationRepository.observeSpecificationsForDataset(VehicleCatalogSeeder.CATALOG_ID_PREFIX).collectAsStateWithLifecycle(emptyList())
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
                    Text(if (state.showArchived) "Archived vehicles" else "Active vehicles", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Show archived", style = MaterialTheme.typography.labelMedium)
                        Switch(
                            checked = state.showArchived,
                            onCheckedChange = onShowArchived,
                            modifier = Modifier.semantics { contentDescription = "Show archived vehicles" }
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
                        Text("Add vehicle")
                    }
                }
                if (state.visibleVehicles.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Sort vehicles", style = MaterialTheme.typography.bodyMedium)
                        Box {
                            TextButton(
                                onClick = { sortMenuExpanded = true },
                                modifier = Modifier.semantics { contentDescription = "Sort vehicles: ${sortOrder.label}" }
                            ) { Text(sortOrder.label) }
                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false }
                            ) {
                                GarageSortOrder.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(if (option == sortOrder) "✓ ${option.label}" else option.label) },
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
                        CircularProgressIndicator(Modifier.semantics { contentDescription = "Loading vehicles" })
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
                title = { Text("Loading vehicle") },
                text = { CircularProgressIndicator(Modifier.semantics { contentDescription = "Loading vehicle details" }) },
                confirmButton = { TextButton(onClick = { editorTargetId = null }) { Text("Cancel") } }
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
            title = { Text("Archive vehicle?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${target.modelYear?.toString()?.plus(" ") ?: ""}${target.make} ${target.model} will be hidden from active vehicles. Its maintenance and service history will be retained and can be restored later.")
                    state.archiveError?.let { error ->
                        Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onArchive(target) }, enabled = !actionPending) {
                    if (actionPending) CircularProgressIndicator(Modifier.semantics { contentDescription = "Archiving vehicle" }) else Text("Archive")
                }
            },
            dismissButton = {
                TextButton(onClick = { archiveTargetId = null; onClearArchiveError() }, enabled = !actionPending) { Text("Cancel") }
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
            title = { Text("Permanently delete vehicle?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${target.modelYear?.toString()?.plus(" ") ?: ""}${target.make} ${target.model} and all its oil changes, fuel records, odometer history, vehicle documents, vehicle-specific recommendations, and receipt files will be permanently removed. This cannot be undone. Shared catalog entries will remain.")
                    state.deleteError?.let { error ->
                        Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onDelete(target) }, enabled = !actionPending) {
                    if (actionPending) CircularProgressIndicator(Modifier.semantics { contentDescription = "Deleting vehicle and associated records" })
                    else Text("Delete permanently", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetId = null; onClearDeleteError() }, enabled = !actionPending) { Text("Cancel") }
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
        Text(if (archived) "No archived vehicles" else "Your garage is empty", style = MaterialTheme.typography.titleLarge)
        Text(
            if (archived) "Archived vehicles will appear here and can be restored." else "Add a vehicle to start keeping its maintenance and fuel records.",
            style = MaterialTheme.typography.bodyMedium
        )
        if (!archived) Button(onClick = onAdd) { Text("Add your first vehicle") }
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
    val odometerReadings by odometerRepository.observeReadings(vehicle.id).collectAsStateWithLifecycle(emptyList())
    val oilChanges by oilChangeRepository.observeRecords(vehicle.id).collectAsStateWithLifecycle(emptyList())
    val specification by specificationRepository.observeOverride(vehicle.id).collectAsStateWithLifecycle(null)
    val catalogSpecification by specificationRepository.observeCatalogEntry(vehicle.defaultSpecificationId.orEmpty()).collectAsStateWithLifecycle(null)
    val latestReading = odometerReadings.firstOrNull()
    val latestService = oilChanges.firstOrNull()
    val schedule = latestService?.let { calculateVehicleOilSchedule(vehicle, it, odometerReadings, specification, catalogSpecification) }
    val odometerUnitLabel = if (vehicle.odometerUnit == "MILE") "mi" else "km"
    val intervalUnitLabel = if (vehicle.odometerUnit == "MILE") "mi" else "km"
    val observedDailyUsage = estimateDailyDistancePerDay(odometerReadings, vehicle.odometerUnit)
    val startingDailyUsage = vehicle.startingDailyUsageKmPerDay?.let { convertDailyDistance(it, "KM", vehicle.odometerUnit) }
    val dailyUsageLabel = when {
        observedDailyUsage != null -> "Daily use (updated): ${formatDailyDistance(observedDailyUsage)} $odometerUnitLabel/day"
        startingDailyUsage != null -> "Starting daily use: ${formatDailyDistance(startingDailyUsage)} $odometerUnitLabel/day"
        else -> "Daily use estimate: set a starting value or log dated mileage readings"
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
    val intervalLabel = buildList {
        displayedDistance?.let { add("${NumberFormat.getIntegerInstance().format(it)} $intervalUnitLabel") }
        recommendedMonths?.let { add("$it months") }
    }.joinToString(" · ").ifBlank { rawCatalogInterval ?: "Not set" }
    val relyingOnUnverifiedCatalogInterval = serviceDistance == null && manualDistance == null &&
        serviceMonths == null && manualMonths == null && !rawCatalogInterval.isNullOrBlank() &&
        !VehicleCatalogOptions.isOilIntervalVerified(catalogSpecification?.verificationStatus)
    val intervalHeading = when {
        serviceDistance != null || serviceMonths != null -> "Latest service-record interval"
        manualDistance != null || manualMonths != null -> "Manual vehicle interval"
        catalogSpecification?.verificationStatus == "OFFICIAL_INTERVAL_RESEARCHED" -> "${intervalMode.displayName} catalog interval (source found; confirm)"
        relyingOnUnverifiedCatalogInterval -> "${intervalMode.displayName} catalog interval (unverified)"
        catalogSpecification?.let { VehicleCatalogOptions.isOilIntervalVerified(it.verificationStatus) } == true -> "${intervalMode.displayName} user-verified interval"
        else -> "Oil interval"
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
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VehicleTypeIcon(VehicleType.fromStoredValue(vehicle.vehicleType))
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(vehicle.nickname?.takeIf(String::isNotBlank) ?: title, style = MaterialTheme.typography.titleLarge)
                    if (!vehicle.nickname.isNullOrBlank()) Text(title, style = MaterialTheme.typography.bodyMedium)
                }
                Box {
                    IconButton(
                        onClick = { actionsExpanded = true },
                        enabled = !pending,
                        modifier = Modifier.semantics { contentDescription = "Vehicle actions" }
                    ) { Text("⋮", style = MaterialTheme.typography.headlineMedium) }
                    DropdownMenu(expanded = actionsExpanded, onDismissRequest = { actionsExpanded = false }) {
                        if (archived) {
                            DropdownMenuItem(
                                text = { Text("Restore") },
                                onClick = { actionsExpanded = false; onRestore() },
                                enabled = !pending
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Edit vehicle and daily use") },
                                onClick = { actionsExpanded = false; onEdit() },
                                enabled = !pending
                            )
                            DropdownMenuItem(
                                text = { Text("Archive") },
                                onClick = { actionsExpanded = false; onArchive() },
                                enabled = !pending
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Delete permanently", color = MaterialTheme.colorScheme.error) },
                            onClick = { actionsExpanded = false; onDelete() },
                            enabled = !pending
                        )
                    }
                }
            }
            val odometerLabel = latestReading?.let { reading ->
                val readingDate = Instant.ofEpochMilli(reading.recordedAtUtc).atZone(ZoneId.systemDefault()).toLocalDate()
                "${NumberFormat.getIntegerInstance().format(reading.value)} ${if (reading.unit == "MILE") "mi" else "km"} · $readingDate"
            } ?: "Not recorded"
            if (!detailMode) {
                Text(
                    "Oil change · ${schedule?.status?.displayLabel() ?: "No service logged"}",
                    color = statusColor,
                    style = MaterialTheme.typography.titleMedium
                )
                val remaining = schedule?.distanceRemaining
                val remainingText = when {
                    latestService == null -> "Log an oil change"
                    remaining == null && schedule?.status == MaintenanceStatus.UNAVAILABLE -> "Set an oil interval"
                    remaining == null && schedule?.nextDueOdometer == null && schedule?.estimatedDueDate != null -> "Time-based interval"
                    remaining == null -> "Record current mileage"
                    remaining < 0L -> "${NumberFormat.getIntegerInstance().format(kotlin.math.abs(remaining))} $odometerUnitLabel overdue"
                    else -> "${NumberFormat.getIntegerInstance().format(remaining)} $odometerUnitLabel remaining"
                }
                Text(remainingText, style = MaterialTheme.typography.headlineMedium, color = statusColor)
                schedule?.nextDueOdometer?.let { due ->
                    Text("Next oil change at ${NumberFormat.getIntegerInstance().format(due)} $odometerUnitLabel", style = MaterialTheme.typography.bodySmall)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("ESTIMATED DATE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(schedule?.estimatedDueDate?.toString() ?: "Not available", style = MaterialTheme.typography.bodySmall)
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("OIL INTERVAL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${intervalMode.displayName}: $intervalLabel", style = MaterialTheme.typography.bodySmall)
                        if (intervalHeading != "Oil interval") {
                            Text(intervalHeading, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text("Tap to view vehicle details and service history", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(
                    "Oil change · ${schedule?.status?.displayLabel() ?: "No service logged"}",
                    color = statusColor,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text("Oil change interval", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = intervalMode == OilIntervalMode.NORMAL,
                        onClick = { if (!archived) onChangeIntervalMode(OilIntervalMode.NORMAL.storedValue) },
                        enabled = !archived && !pending,
                        label = { Text("Normal") }
                    )
                    FilterChip(
                        selected = intervalMode == OilIntervalMode.SEVERE,
                        onClick = { if (!archived) onChangeIntervalMode(OilIntervalMode.SEVERE.storedValue) },
                        enabled = !archived && !pending,
                        label = { Text("Severe / heavy use") }
                    )
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("DISTANCE REMAINING", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val remaining = schedule?.distanceRemaining
                        val remainingText = when {
                            latestService == null -> "Log an oil change"
                            remaining == null && schedule?.status == MaintenanceStatus.UNAVAILABLE -> "Set an oil interval"
                            remaining == null && schedule?.nextDueOdometer == null && schedule?.estimatedDueDate != null -> "Time-based interval"
                            remaining == null -> "Record current mileage"
                            remaining < 0L -> "${NumberFormat.getIntegerInstance().format(kotlin.math.abs(remaining))} $odometerUnitLabel overdue"
                            else -> "${NumberFormat.getIntegerInstance().format(remaining)} $odometerUnitLabel remaining"
                        }
                        Text(remainingText, style = MaterialTheme.typography.displaySmall, color = statusColor)
                        schedule?.nextDueOdometer?.let { due ->
                            Text("Next oil change at ${NumberFormat.getIntegerInstance().format(due)} $odometerUnitLabel", style = MaterialTheme.typography.bodyMedium)
                        }
                        HorizontalDivider()
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("ESTIMATED DATE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(schedule?.estimatedDueDate?.toString() ?: "Not available", style = MaterialTheme.typography.bodySmall)
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("OIL INTERVAL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(intervalLabel, style = MaterialTheme.typography.bodySmall)
                                if (intervalHeading != "Oil interval") {
                                    Text(intervalHeading, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                Text("Last odometer: $odometerLabel")
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(dailyUsageLabel, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    if (!archived) TextButton(onClick = onEdit, enabled = !pending) { Text("Edit daily use") }
                }
                Text("Last service: ${latestService?.let { "${it.serviceDate} · ${NumberFormat.getIntegerInstance().format(it.odometerValue)} ${if (it.odometerUnit == "MILE") "mi" else "km"}" } ?: "Not recorded"}")
                when {
                    latestService == null -> Text("Log an oil change to start the service schedule.", style = MaterialTheme.typography.bodySmall)
                    serviceDistance != null || serviceMonths != null -> Text("The interval saved on the latest oil-change record takes priority. Edit that service record to change the active schedule interval.", style = MaterialTheme.typography.bodySmall)
                    manualDistance != null || manualMonths != null -> Text("Your manually saved vehicle interval takes priority over the ${intervalMode.displayName.lowercase()} catalog interval.", style = MaterialTheme.typography.bodySmall)
                    intervalMode == OilIntervalMode.SEVERE && catalogSpecification?.let { it.severeDistanceInterval == null && it.severeTimeIntervalMonths == null } == true -> Text("No severe-service interval is present for this catalog entry. The normal interval is not substituted; check the owner's manual or select Normal.", style = MaterialTheme.typography.bodySmall)
                    schedule?.status == MaintenanceStatus.UNAVAILABLE -> Text("Add a manual interval or open Edit → Modify catalog data, check the cited guide, and mark the selected interval verified before it is used.", style = MaterialTheme.typography.bodySmall)
                    schedule?.nextDueOdometer != null && schedule.estimatedDueDate == null -> Text("For a date estimate, enter starting daily use or record two increasing odometer readings on different dates.", style = MaterialTheme.typography.bodySmall)
                }
                if (!archived) {
                    OutlinedButton(onClick = onOpenOdometer, enabled = !pending, modifier = Modifier.fillMaxWidth()) {
                        Text("Update current mileage")
                    }
                    OutlinedButton(onClick = onOpenSpecifications, enabled = !pending, modifier = Modifier.fillMaxWidth()) {
                        Text("Edit oil interval and specs")
                    }
                }
                HorizontalDivider()
                val viscosity = catalogSpecification?.viscosityGrade ?: specification?.viscosityGrade ?: latestService?.viscosityGrade
                val approval = catalogSpecification?.manufacturerApproval ?: specification?.manufacturerApproval ?: latestService?.manufacturerApproval
                val filter = catalogSpecification?.oilFilterPartNumber ?: specification?.oilFilterPartNumber ?: latestService?.filterPartNumber
                Text("Vehicle information", style = MaterialTheme.typography.titleMedium)
                Text("Fuel type: ${vehicle.fuelType?.takeIf(String::isNotBlank) ?: "Not specified"}")
                Text("Engine: ${listOfNotNull(vehicle.engine, vehicle.engineCode?.let { "Code $it" }).joinToString(" · ").ifBlank { "Not specified" }}")
                Text("Transmission: ${vehicle.transmission?.takeIf(String::isNotBlank) ?: "Not specified"}")
                Text("Recommended oil type: ${viscosity ?: "Not specified"}")
                Text("Oil approval: ${approval ?: "Not specified"}")
                Text("Oil filter: ${filter ?: "Not specified"}")
                val capacityMl = catalogSpecification?.capacityWithFilterMl ?: specification?.capacityWithFilterMl
                Text("Oil capacity with filter: ${capacityMl?.let { "$it mL" } ?: "Not specified"}")
                catalogSpecification?.let { catalog ->
                    val extraCatalogDetails = listOfNotNull(
                        catalogNoteValue(catalog, "Raw oil recommendation")?.let { "CSV oil recommendation: $it" },
                        catalogNoteValue(catalog, "Raw oil filter type/form factor")?.let { "CSV filter description: $it" },
                        catalogNoteValue(catalog, "Uses oil-life monitor")?.let { "Oil-life monitor: $it" },
                        catalogNoteValue(catalog, "Oil reset procedure")?.let { "Reset procedure: $it" },
                        catalogNoteValue(catalog, "Generation code")?.let { "Generation: $it" }
                    )
                    if (extraCatalogDetails.isNotEmpty()) {
                        Text("Additional catalog data", style = MaterialTheme.typography.titleSmall)
                        if (!VehicleCatalogOptions.isOilIntervalVerified(catalog.verificationStatus)) {
                            Text("Unverified catalog suggestions; confirm them against the owner's manual.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        extraCatalogDetails.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                if (!vehicle.vin.isNullOrBlank()) Text("VIN: ${vehicle.vin}")
                if (!vehicle.licensePlate.isNullOrBlank()) Text("License plate: ${vehicle.licensePlate}")
                vehicle.notes?.takeIf(String::isNotBlank)?.let { Text("Notes: $it", style = MaterialTheme.typography.bodySmall) }
                HorizontalDivider()
                VehicleDocumentsSection(vehicle, receiptRepository, enabled = !archived && !pending)
                if (!archived) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onOpenOilChanges, enabled = !pending, modifier = Modifier.weight(1f)) { Text("Oil changes") }
                        Button(onClick = onOpenFuel, enabled = !pending, modifier = Modifier.weight(1f)) { Text("Fuel tracking") }
                    }
                } else {
                    Text("Archived vehicles are read-only until restored.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun MaintenanceStatus.displayLabel(): String = when (this) {
    MaintenanceStatus.UNAVAILABLE -> "Schedule unavailable"
    MaintenanceStatus.UP_TO_DATE -> "Up to date"
    MaintenanceStatus.DUE_SOON -> "Due soon"
    MaintenanceStatus.DUE -> "Due"
    MaintenanceStatus.OVERDUE -> "Overdue"
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
            .onFailure { catalogLoadError = it.message ?: "Vehicle catalog could not be loaded." }
    }

    val yearOptions = VehicleCatalogOptions.modelYears(catalogEntries)
    val selectedYear = year.toIntOrNull()
    val manufacturerOptions = selectedYear?.let { VehicleCatalogOptions.makesForYear(catalogEntries, it) }.orEmpty()
    val modelOptions = if (selectedYear != null && make.isNotBlank()) {
        VehicleCatalogOptions.modelsForYearAndMake(catalogEntries, selectedYear, make)
    } else emptyList()
    val configurationOptions = if (selectedYear != null && make.isNotBlank() && model.isNotBlank()) {
        VehicleCatalogOptions.configurationsForVehicle(catalogEntries, selectedYear, make, model)
    } else emptyList()
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
        title = { Text(if (vehicle == null) "Add vehicle" else "Edit vehicle") },
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
                            TextButton(onClick = onSaveErrorDismiss, enabled = !busy) { Text("Dismiss error") }
                        }
                    }
                    Text("Vehicle selection", style = MaterialTheme.typography.titleSmall)
                    if (vehicle == null) Text(
                        "The production catalog contains 15,886 AI-generated, unverified entries and 66 source-researched interval suggestions. Check the exact year, market, engine, oil type, and filter against the owner's manual. Unverified intervals do not drive the reminder schedule until you confirm them.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    CatalogDropdownField(
                        label = "Year",
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
                            label = { Text("Enter model year") }, singleLine = true, enabled = !busy
                        )
                        TextButton(onClick = { yearManual = false; year = ""; clearAfterYearChange() }, enabled = !busy) { Text("Choose a listed year") }
                    }
                    catalogLoadError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                    CatalogDropdownField(
                        label = "Manufacturer",
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
                        OutlinedTextField(value = make, onValueChange = { make = it; clearAfterMakeChange() }, label = { Text("Enter manufacturer") }, singleLine = true, enabled = !busy)
                        TextButton(onClick = { makeManual = false; make = ""; clearAfterMakeChange() }, enabled = !busy) { Text("Choose a listed manufacturer") }
                    }

                    CatalogDropdownField(
                        label = "Model",
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
                        OutlinedTextField(value = model, onValueChange = { model = it; clearAfterModelChange() }, label = { Text("Enter model") }, singleLine = true, enabled = !busy)
                        TextButton(onClick = { modelManual = false; model = ""; clearAfterModelChange() }, enabled = !busy) { Text("Choose a listed model") }
                    }

                    Text("Engine / configuration", style = MaterialTheme.typography.labelLarge)
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
                        OutlinedTextField(value = engine, onValueChange = { engine = it; selectedSpecificationId = null }, label = { Text("Enter engine / configuration") }, singleLine = true, enabled = !busy)
                        TextButton(onClick = { engineManual = false; engine = "" }, enabled = !busy) { Text("Choose a catalog configuration") }
                    }
                    if (selectedSpecification != null) {
                        val intervalUnit = if (odometerUnit == "MILE") "mi" else "km"
                        val interval = listOfNotNull(
                            selectedSpecification.distanceInterval?.let { distance ->
                                convertDistanceIntervalToUnit(distance, selectedSpecification.distanceUnit, odometerUnit)?.let { "$it $intervalUnit" }
                            },
                            selectedSpecification.timeIntervalMonths?.let { "$it months" }
                        ).joinToString(" · ").ifBlank { rawIntervalText(selectedSpecification) ?: "Not specified" }
                        val intervalSourceLabel = when (selectedSpecification.verificationStatus) {
                            "USER_VERIFIED" -> "user-marked verified"
                            "OFFICIAL_INTERVAL_RESEARCHED" -> "official interval source found; confirm to use"
                            else -> "unverified"
                        }
                        Text("Imported oil interval: $interval · $intervalSourceLabel", style = MaterialTheme.typography.bodySmall)
                        if (!VehicleCatalogOptions.isOilIntervalVerified(selectedSpecification.verificationStatus)) {
                            Text("The interval is linked to this vehicle but will not calculate reminders until verified.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    TextButton(onClick = { engineManual = true; engine = ""; trim = ""; selectedSpecificationId = null }, enabled = !busy) { Text("Enter engine / configuration manually") }

                    TextButton(onClick = { showCatalogManager = true }, enabled = !busy) { Text("Modify catalog data") }
                    HorizontalDivider()
                    Text("Vehicle details", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(value = nickname, onValueChange = { nickname = it }, label = { Text("Nickname (optional)") }, singleLine = true, enabled = !busy)
                    Text("Vehicle icon", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(VehicleType.entries, key = { it.storedValue }) { type ->
                            FilterChip(
                                selected = VehicleType.fromStoredValue(vehicleType) == type,
                                onClick = { vehicleType = type.storedValue },
                                enabled = !busy,
                                label = { Text(type.label) },
                                leadingIcon = { VehicleTypeIcon(type, iconSize = 24.dp) }
                            )
                        }
                    }
                    Text("Choose the body style icon shown on this vehicle's garage card.", style = MaterialTheme.typography.bodySmall)
                    Text("Odometer units", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = odometerUnit == "KM", onClick = { setOdometerUnit("KM") }, label = { Text("Kilometres (km)") }, enabled = !busy)
                        FilterChip(selected = odometerUnit == "MILE", onClick = { setOdometerUnit("MILE") }, label = { Text("Miles (mi)") }, enabled = !busy)
                    }
                    OutlinedTextField(
                        value = startingDailyUsage,
                        onValueChange = { startingDailyUsage = normalizeDecimalInput(it) },
                        label = { Text("Starting daily use (${if (odometerUnit == "MILE") "mi" else "km"}/day, optional)") },
                        supportingText = { Text("Used until dated odometer readings provide an actual average; that estimate updates as you log mileage.") },
                        singleLine = true,
                        enabled = !busy
                    )
                    OutlinedTextField(value = engineCode, onValueChange = { engineCode = it }, label = { Text("Engine code (optional)") }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = fuelType, onValueChange = { fuelType = it }, label = { Text("Fuel type (optional)") }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = transmission, onValueChange = { transmission = it }, label = { Text("Transmission (optional)") }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = vin, onValueChange = { vin = it.uppercase().take(17) }, label = { Text("VIN (optional)") }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = plate, onValueChange = { plate = it.uppercase().take(16) }, label = { Text("License plate (optional)") }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, minLines = 2, enabled = !busy)
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
                    CircularProgressIndicator(Modifier.semantics { contentDescription = "Saving vehicle" })
                    Text("Saving…", modifier = Modifier.padding(start = 8.dp))
                } else Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = requestDismiss, enabled = !busy) { Text("Cancel") } }
    )
    if (discardConfirmation) {
        AlertDialog(
            onDismissRequest = { discardConfirmation = false },
            title = { Text("Discard changes?") },
            text = { Text("Your unsaved vehicle changes will be lost.") },
            confirmButton = { TextButton(onClick = { discardConfirmation = false; onDismiss() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { discardConfirmation = false }) { Text("Keep editing") } }
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
                title = { Text("Double-check this catalog data") },
                text = {
                    Text(
                        "This vehicle entry is AI-generated and unverified. Before relying on its oil recommendation or intervals, confirm the exact year, market, engine, and maintenance schedule in the owner's manual. It will not drive the reminder schedule until you verify the interval in Modify catalog data."
                    )
                },
                confirmButton = {
                    TextButton(onClick = { pendingAISelectionId = null; applyCatalogSelection(entry) }) {
                        Text("Select unverified data")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingAISelectionId = null }) { Text("Cancel") }
                }
            )
        }
    }
}

private const val OTHER_CATALOG_OPTION = "Other — enter manually"

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
            placeholder = { Text("Select $label") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = enabled).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { expanded = false; onSelect(option) })
            }
            DropdownMenuItem(text = { Text(OTHER_CATALOG_OPTION) }, onClick = { expanded = false; onSelect(OTHER_CATALOG_OPTION) })
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
            label = { Text("Engine / configuration (as supplied)") },
            placeholder = { Text("Select an engine configuration") },
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
                text = { Text(OTHER_CATALOG_OPTION) },
                onClick = { expanded = false; onSelect(null) }
            )
        }
    }
}

private fun configurationLabel(entry: VehicleSpecificationEntity, targetDistanceUnit: String): String {
    val unitLabel = if (targetDistanceUnit == "MILE") "mi" else "km"
    val interval = listOfNotNull(
        entry.distanceInterval?.let { distance ->
            convertDistanceIntervalToUnit(distance, entry.distanceUnit, targetDistanceUnit)?.let { "$it $unitLabel" }
        },
        entry.timeIntervalMonths?.let { "$it months" }
    ).joinToString(" · ").ifBlank { VehicleCatalogOptions.rawNormalInterval(entry) }
    return listOfNotNull(
        entry.engine?.takeIf(String::isNotBlank),
        VehicleCatalogOptions.marketRegion(entry),
        interval?.let { "Oil interval: $it" },
        when (entry.verificationStatus) {
            "USER_VERIFIED" -> "User verified"
            "OFFICIAL_INTERVAL_RESEARCHED" -> "OEM source found · confirm to use"
            else -> "Unverified"
        }
    ).joinToString(" · ").ifBlank { "Engine/configuration not specified" }
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
