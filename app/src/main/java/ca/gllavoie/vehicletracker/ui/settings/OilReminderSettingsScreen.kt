package ca.gllavoie.vehicletracker.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ca.gllavoie.vehicletracker.R
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ca.gllavoie.vehicletracker.notifications.OilReminderNotifications
import ca.gllavoie.vehicletracker.preferences.ButtonColorTheme
import ca.gllavoie.vehicletracker.preferences.MeasurementSystem
import ca.gllavoie.vehicletracker.preferences.MeasurementSystemPreferences
import ca.gllavoie.vehicletracker.preferences.ThemeMode

@Composable
fun OilReminderSettingsScreen(
    themeMode: ThemeMode,
    buttonColorTheme: ButtonColorTheme,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onButtonColorThemeChanged: (ButtonColorTheme) -> Unit
) {
    val context = LocalContext.current
    var enabled by rememberSaveable { mutableStateOf(OilReminderNotifications.isEnabled(context)) }
    var measurementSystem by rememberSaveable { mutableStateOf(MeasurementSystemPreferences.get(context)) }
    var showPermissionRationale by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            OilReminderNotifications.enable(context)
            enabled = true
            error = null
        } else {
            enabled = false
            error = context.getString(R.string.notifications_permission_denied)
        }
    }

    fun turnOnReminders() {
        error = null
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            showPermissionRationale = true
        } else if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            error = context.getString(R.string.notifications_disabled)
        } else {
            OilReminderNotifications.enable(context)
            enabled = true
        }
    }

    val enableOilChangeNotificationsCd = stringResource(R.string.enable_oil_change_notifications_cd)
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.app_name_full), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.measurement_system_title), style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MeasurementSystem.entries.forEach { system ->
                FilterChip(
                    selected = measurementSystem == system,
                    onClick = {
                        measurementSystem = system
                        MeasurementSystemPreferences.set(context, system)
                    },
                    label = { Text(stringResource(system.labelRes)) }
                )
            }
        }
        Text(stringResource(R.string.measurement_system_hint), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.appearance_title), style = MaterialTheme.typography.titleLarge)
        ThemeMode.entries.forEach { mode ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onThemeModeChanged(mode) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = themeMode == mode, onClick = { onThemeModeChanged(mode) })
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(stringResource(mode.labelRes), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(mode.descriptionRes), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text(stringResource(R.string.button_color_title), style = MaterialTheme.typography.titleMedium)
        ButtonColorTheme.entries.forEach { colorTheme ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onButtonColorThemeChanged(colorTheme) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = buttonColorTheme == colorTheme, onClick = { onButtonColorThemeChanged(colorTheme) })
                Text(stringResource(colorTheme.labelRes), modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
        Text(stringResource(R.string.button_color_hint), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.oil_reminder_channel_name), style = MaterialTheme.typography.titleLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.daily_notification_check), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.daily_notification_check_hint), style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = enabled,
                onCheckedChange = { checked ->
                    if (checked) turnOnReminders()
                    else {
                        OilReminderNotifications.disable(context)
                        enabled = false
                        error = null
                    }
                },
                modifier = Modifier.semantics { contentDescription = enableOilChangeNotificationsCd }
            )
        }
        Text(stringResource(R.string.reminders_hint), style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }

    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = { Text(stringResource(R.string.allow_oil_change_notifications_title)) },
            text = { Text(stringResource(R.string.permission_rationale)) },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionRationale = false
                    permissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text(stringResource(R.string.continue_label)) }
            },
            dismissButton = { TextButton(onClick = { showPermissionRationale = false }) { Text(stringResource(R.string.not_now)) } }
        )
    }
}
