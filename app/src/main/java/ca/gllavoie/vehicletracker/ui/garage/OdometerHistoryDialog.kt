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
    val loadingOdometerHistoryCd = stringResource(R.string.loading_odometer_history)
    val savingOdometerReadingCd = stringResource(R.string.saving_odometer_reading_cd)
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
        title = {
            Text(
                vehicle?.let {
                    stringResource(R.string.update_current_mileage_vehicle_format, it.make, it.model)
                } ?: stringResource(R.string.update_current_mileage)
            )
        },
        text = {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val availableHeight = if (maxHeight == Dp.Infinity) 400.dp else (maxHeight - 64.dp).coerceAtLeast(180.dp)
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = availableHeight).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(Modifier.semantics { contentDescription = loadingOdometerHistoryCd })
                    } else if (vehicle == null) {
                        Text(stringResource(R.string.vehicle_no_longer_available))
                    } else {
                        Text(stringResource(R.string.new_reading_label, stringResource(if (vehicle.odometerUnit == "MILE") R.string.unit_mi else R.string.unit_km)), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.odometer_hint), style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = readingValue,
                            onValueChange = { readingValue = it.filter(Char::isDigit) },
                            label = { Text(stringResource(R.string.odometer_reading_label)) },
                            singleLine = true,
                            enabled = !state.isSaving
                        )
                        OutlinedTextField(
                            value = readingDate,
                            onValueChange = { readingDate = it.take(10) },
                            label = { Text(stringResource(R.string.date_ymd_label)) },
                            singleLine = true,
                            enabled = !state.isSaving
                        )
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text(stringResource(R.string.notes_optional)) },
                            singleLine = true,
                            enabled = !state.isSaving
                        )
                        state.error?.let { error ->
                            Column {
                                Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
                                TextButton(onClick = onClearError) { Text(stringResource(R.string.dismiss_error)) }
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
                                CircularProgressIndicator(Modifier.semantics { contentDescription = savingOdometerReadingCd })
                                Text(stringResource(R.string.saving_ellipsis), modifier = Modifier.padding(start = 8.dp))
                            } else Text(stringResource(R.string.add_reading))
                        }
                        HorizontalDivider()
                        Text(stringResource(R.string.recorded_readings), style = MaterialTheme.typography.titleMedium)
                        if (state.readings.isEmpty()) {
                            Text(stringResource(R.string.no_odometer_readings), style = MaterialTheme.typography.bodyMedium)
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
            ) { Text(stringResource(R.string.done)) }
        }
    )
    state.reviewDraft?.let { review ->
        AlertDialog(
            onDismissRequest = { dismissAfterSave = false; onDismissReview() },
            title = { Text(stringResource(R.string.confirm_odometer_reading_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.confirm_odometer_message,
                        review.reason,
                        review.value.toString(),
                        vehicle?.odometerUnit?.lowercase() ?: stringResource(R.string.units),
                        review.date.toString()
                    )
                )
            },
            confirmButton = { TextButton(onClick = onConfirmReview) { Text(stringResource(R.string.save_reading)) } },
            dismissButton = { TextButton(onClick = { dismissAfterSave = false; onDismissReview() }) { Text(stringResource(R.string.go_back)) } }
        )
    }
}

@Composable
private fun OdometerReadingRow(reading: OdometerReadingEntity) {
    val date = Instant.ofEpochMilli(reading.recordedAtUtc).atZone(ZoneId.systemDefault()).toLocalDate()
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.two_part_format, reading.value.toString(), stringResource(if (reading.unit == "MILE") R.string.unit_mi else R.string.unit_km)), style = MaterialTheme.typography.bodyLarge)
            Text(DateTimeFormatter.ISO_LOCAL_DATE.format(date), style = MaterialTheme.typography.bodyMedium)
        }
        if (!reading.notes.isNullOrBlank()) Text(reading.notes, style = MaterialTheme.typography.bodySmall)
    }
}
