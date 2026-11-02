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
import ca.gllavoie.vehicletracker.data.local.OdometerReadingEntity
import ca.gllavoie.vehicletracker.data.repository.OdometerRepository
import ca.gllavoie.vehicletracker.data.repository.VehicleRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun OdometerRoute(
    vehicleId: String,
    vehicleRepository: VehicleRepository,
    odometerRepository: OdometerRepository,
    onDismiss: () -> Unit
) {
    val odometerViewModel: OdometerViewModel = viewModel(
        key = "odometer-$vehicleId",
        factory = OdometerViewModel.Factory(vehicleId, vehicleRepository, odometerRepository)
    )
    val state by odometerViewModel.uiState.collectAsStateWithLifecycle()
    OdometerHistoryDialog(
        state = state,
        onDismiss = onDismiss,
        onRecord = odometerViewModel::record,
        onConfirmReview = odometerViewModel::confirmReview,
        onDismissReview = odometerViewModel::dismissReview,
        onClearError = odometerViewModel::clearError
    )
}

@Composable
private fun OdometerHistoryDialog(
    state: OdometerUiState,
    onDismiss: () -> Unit,
    onRecord: (String, String, String, Boolean) -> Unit,
    onConfirmReview: () -> Unit,
    onDismissReview: () -> Unit,
    onClearError: () -> Unit
) {
    val vehicle = state.vehicle
    val today = rememberSaveable { LocalDate.now().toString() }
    var readingValue by rememberSaveable(vehicle?.id) { mutableStateOf("") }
    var readingDate by rememberSaveable(vehicle?.id) { mutableStateOf(today) }
    var notes by rememberSaveable(vehicle?.id) { mutableStateOf("") }
    var seenSaveCount by rememberSaveable(vehicle?.id) { mutableIntStateOf(state.saveSuccessCount) }
    var dismissAfterSave by rememberSaveable(vehicle?.id) { mutableStateOf(false) }
    LaunchedEffect(state.saveSuccessCount) {
        if (state.saveSuccessCount > seenSaveCount) {
            seenSaveCount = state.saveSuccessCount
            if (dismissAfterSave) {
                dismissAfterSave = false
                onDismiss()
            } else {
                readingValue = ""
                notes = ""
                readingDate = LocalDate.now().toString()
            }
        }
    }
    AlertDialog(
        onDismissRequest = { if (!state.isSaving) onDismiss() },
        title = { Text(vehicle?.let { "Update current mileage · ${it.make} ${it.model}" } ?: "Update current mileage") },
        text = {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val availableHeight = if (maxHeight == Dp.Infinity) 400.dp else (maxHeight - 64.dp).coerceAtLeast(180.dp)
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = availableHeight).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.isLoading || vehicle == null) {
                        CircularProgressIndicator(Modifier.semantics { contentDescription = "Loading odometer history" })
                    } else {
                        Text("New reading (${if (vehicle.odometerUnit == "MILE") "mi" else "km"})", style = MaterialTheme.typography.titleMedium)
                        Text("Two or more increasing readings on different dates let the app estimate mileage usage and project the next service date.", style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = readingValue,
                            onValueChange = { readingValue = it.filter(Char::isDigit) },
                            label = { Text("Odometer reading") },
                            singleLine = true,
                            enabled = !state.isSaving
                        )
                        OutlinedTextField(
                            value = readingDate,
                            onValueChange = { readingDate = it.take(10) },
                            label = { Text("Date (YYYY-MM-DD)") },
                            singleLine = true,
                            enabled = !state.isSaving
                        )
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Notes (optional)") },
                            singleLine = true,
                            enabled = !state.isSaving
                        )
                        state.error?.let { error ->
                            Column {
                                Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
                                TextButton(onClick = onClearError) { Text("Dismiss error") }
                            }
                        }
                        state.message?.let { message ->
                            Text(message, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        }
                        TextButton(
                            onClick = { dismissAfterSave = false; onRecord(readingValue, readingDate, notes, false) },
                            enabled = !state.isSaving && readingValue.isNotBlank() && readingDate.length == 10
                        ) {
                            if (state.isSaving) {
                                CircularProgressIndicator(Modifier.semantics { contentDescription = "Saving odometer reading" })
                                Text("Saving…", modifier = Modifier.padding(start = 8.dp))
                            } else Text("Add reading")
                        }
                        HorizontalDivider()
                        Text("Recorded readings", style = MaterialTheme.typography.titleMedium)
                        if (state.readings.isEmpty()) {
                            Text("No odometer readings recorded yet.", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            state.readings.forEach { reading -> OdometerReadingRow(reading) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (!shouldSaveOdometerDraftOnDone(readingValue, readingDate, notes, today)) {
                        onDismiss()
                    } else {
                        dismissAfterSave = true
                        onRecord(readingValue, readingDate, notes, false)
                    }
                },
                enabled = !state.isSaving
            ) { Text("Done") }
        }
    )
    state.reviewDraft?.let { review ->
        AlertDialog(
            onDismissRequest = { dismissAfterSave = false; onDismissReview() },
            title = { Text("Confirm odometer reading") },
            text = { Text("${review.reason}\n\nSave ${review.value} ${vehicle?.odometerUnit?.lowercase() ?: "units"} on ${review.date} anyway?") },
            confirmButton = { TextButton(onClick = onConfirmReview) { Text("Save reading") } },
            dismissButton = { TextButton(onClick = { dismissAfterSave = false; onDismissReview() }) { Text("Go back") } }
        )
    }
}

@Composable
private fun OdometerReadingRow(reading: OdometerReadingEntity) {
    val date = Instant.ofEpochMilli(reading.recordedAtUtc).atZone(ZoneId.systemDefault()).toLocalDate()
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${reading.value} ${if (reading.unit == "MILE") "mi" else "km"}", style = MaterialTheme.typography.bodyLarge)
            Text(DateTimeFormatter.ISO_LOCAL_DATE.format(date), style = MaterialTheme.typography.bodyMedium)
        }
        if (!reading.notes.isNullOrBlank()) Text(reading.notes, style = MaterialTheme.typography.bodySmall)
    }
}
