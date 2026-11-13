package ca.gllavoie.vehicletracker.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Read-only date field backed by the Material date picker. The stored value stays an
 * ISO `YYYY-MM-DD` string so the repository's date validation is unchanged.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var showPicker by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = { },
        label = { Text(label) },
        singleLine = true,
        readOnly = true,
        enabled = enabled,
        modifier = modifier,
        trailingIcon = {
            IconButton(onClick = { if (enabled) showPicker = true }) {
                Icon(Icons.Default.DateRange, contentDescription = null)
            }
        }
    )
    if (showPicker) {
        val zoneId = ZoneId.systemDefault()
        val initialMillis = runCatching { LocalDate.parse(value).atStartOfDay(zoneId).toInstant().toEpochMilli() }.getOrNull()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onValueChange(Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate().toString())
                    }
                    showPicker = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(android.R.string.cancel)) }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}
