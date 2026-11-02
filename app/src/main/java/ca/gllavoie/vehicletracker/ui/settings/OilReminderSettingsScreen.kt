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
            error = "Notifications permission was not granted. Enable it in Android Settings to receive oil reminders."
        }
    }

    fun turnOnReminders() {
        error = null
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            showPermissionRationale = true
        } else if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            error = "Notifications are disabled for RoadLedger in Android Settings."
        } else {
            OilReminderNotifications.enable(context)
            enabled = true
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.app_name_full), style = MaterialTheme.typography.bodySmall)
        Text("Measurement system", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MeasurementSystem.entries.forEach { system ->
                FilterChip(
                    selected = measurementSystem == system,
                    onClick = {
                        measurementSystem = system
                        MeasurementSystemPreferences.set(context, system)
                    },
                    label = { Text(system.label) }
                )
            }
        }
        Text("Recommended oil-change distance intervals are converted to this unit system, and new vehicles default to it. Existing odometer and service records keep their saved units.", style = MaterialTheme.typography.bodySmall)
        Text("Appearance", style = MaterialTheme.typography.titleLarge)
        ThemeMode.entries.forEach { mode ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onThemeModeChanged(mode) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = themeMode == mode, onClick = { onThemeModeChanged(mode) })
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(mode.label, style = MaterialTheme.typography.titleMedium)
                    Text(mode.description, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text("Button color", style = MaterialTheme.typography.titleMedium)
        ButtonColorTheme.entries.forEach { colorTheme ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onButtonColorThemeChanged(colorTheme) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = buttonColorTheme == colorTheme, onClick = { onButtonColorThemeChanged(colorTheme) })
                Text(colorTheme.label, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
        Text("Phone colors use Android's wallpaper palette when available. Blue, teal, violet, green, amber, and rose change the app's primary button color.", style = MaterialTheme.typography.bodySmall)
        Text("Oil change reminders", style = MaterialTheme.typography.titleLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Daily notification check", style = MaterialTheme.typography.titleMedium)
                Text("Notify when a saved oil-change schedule is due soon, due, or overdue.", style = MaterialTheme.typography.bodySmall)
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
                modifier = Modifier.semantics { contentDescription = "Enable oil change notifications" }
            )
        }
        Text("Reminders use the stored interval and current odometer. The current due-soon thresholds are 1,000 miles/kilometres or 30 days. Intervals must be entered or user-verified, and Android notification permission must remain enabled.", style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }

    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = { Text("Allow oil change notifications?") },
            text = { Text("RoadLedger checks saved oil-change schedules once per day and posts a notification when service is approaching or due.") },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionRationale = false
                    permissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { showPermissionRationale = false }) { Text("Not now") } }
        )
    }
}
