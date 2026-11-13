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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.gllavoie.vehicletracker.R
import ca.gllavoie.vehicletracker.data.local.OilChangeEntity
import ca.gllavoie.vehicletracker.data.local.VehicleSpecificationEntity
import ca.gllavoie.vehicletracker.data.repository.OdometerRepository
import ca.gllavoie.vehicletracker.data.repository.OilChangeRepository
import ca.gllavoie.vehicletracker.data.repository.ReceiptRepository
import ca.gllavoie.vehicletracker.data.repository.VehicleRepository
import ca.gllavoie.vehicletracker.data.repository.VehicleSpecificationRepository
import ca.gllavoie.vehicletracker.domain.calculation.DueLimit
import ca.gllavoie.vehicletracker.ui.common.DateTextField
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
    val loadingOilChangeHistoryCd = stringResource(R.string.loading_oil_change_history)
    val loadingServiceRecordCd = stringResource(R.string.loading_service_record)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                vehicle?.let { stringResource(R.string.oil_changes_vehicle_format, it.make, it.model) }
                    ?: stringResource(R.string.oil_changes)
            )
        },
        text = {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val availableHeight = if (maxHeight == Dp.Infinity) 500.dp else (maxHeight - 64.dp).coerceAtLeast(180.dp)
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = availableHeight).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.isLoading || vehicle == null) {
                        CircularProgressIndicator(Modifier.semantics { contentDescription = loadingOilChangeHistoryCd })
                    } else {
                        ScheduleSummary(state, vehicle.odometerUnit)
                        Button(
                            onClick = {
                                editorRecordId = null
                                saveCountAtEditorOpen = state.saveSuccessCount
                                creatingRecord = true
                            },
                            enabled = !state.isSaving
                        ) { Text(stringResource(R.string.log_oil_change)) }
                        state.error?.let { error ->
                            Column {
                                Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
                                TextButton(onClick = onClearError) { Text(stringResource(R.string.dismiss_error)) }
                            }
                        }
                        HorizontalDivider()
                        Text(stringResource(R.string.service_history), style = MaterialTheme.typography.titleMedium)
                        if (state.records.isEmpty()) {
                            Text(stringResource(R.string.no_oil_changes_recorded))
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
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } }
    )
    if (creatingRecord || editorRecordId != null) {
        if (editorRecordId != null && editorRecord == null) {
            AlertDialog(
                onDismissRequest = { editorRecordId = null },
                title = { Text(stringResource(R.string.loading_service_record)) },
                text = { CircularProgressIndicator(Modifier.semantics { contentDescription = loadingServiceRecordCd }) },
                confirmButton = { TextButton(onClick = { editorRecordId = null }) { Text(stringResource(R.string.cancel)) } }
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
    val status = schedule?.status?.displayLabel() ?: stringResource(R.string.no_oil_change_recorded)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.next_oil_change_format, status), style = MaterialTheme.typography.titleMedium)
        state.specificationOverride?.let { spec ->
            Text(stringResource(R.string.vehicle_specific_recommendations), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
            spec.viscosityGrade?.let { Text(stringResource(R.string.oil_viscosity_format, it)) }
            spec.manufacturerApproval?.let { Text(stringResource(R.string.required_manufacturer_approval_format, it)) }
            spec.capacityWithFilterMl?.let {
                Text(stringResource(R.string.capacity_with_filter_actual_note, BigDecimal(it).divide(BigDecimal(1_000)).stripTrailingZeros().toPlainString()))
            }
            spec.oilFilterPartNumber?.let { Text(stringResource(R.string.filter_part_format, it)) }
            spec.sourceDescription?.let { Text(stringResource(R.string.source_note_format, it), style = MaterialTheme.typography.bodySmall) }
        }
        state.catalogSpecification?.let { spec ->
            Text(stringResource(R.string.catalog_oil_specifications), style = MaterialTheme.typography.labelLarge)
            spec.viscosityGrade?.let { Text(stringResource(R.string.oil_viscosity_format, it)) }
            spec.manufacturerApproval?.let { Text(stringResource(R.string.required_manufacturer_approval_format, it)) }
            spec.capacityWithFilterMl?.let {
                Text(stringResource(R.string.capacity_with_filter_actual_note, BigDecimal(it).divide(BigDecimal(1_000)).stripTrailingZeros().toPlainString()))
            }
            spec.oilFilterPartNumber?.let { Text(stringResource(R.string.oil_filter_part_format, it)) }
        }
        if (schedule != null) {
            schedule.nextDueOdometer?.let {
                Text(stringResource(R.string.due_mileage_format, NumberFormat.getIntegerInstance().format(it), stringResource(if (distanceUnit == "MILE") R.string.unit_mi else R.string.unit_km)))
            }
            schedule.estimatedDueDate?.let { Text(stringResource(R.string.estimated_due_date_format, it.toString())) }
            schedule.distanceRemaining?.let {
                Text(stringResource(R.string.distance_remaining_format, NumberFormat.getIntegerInstance().format(it), stringResource(if (distanceUnit == "MILE") R.string.unit_mi else R.string.unit_km)))
            }
            schedule.daysRemaining?.let { Text(stringResource(R.string.days_to_deadline_format, it.toString())) }
            Text(
                stringResource(
                    R.string.deadline_determined_by_format,
                    stringResource(
                        when (schedule.determiningLimit) {
                            DueLimit.MILEAGE -> R.string.due_limit_mileage
                            DueLimit.TIME -> R.string.due_limit_time
                            else -> R.string.due_limit_unverified
                        }
                    )
                )
            )
            schedule.assumptions.distinct().forEach { Text(stringResource(R.string.bullet_format, it), style = MaterialTheme.typography.bodySmall) }
            Text(stringResource(R.string.dates_are_estimates), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun OilChangeRecordRow(record: OilChangeEntity, onEdit: () -> Unit, onReceipts: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(record.serviceDate, style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.two_part_format, NumberFormat.getIntegerInstance().format(record.odometerValue), stringResource(if (record.odometerUnit == "MILE") R.string.unit_mi else R.string.unit_km)))
        }
        val oilDescription = listOfNotNull(record.oilBrand, record.oilProduct, record.viscosityGrade, record.manufacturerApproval).joinToString(" · ")
        if (oilDescription.isNotBlank()) Text(oilDescription, style = MaterialTheme.typography.bodyMedium)
        record.quantityUsedMl?.let {
            Text(stringResource(R.string.added_litres_format, BigDecimal(it).divide(BigDecimal(1000)).stripTrailingZeros().toPlainString()), style = MaterialTheme.typography.bodySmall)
        }
        val filter = listOfNotNull(record.filterBrand, record.filterPartNumber).joinToString(" · ")
        if (filter.isNotBlank()) Text(stringResource(R.string.filter_format, filter), style = MaterialTheme.typography.bodySmall)
        record.totalCostMinor?.let {
            Text(stringResource(R.string.total_format, record.currencyCode, BigDecimal(it).divide(BigDecimal(100)).setScale(2).toPlainString()), style = MaterialTheme.typography.bodySmall)
        }
        val providerSuffix = record.serviceProvider?.let { stringResource(R.string.service_provider_suffix, it) } ?: ""
        Text("${record.serviceType}$providerSuffix", style = MaterialTheme.typography.bodySmall)
        if (!record.notes.isNullOrBlank()) Text(record.notes, style = MaterialTheme.typography.bodySmall)
        val editOilChangeCd = stringResource(R.string.edit_oil_change_dated_cd, record.serviceDate)
        val receiptsForOilChangeCd = stringResource(R.string.receipts_for_oil_change_dated_cd, record.serviceDate)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onEdit, modifier = Modifier.semantics { contentDescription = editOilChangeCd }) { Text(stringResource(R.string.edit)) }
            TextButton(onClick = onReceipts, modifier = Modifier.semantics { contentDescription = receiptsForOilChangeCd }) { Text(stringResource(R.string.receipts)) }
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
    val savingOilChangeCd = stringResource(R.string.saving_oil_change_cd)
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
        title = { Text(if (record == null) stringResource(R.string.log_oil_change) else stringResource(R.string.edit_oil_change)) },
        text = {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val availableHeight = if (maxHeight == Dp.Infinity) 520.dp else (maxHeight - 64.dp).coerceAtLeast(180.dp)
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = availableHeight).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
                        TextButton(onClick = onClearError) { Text(stringResource(R.string.dismiss_error)) }
                    }
                    Text(stringResource(R.string.service_details), style = MaterialTheme.typography.labelLarge)
                    DateTextField(value = date, onValueChange = { date = it }, label = stringResource(R.string.service_date_required_label), enabled = !busy)
                    OutlinedTextField(value = odometer, onValueChange = { odometer = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.odometer_required_label, stringResource(if (vehicle?.odometerUnit == "MILE") R.string.unit_mi else R.string.unit_km))) }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = oilBrand, onValueChange = { oilBrand = it }, label = { Text(stringResource(R.string.oil_brand_label)) }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = oilProduct, onValueChange = { oilProduct = it }, label = { Text(stringResource(R.string.oil_product_label)) }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = viscosity, onValueChange = { viscosity = it }, label = { Text(stringResource(R.string.viscosity_container_label)) }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = approval, onValueChange = { approval = it }, label = { Text(stringResource(R.string.manufacturer_approval_label)) }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = quantity, onValueChange = { quantity = it }, label = { Text(stringResource(R.string.oil_actually_added_label)) }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = filterBrand, onValueChange = { filterBrand = it }, label = { Text(stringResource(R.string.filter_brand_label)) }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = filterPart, onValueChange = { filterPart = it }, label = { Text(stringResource(R.string.filter_part_number_label)) }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = provider, onValueChange = { provider = it }, label = { Text(stringResource(R.string.garage_service_provider_label)) }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = serviceType, onValueChange = { serviceType = it }, label = { Text(stringResource(R.string.service_type_label)) }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = total, onValueChange = { total = it }, label = { Text(stringResource(R.string.total_cost_label)) }, singleLine = true, enabled = !busy)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = parts, onValueChange = { parts = it }, label = { Text(stringResource(R.string.parts_cost_label)) }, singleLine = true, enabled = !busy, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = labor, onValueChange = { labor = it }, label = { Text(stringResource(R.string.labor_cost_label)) }, singleLine = true, enabled = !busy, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = currency, onValueChange = { currency = it.uppercase().take(3) }, label = { Text(stringResource(R.string.currency_cad_label)) }, singleLine = true, enabled = !busy)
                    Text(stringResource(R.string.optional_interval_override_hint), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = distanceInterval, onValueChange = { distanceInterval = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.next_interval_label, stringResource(if (vehicle?.odometerUnit == "MILE") R.string.unit_mi else R.string.unit_km))) }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = timeInterval, onValueChange = { timeInterval = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.next_interval_months_label)) }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(stringResource(R.string.notes_label)) }, minLines = 2, enabled = !busy)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(OilChangeDraft(date, odometer, oilBrand, oilProduct, viscosity, approval, quantity, filterBrand, filterPart, provider, serviceType, total, parts, labor, currency, distanceInterval, timeInterval, notes))
            }, enabled = valid && !busy) {
                if (busy) CircularProgressIndicator(Modifier.semantics { contentDescription = savingOilChangeCd }) else Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) } }
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
