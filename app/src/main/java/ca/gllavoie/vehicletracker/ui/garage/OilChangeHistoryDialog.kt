package ca.gllavoie.vehicletracker.ui.garage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.gllavoie.vehicletracker.data.local.OilChangeEntity
import ca.gllavoie.vehicletracker.data.local.VehicleSpecificationEntity
import ca.gllavoie.vehicletracker.data.repository.OdometerRepository
import ca.gllavoie.vehicletracker.data.repository.OilChangeRepository
import ca.gllavoie.vehicletracker.data.repository.ReceiptRepository
import ca.gllavoie.vehicletracker.data.repository.VehicleRepository
import ca.gllavoie.vehicletracker.data.repository.VehicleSpecificationRepository
import ca.gllavoie.vehicletracker.domain.calculation.DueLimit
import ca.gllavoie.vehicletracker.domain.calculation.MaintenanceStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.text.NumberFormat

@Composable
fun OilChangeRoute(
    vehicleId: String,
    vehicleRepository: VehicleRepository,
    oilChangeRepository: OilChangeRepository,
    odometerRepository: OdometerRepository,
    receiptRepository: ReceiptRepository,
    specificationRepository: VehicleSpecificationRepository,
    onDismiss: () -> Unit
) {
    val oilChangeViewModel: OilChangeViewModel = viewModel(
        key = "oil-change-$vehicleId",
        factory = OilChangeViewModel.Factory(vehicleId, vehicleRepository, oilChangeRepository, odometerRepository, specificationRepository)
    )
    val state by oilChangeViewModel.uiState.collectAsStateWithLifecycle()
    OilChangeHistoryDialog(
        state = state,
        receiptRepository = receiptRepository,
        onDismiss = onDismiss,
        onSave = oilChangeViewModel::save,
        onClearError = oilChangeViewModel::clearError
    )
}

@Composable
private fun OilChangeHistoryDialog(
    state: OilChangeUiState,
    receiptRepository: ReceiptRepository,
    onDismiss: () -> Unit,
    onSave: (OilChangeDraft, OilChangeEntity?) -> Unit,
    onClearError: () -> Unit
) {
    var editorRecordId by rememberSaveable { mutableStateOf<String?>(null) }
    var receiptServiceId by rememberSaveable { mutableStateOf<String?>(null) }
    var creatingRecord by rememberSaveable { mutableStateOf(false) }
    var saveCountAtEditorOpen by rememberSaveable { mutableIntStateOf(state.saveSuccessCount) }
    val editorRecord = state.records.firstOrNull { it.id == editorRecordId }
    LaunchedEffect(state.saveSuccessCount) {
        if ((creatingRecord || editorRecordId != null) && state.saveSuccessCount > saveCountAtEditorOpen) {
            creatingRecord = false
            editorRecordId = null
        }
    }
    val vehicle = state.vehicle
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(vehicle?.let { "Oil changes · ${it.make} ${it.model}" } ?: "Oil changes") },
        text = {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val availableHeight = if (maxHeight == Dp.Infinity) 500.dp else (maxHeight - 64.dp).coerceAtLeast(180.dp)
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = availableHeight).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.isLoading || vehicle == null) {
                        CircularProgressIndicator(Modifier.semantics { contentDescription = "Loading oil change history" })
                    } else {
                        ScheduleSummary(state, vehicle.odometerUnit)
                        Button(
                            onClick = {
                                editorRecordId = null
                                saveCountAtEditorOpen = state.saveSuccessCount
                                creatingRecord = true
                            },
                            enabled = !state.isSaving
                        ) { Text("Log oil change") }
                        state.error?.let { error ->
                            Column {
                                Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
                                TextButton(onClick = onClearError) { Text("Dismiss error") }
                            }
                        }
                        HorizontalDivider()
                        Text("Service history", style = MaterialTheme.typography.titleMedium)
                        if (state.records.isEmpty()) {
                            Text("No oil changes recorded. Specifications are not verified until you enter sourced recommendations.")
                        } else {
                            state.records.forEach { record ->
                                OilChangeRecordRow(
                                    record = record,
                                    onEdit = {
                                        editorRecordId = record.id
                                        saveCountAtEditorOpen = state.saveSuccessCount
                                    },
                                    onReceipts = { receiptServiceId = record.id }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
    if (creatingRecord || editorRecordId != null) {
        if (editorRecordId != null && editorRecord == null) {
            AlertDialog(
                onDismissRequest = { editorRecordId = null },
                title = { Text("Loading service record") },
                text = { CircularProgressIndicator(Modifier.semantics { contentDescription = "Loading service record" }) },
                confirmButton = { TextButton(onClick = { editorRecordId = null }) { Text("Cancel") } }
            )
        } else {
            OilChangeEditorDialog(
                record = editorRecord,
                vehicle = vehicle,
                busy = state.isSaving,
                error = state.error,
                onClearError = onClearError,
                onDismiss = { creatingRecord = false; editorRecordId = null },
                onSave = { draft -> onSave(draft, editorRecord) }
            )
        }
    }
    receiptServiceId?.let { serviceId ->
        vehicle?.let { targetVehicle ->
            ReceiptRoute(targetVehicle.id, serviceId, receiptRepository) { receiptServiceId = null }
        }
    }
}

@Composable
private fun ScheduleSummary(state: OilChangeUiState, distanceUnit: String) {
    val schedule = state.schedule
    val status = schedule?.status?.let {
        when (it) {
            MaintenanceStatus.UP_TO_DATE -> "Up to date"
            MaintenanceStatus.DUE_SOON -> "Due soon"
            MaintenanceStatus.DUE -> "Due"
            MaintenanceStatus.OVERDUE -> "Overdue"
            MaintenanceStatus.UNAVAILABLE -> "Schedule unavailable"
        }
    } ?: "No oil change recorded"
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Next oil change: $status", style = MaterialTheme.typography.titleMedium)
        state.specificationOverride?.let { spec ->
            Text("Vehicle-specific recommendations (user-entered, NOT verified)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
            spec.viscosityGrade?.let { Text("Oil viscosity: $it") }
            spec.manufacturerApproval?.let { Text("Required manufacturer approval: $it") }
            spec.capacityWithFilterMl?.let { Text("Capacity with filter: ${BigDecimal(it).divide(BigDecimal(1_000)).stripTrailingZeros().toPlainString()} L (not the amount actually added)") }
            spec.oilFilterPartNumber?.let { Text("Filter part: $it") }
            spec.sourceDescription?.let { Text("Source note: $it", style = MaterialTheme.typography.bodySmall) }
        }
        state.catalogSpecification?.let { spec ->
            Text("Catalog oil specifications", style = MaterialTheme.typography.labelLarge)
            spec.viscosityGrade?.let { Text("Oil viscosity: $it") }
            spec.manufacturerApproval?.let { Text("Required manufacturer approval: $it") }
            spec.capacityWithFilterMl?.let { Text("Capacity with filter: ${BigDecimal(it).divide(BigDecimal(1_000)).stripTrailingZeros().toPlainString()} L (not the amount actually added)") }
            spec.oilFilterPartNumber?.let { Text("Oil filter part: $it") }
        }
        if (schedule != null) {
            schedule.nextDueOdometer?.let { Text("Due mileage: ${NumberFormat.getIntegerInstance().format(it)} ${if (distanceUnit == "MILE") "mi" else "km"}") }
            schedule.estimatedDueDate?.let { Text("Estimated due date: $it") }
            schedule.distanceRemaining?.let { Text("Distance remaining: ${NumberFormat.getIntegerInstance().format(it)} ${if (distanceUnit == "MILE") "mi" else "km"}") }
            schedule.daysRemaining?.let { Text("Days to estimated deadline: $it") }
            Text("Deadline determined by ${if (schedule.determiningLimit == DueLimit.MILEAGE) "mileage" else if (schedule.determiningLimit == DueLimit.TIME) "elapsed time" else "unverified limits"}.")
            schedule.assumptions.distinct().forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            Text("Dates are estimates. Intervals recorded on a service are historical overrides, not verified manufacturer specifications.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun OilChangeRecordRow(record: OilChangeEntity, onEdit: () -> Unit, onReceipts: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(record.serviceDate, style = MaterialTheme.typography.titleSmall)
            Text("${NumberFormat.getIntegerInstance().format(record.odometerValue)} ${if (record.odometerUnit == "MILE") "mi" else "km"}")
        }
        val oilDescription = listOfNotNull(record.oilBrand, record.oilProduct, record.viscosityGrade, record.manufacturerApproval).joinToString(" · ")
        if (oilDescription.isNotBlank()) Text(oilDescription, style = MaterialTheme.typography.bodyMedium)
        record.quantityUsedMl?.let { Text("Added: ${BigDecimal(it).divide(BigDecimal(1000)).stripTrailingZeros().toPlainString()} L (actual amount)", style = MaterialTheme.typography.bodySmall) }
        val filter = listOfNotNull(record.filterBrand, record.filterPartNumber).joinToString(" · ")
        if (filter.isNotBlank()) Text("Filter: $filter", style = MaterialTheme.typography.bodySmall)
        record.totalCostMinor?.let { Text("Total: ${record.currencyCode} ${BigDecimal(it).divide(BigDecimal(100)).setScale(2)}", style = MaterialTheme.typography.bodySmall) }
        Text("${record.serviceType}${record.serviceProvider?.let { " · $it" } ?: ""}", style = MaterialTheme.typography.bodySmall)
        if (!record.notes.isNullOrBlank()) Text(record.notes, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onEdit, modifier = Modifier.semantics { contentDescription = "Edit oil change dated ${record.serviceDate}" }) { Text("Edit") }
            TextButton(onClick = onReceipts, modifier = Modifier.semantics { contentDescription = "Receipts for oil change dated ${record.serviceDate}" }) { Text("Receipts") }
        }
        HorizontalDivider()
    }
}

@Composable
private fun OilChangeEditorDialog(
    record: OilChangeEntity?,
    vehicle: ca.gllavoie.vehicletracker.data.local.VehicleEntity?,
    busy: Boolean,
    error: String?,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (OilChangeDraft) -> Unit
) {
    val initial = record?.toOilChangeDraft() ?: OilChangeDraft()
    var date by rememberSaveable(record?.id) { mutableStateOf(initial.serviceDate) }
    var odometer by rememberSaveable(record?.id) { mutableStateOf(initial.odometerValue) }
    var oilBrand by rememberSaveable(record?.id) { mutableStateOf(initial.oilBrand) }
    var oilProduct by rememberSaveable(record?.id) { mutableStateOf(initial.oilProduct) }
    var viscosity by rememberSaveable(record?.id) { mutableStateOf(initial.viscosityGrade) }
    var approval by rememberSaveable(record?.id) { mutableStateOf(initial.manufacturerApproval) }
    var quantity by rememberSaveable(record?.id) { mutableStateOf(initial.quantityLitres) }
    var filterBrand by rememberSaveable(record?.id) { mutableStateOf(initial.filterBrand) }
    var filterPart by rememberSaveable(record?.id) { mutableStateOf(initial.filterPartNumber) }
    var provider by rememberSaveable(record?.id) { mutableStateOf(initial.serviceProvider) }
    var serviceType by rememberSaveable(record?.id) { mutableStateOf(initial.serviceType) }
    var total by rememberSaveable(record?.id) { mutableStateOf(initial.totalCost) }
    var parts by rememberSaveable(record?.id) { mutableStateOf(initial.partsCost) }
    var labor by rememberSaveable(record?.id) { mutableStateOf(initial.laborCost) }
    var currency by rememberSaveable(record?.id) { mutableStateOf(initial.currencyCode) }
    var distanceInterval by rememberSaveable(record?.id) { mutableStateOf(initial.distanceInterval) }
    var timeInterval by rememberSaveable(record?.id) { mutableStateOf(initial.timeIntervalMonths) }
    var notes by rememberSaveable(record?.id) { mutableStateOf(initial.notes) }
    val valid = date.length == 10 && odometer.isNotBlank()
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (record == null) "Log oil change" else "Edit oil change") },
        text = {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val availableHeight = if (maxHeight == Dp.Infinity) 520.dp else (maxHeight - 64.dp).coerceAtLeast(180.dp)
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = availableHeight).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
                        TextButton(onClick = onClearError) { Text("Dismiss error") }
                    }
                    Text("Service details", style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(value = date, onValueChange = { date = it.take(10) }, label = { Text("Service date (YYYY-MM-DD) *") }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = odometer, onValueChange = { odometer = it.filter(Char::isDigit) }, label = { Text("Odometer (${if (vehicle?.odometerUnit == "MILE") "mi" else "km"}) *") }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = oilBrand, onValueChange = { oilBrand = it }, label = { Text("Oil brand") }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = oilProduct, onValueChange = { oilProduct = it }, label = { Text("Oil product") }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = viscosity, onValueChange = { viscosity = it }, label = { Text("Viscosity (as printed on container)") }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = approval, onValueChange = { approval = it }, label = { Text("Manufacturer approval/specification") }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = quantity, onValueChange = { quantity = it }, label = { Text("Oil actually added (litres)") }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = filterBrand, onValueChange = { filterBrand = it }, label = { Text("Filter brand") }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = filterPart, onValueChange = { filterPart = it }, label = { Text("Filter part number") }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = provider, onValueChange = { provider = it }, label = { Text("Garage / service provider") }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = serviceType, onValueChange = { serviceType = it }, label = { Text("Service type (DIY / garage / dealer)") }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = total, onValueChange = { total = it }, label = { Text("Total cost") }, singleLine = true, enabled = !busy)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = parts, onValueChange = { parts = it }, label = { Text("Parts cost") }, singleLine = true, enabled = !busy, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = labor, onValueChange = { labor = it }, label = { Text("Labor cost") }, singleLine = true, enabled = !busy, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = currency, onValueChange = { currency = it.uppercase().take(3) }, label = { Text("Currency (CAD)") }, singleLine = true, enabled = !busy)
                    Text("Optional interval override for this service; values are not treated as verified manufacturer data.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = distanceInterval, onValueChange = { distanceInterval = it.filter(Char::isDigit) }, label = { Text("Next interval (${if (vehicle?.odometerUnit == "MILE") "mi" else "km"})") }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = timeInterval, onValueChange = { timeInterval = it.filter(Char::isDigit) }, label = { Text("Next interval (months)") }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, minLines = 2, enabled = !busy)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(OilChangeDraft(date, odometer, oilBrand, oilProduct, viscosity, approval, quantity, filterBrand, filterPart, provider, serviceType, total, parts, labor, currency, distanceInterval, timeInterval, notes))
            }, enabled = valid && !busy) {
                if (busy) CircularProgressIndicator(Modifier.semantics { contentDescription = "Saving oil change" }) else Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } }
    )
}

private fun OilChangeEntity.toOilChangeDraft() = OilChangeDraft(
    serviceDate = serviceDate,
    odometerValue = odometerValue.toString(),
    oilBrand = oilBrand.orEmpty(),
    oilProduct = oilProduct.orEmpty(),
    viscosityGrade = viscosityGrade.orEmpty(),
    manufacturerApproval = manufacturerApproval.orEmpty(),
    quantityLitres = quantityUsedMl?.let { BigDecimal(it).divide(BigDecimal(1000)).stripTrailingZeros().toPlainString() }.orEmpty(),
    filterBrand = filterBrand.orEmpty(),
    filterPartNumber = filterPartNumber.orEmpty(),
    serviceProvider = serviceProvider.orEmpty(),
    serviceType = serviceType,
    totalCost = totalCostMinor?.let { BigDecimal(it).divide(BigDecimal(100)).toPlainString() }.orEmpty(),
    partsCost = partsCostMinor?.let { BigDecimal(it).divide(BigDecimal(100)).toPlainString() }.orEmpty(),
    laborCost = laborCostMinor?.let { BigDecimal(it).divide(BigDecimal(100)).toPlainString() }.orEmpty(),
    currencyCode = currencyCode,
    distanceInterval = distanceIntervalOverride?.toString().orEmpty(),
    timeIntervalMonths = timeIntervalMonthsOverride?.toString().orEmpty(),
    notes = notes.orEmpty()
)
