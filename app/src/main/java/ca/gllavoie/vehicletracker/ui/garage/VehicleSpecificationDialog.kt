package ca.gllavoie.vehicletracker.ui.garage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.remember
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
import ca.gllavoie.vehicletracker.data.local.VehicleEntity
import ca.gllavoie.vehicletracker.data.local.VehicleSpecificationEntity
import ca.gllavoie.vehicletracker.data.repository.VehicleCatalogOptions
import ca.gllavoie.vehicletracker.data.repository.VehicleSpecificationRepository
import ca.gllavoie.vehicletracker.preferences.convertDistanceIntervalToUnit
import java.text.NumberFormat
import kotlinx.coroutines.flow.flowOf

@Composable
fun VehicleSpecificationRoute(
    vehicle: VehicleEntity,
    repository: VehicleSpecificationRepository,
    onDismiss: () -> Unit
) {
    val specificationViewModel: VehicleSpecificationViewModel = viewModel(
        key = "specification-${vehicle.id}",
        factory = VehicleSpecificationViewModel.Factory(vehicle.id, repository)
    )
    val state by specificationViewModel.uiState.collectAsStateWithLifecycle()
    val loadingVehicleSpecificationsCd = stringResource(R.string.loading_vehicle_specifications)
    val catalogSpecification by remember(vehicle.defaultSpecificationId) {
        vehicle.defaultSpecificationId?.let(repository::observeCatalogEntry) ?: flowOf(null)
    }.collectAsStateWithLifecycle(null)
    var saveCountAtOpen by rememberSaveable(vehicle.id) { mutableIntStateOf(state.saveSuccessCount) }
    LaunchedEffect(state.saveSuccessCount) {
        if (state.saveSuccessCount > saveCountAtOpen) {
            saveCountAtOpen = state.saveSuccessCount
            onDismiss()
        }
    }
    if (state.isLoading) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.loading_specifications)) },
            text = { CircularProgressIndicator(Modifier.semantics { contentDescription = loadingVehicleSpecificationsCd }) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
        )
    } else {
        VehicleSpecificationDialog(
            vehicle = vehicle,
            state = state,
            catalogSpecification = catalogSpecification,
            onDismiss = onDismiss,
            onSave = specificationViewModel::save,
            onClearError = specificationViewModel::clearError
        )
    }
}

@Composable
private fun VehicleSpecificationDialog(
    vehicle: VehicleEntity,
    state: VehicleSpecificationUiState,
    catalogSpecification: VehicleSpecificationEntity?,
    onDismiss: () -> Unit,
    onSave: (VehicleSpecificationDraft, String) -> Unit,
    onClearError: () -> Unit
) {
    val override = state.override
    val loadingVehicleSpecificationsCd = stringResource(R.string.loading_vehicle_specifications)
    val savingVehicleSpecificationsCd = stringResource(R.string.saving_vehicle_specifications_cd)
    var viscosity by rememberSaveable(vehicle.id) { mutableStateOf(override?.viscosityGrade.orEmpty()) }
    var approval by rememberSaveable(vehicle.id) { mutableStateOf(override?.manufacturerApproval.orEmpty()) }
    var capacity by rememberSaveable(vehicle.id) {
        mutableStateOf(override?.capacityWithFilterMl?.let { java.math.BigDecimal(it).divide(java.math.BigDecimal(1_000)).stripTrailingZeros().toPlainString() }.orEmpty())
    }
    var distance by rememberSaveable(vehicle.id) { mutableStateOf(override?.distanceInterval?.toString().orEmpty()) }
    var months by rememberSaveable(vehicle.id) { mutableStateOf(override?.timeIntervalMonths?.toString().orEmpty()) }
    var filterPart by rememberSaveable(vehicle.id) { mutableStateOf(override?.oilFilterPartNumber.orEmpty()) }
    var source by rememberSaveable(vehicle.id) { mutableStateOf(override?.sourceDescription.orEmpty()) }
    val unitLabel = stringResource(if (vehicle.odometerUnit == "MILE") R.string.unit_mi else R.string.unit_km)
    val savedDistance = override?.distanceInterval?.let { distance ->
        convertDistanceIntervalToUnit(distance, override.distanceUnit, vehicle.odometerUnit)
    } ?: catalogSpecification?.distanceInterval?.let { distance ->
        convertDistanceIntervalToUnit(distance, catalogSpecification.distanceUnit, vehicle.odometerUnit)
    }
    val savedMonths = override?.timeIntervalMonths ?: catalogSpecification?.timeIntervalMonths
    val savedDistanceText = savedDistance?.let {
        stringResource(R.string.two_part_format, NumberFormat.getIntegerInstance().format(it), unitLabel)
    }
    val savedMonthsText = savedMonths?.let { stringResource(R.string.months_format, it) }
    val savedInterval = listOfNotNull(savedDistanceText, savedMonthsText).joinToString(" · ").ifBlank {
        catalogSpecification?.serviceNotes.orEmpty().lineSequence()
            .firstOrNull { it.startsWith("Raw normal-service interval:") }
            ?.substringAfter(":")?.trim()
            ?.takeIf(String::isNotBlank)
            ?: stringResource(R.string.not_set)
    }
    val intervalSource = when {
        override?.distanceInterval != null || override?.timeIntervalMonths != null -> stringResource(R.string.interval_source_manual_override)
        catalogSpecification == null -> stringResource(R.string.interval_source_no_match)
        VehicleCatalogOptions.isOilIntervalVerified(catalogSpecification.verificationStatus) -> stringResource(R.string.interval_source_db_verified)
        catalogSpecification.verificationStatus == "OFFICIAL_INTERVAL_RESEARCHED" -> stringResource(R.string.interval_source_db_confirm)
        else -> stringResource(R.string.interval_source_db_unverified)
    }

    AlertDialog(
        onDismissRequest = { if (!state.isSaving) onDismiss() },
        title = { Text(stringResource(R.string.vehicle_specifications_title)) },
        text = {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val availableHeight = if (maxHeight == Dp.Infinity) 460.dp else (maxHeight - 64.dp).coerceAtLeast(180.dp)
                Column(Modifier.fillMaxWidth().heightIn(max = availableHeight).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (state.isLoading) {
                        CircularProgressIndicator(Modifier.semantics { contentDescription = loadingVehicleSpecificationsCd })
                    } else {
                        Text(stringResource(R.string.manual_specs_warning), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.current_oil_interval_format, savedInterval), style = MaterialTheme.typography.titleSmall)
                        Text(intervalSource, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.manual_interval_hint), style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(value = viscosity, onValueChange = { viscosity = it.uppercase() }, label = { Text(stringResource(R.string.recommended_oil_viscosity_label)) }, singleLine = true, enabled = !state.isSaving)
                        OutlinedTextField(value = approval, onValueChange = { approval = it }, label = { Text(stringResource(R.string.required_manufacturer_approval_label)) }, singleLine = true, enabled = !state.isSaving)
                        OutlinedTextField(value = capacity, onValueChange = { capacity = it }, label = { Text(stringResource(R.string.capacity_with_filter_litres_label)) }, singleLine = true, enabled = !state.isSaving)
                        HorizontalDivider()
                        Text(stringResource(R.string.oil_change_interval), style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(value = distance, onValueChange = { distance = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.manual_distance_interval_label, unitLabel)) }, singleLine = true, enabled = !state.isSaving)
                        OutlinedTextField(value = months, onValueChange = { months = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.manual_time_interval_label)) }, singleLine = true, enabled = !state.isSaving)
                        OutlinedTextField(value = filterPart, onValueChange = { filterPart = it }, label = { Text(stringResource(R.string.oil_filter_part_number_label)) }, singleLine = true, enabled = !state.isSaving)
                        OutlinedTextField(value = source, onValueChange = { source = it }, label = { Text(stringResource(R.string.source_name_url_label)) }, minLines = 2, enabled = !state.isSaving)
                        Text(stringResource(R.string.source_notes_hint), style = MaterialTheme.typography.bodySmall)
                        state.error?.let { error ->
                            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
                            TextButton(onClick = onClearError) { Text(stringResource(R.string.dismiss_error)) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(VehicleSpecificationDraft(viscosity, approval, capacity, distance, months, filterPart, source), vehicle.odometerUnit) },
                enabled = !state.isLoading && !state.isSaving
            ) {
                if (state.isSaving) CircularProgressIndicator(Modifier.semantics { contentDescription = savingVehicleSpecificationsCd }) else Text(stringResource(R.string.save_manual_values))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.isSaving) { Text(stringResource(R.string.close)) } }
    )
}
