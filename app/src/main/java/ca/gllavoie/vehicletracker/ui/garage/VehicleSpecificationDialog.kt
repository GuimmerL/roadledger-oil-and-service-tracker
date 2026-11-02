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
    val catalogSpecification by (vehicle.defaultSpecificationId?.let(repository::observeCatalogEntry) ?: flowOf(null))
        .collectAsStateWithLifecycle(null)
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
            title = { Text("Loading specifications") },
            text = { CircularProgressIndicator(Modifier.semantics { contentDescription = "Loading vehicle specifications" }) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
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
    var viscosity by rememberSaveable(vehicle.id) { mutableStateOf(override?.viscosityGrade.orEmpty()) }
    var approval by rememberSaveable(vehicle.id) { mutableStateOf(override?.manufacturerApproval.orEmpty()) }
    var capacity by rememberSaveable(vehicle.id) {
        mutableStateOf(override?.capacityWithFilterMl?.let { java.math.BigDecimal(it).divide(java.math.BigDecimal(1_000)).stripTrailingZeros().toPlainString() }.orEmpty())
    }
    var distance by rememberSaveable(vehicle.id) { mutableStateOf(override?.distanceInterval?.toString().orEmpty()) }
    var months by rememberSaveable(vehicle.id) { mutableStateOf(override?.timeIntervalMonths?.toString().orEmpty()) }
    var filterPart by rememberSaveable(vehicle.id) { mutableStateOf(override?.oilFilterPartNumber.orEmpty()) }
    var source by rememberSaveable(vehicle.id) { mutableStateOf(override?.sourceDescription.orEmpty()) }
    val unitLabel = if (vehicle.odometerUnit == "MILE") "mi" else "km"
    val savedDistance = override?.distanceInterval?.let { distance ->
        convertDistanceIntervalToUnit(distance, override.distanceUnit, vehicle.odometerUnit)
    } ?: catalogSpecification?.distanceInterval?.let { distance ->
        convertDistanceIntervalToUnit(distance, catalogSpecification.distanceUnit, vehicle.odometerUnit)
    }
    val savedMonths = override?.timeIntervalMonths ?: catalogSpecification?.timeIntervalMonths
    val savedInterval = listOfNotNull(
        savedDistance?.let { "${NumberFormat.getIntegerInstance().format(it)} $unitLabel" },
        savedMonths?.let { "$it months" }
    ).joinToString(" · ").ifBlank {
        catalogSpecification?.serviceNotes.orEmpty().lineSequence()
            .firstOrNull { it.startsWith("Raw normal-service interval:") }
            ?.substringAfter(":")?.trim()
            ?.takeIf(String::isNotBlank)
            ?: "Not set"
    }
    val intervalSource = when {
        override?.distanceInterval != null || override?.timeIntervalMonths != null -> "Saved vehicle-specific override (manual; not independently verified)"
        catalogSpecification == null -> "No matching database specification selected"
        VehicleCatalogOptions.isOilIntervalVerified(catalogSpecification.verificationStatus) -> "Selected database interval (verified by the user)"
        catalogSpecification.verificationStatus == "OFFICIAL_INTERVAL_RESEARCHED" -> "Database source found; user confirmation is required before scheduling uses it"
        else -> "Database suggestion is unverified and is not used for scheduling"
    }

    AlertDialog(
        onDismissRequest = { if (!state.isSaving) onDismiss() },
        title = { Text("Vehicle specifications") },
        text = {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val availableHeight = if (maxHeight == Dp.Infinity) 460.dp else (maxHeight - 64.dp).coerceAtLeast(180.dp)
                Column(Modifier.fillMaxWidth().heightIn(max = availableHeight).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (state.isLoading) {
                        CircularProgressIndicator(Modifier.semantics { contentDescription = "Loading vehicle specifications" })
                    } else {
                        Text("These are manual vehicle-specific values. They are NOT manufacturer-verified by this app; check the owner's manual or official service information.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        Text("Current oil interval in saved data: $savedInterval", style = MaterialTheme.typography.titleSmall)
                        Text(intervalSource, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Enter a vehicle-specific value below to use or change the interval. Leave a field blank if you do not want to set that limit.", style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(value = viscosity, onValueChange = { viscosity = it.uppercase() }, label = { Text("Recommended oil viscosity") }, singleLine = true, enabled = !state.isSaving)
                        OutlinedTextField(value = approval, onValueChange = { approval = it }, label = { Text("Required manufacturer approval/specification") }, singleLine = true, enabled = !state.isSaving)
                        OutlinedTextField(value = capacity, onValueChange = { capacity = it }, label = { Text("Capacity with filter (litres)") }, singleLine = true, enabled = !state.isSaving)
                        HorizontalDivider()
                        Text("Oil change interval", style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(value = distance, onValueChange = { distance = it.filter(Char::isDigit) }, label = { Text("Manual distance interval ($unitLabel)") }, singleLine = true, enabled = !state.isSaving)
                        OutlinedTextField(value = months, onValueChange = { months = it.filter(Char::isDigit) }, label = { Text("Manual time interval (months)") }, singleLine = true, enabled = !state.isSaving)
                        OutlinedTextField(value = filterPart, onValueChange = { filterPart = it }, label = { Text("Oil filter part number") }, singleLine = true, enabled = !state.isSaving)
                        OutlinedTextField(value = source, onValueChange = { source = it }, label = { Text("Source name and/or URL (optional note)") }, minLines = 2, enabled = !state.isSaving)
                        Text("Source notes are preserved with this vehicle override. No grade, capacity, approval, or interval is inferred when a field is blank.", style = MaterialTheme.typography.bodySmall)
                        state.error?.let { error ->
                            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
                            TextButton(onClick = onClearError) { Text("Dismiss error") }
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
                if (state.isSaving) CircularProgressIndicator(Modifier.semantics { contentDescription = "Saving vehicle specifications" }) else Text("Save manual values")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.isSaving) { Text("Close") } }
    )
}
