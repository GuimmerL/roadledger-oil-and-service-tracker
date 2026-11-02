package ca.gllavoie.vehicletracker.ui.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ca.gllavoie.vehicletracker.data.repository.BackupPreview
import ca.gllavoie.vehicletracker.data.repository.BackupRepository
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@Composable
fun BackupRoute(repository: BackupRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var history by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var refreshCount by rememberSaveable { mutableIntStateOf(0) }
    var isBusy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var restorePreview by remember { mutableStateOf<BackupPreview?>(null) }

    LaunchedEffect(refreshCount) { history = runCatching { repository.history() }.getOrDefault(emptyMap()) }

    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { destination ->
        if (destination != null) {
            scope.launch {
                isBusy = true
                error = null
                message = null
                try {
                    val output = context.contentResolver.openOutputStream(destination)
                        ?: throw IllegalStateException("The selected backup location could not be opened.")
                    val result = output.use { repository.createBackup(it) }
                    message = "Backup created · ${result.recordCounts.values.sum()} records · ${result.attachmentCount} receipts."
                    refreshCount++
                } catch (failure: Exception) {
                    runCatching { context.contentResolver.delete(destination, null, null) }
                    error = failure.message ?: "Backup creation failed."
                    refreshCount++
                } finally {
                    isBusy = false
                }
            }
        }
    }

    val chooseBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { selected ->
        if (selected != null) {
            scope.launch {
                isBusy = true
                error = null
                message = null
                try {
                    val input = context.contentResolver.openInputStream(selected)
                        ?: throw IllegalStateException("The selected backup could not be opened.")
                    val preview = input.use { repository.validateBackup(it) }
                    pendingRestoreUri = selected
                    restorePreview = preview
                } catch (failure: Exception) {
                    error = failure.message ?: "Backup validation failed."
                    pendingRestoreUri = null
                    restorePreview = null
                } finally {
                    isBusy = false
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Backups & Restore", style = MaterialTheme.typography.headlineMedium)
        Text("Create a ZIP export containing all vehicle records, settings and receipt files. Restore replaces all current data after full validation.")
        Button(onClick = { createBackup.launch("vehicle_tracker_backup_${System.currentTimeMillis()}.zip") }, enabled = !isBusy) {
            if (isBusy) CircularProgressIndicator(Modifier.semantics { contentDescription = "Creating or validating backup" }) else Text("Create backup")
        }
        Button(onClick = { chooseBackup.launch(arrayOf("application/zip", "application/octet-stream")) }, enabled = !isBusy) {
            Text("Choose backup to restore")
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }
        HorizontalDivider()
        Text("Backup history", style = MaterialTheme.typography.titleLarge)
        val lastSuccess = history["backup.last_success_utc"]?.toLongOrNull()
        Text("Last successful backup: ${lastSuccess?.let { DateFormat.getDateTimeInstance().format(Date(it)) } ?: "Never"}")
        history["backup.last_restore_utc"]?.toLongOrNull()?.let { Text("Last restore: ${DateFormat.getDateTimeInstance().format(Date(it))}") }
        history["backup.last_error"]?.takeIf(String::isNotBlank)?.let {
            Text("Last backup/restore error: $it", color = MaterialTheme.colorScheme.error)
        }
        Text("Keep a copy on a different physical device or drive. An archive on this phone cannot protect against loss or failure of the same device.", style = MaterialTheme.typography.bodySmall)
        Text("Backups are not encrypted. Store exported files securely; VINs, plates and receipts may contain sensitive information.", style = MaterialTheme.typography.bodySmall)
    }

    restorePreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { restorePreview = null; pendingRestoreUri = null },
            title = { Text("Replace current garage data?") },
            text = {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val availableHeight = if (maxHeight == Dp.Infinity) 400.dp else (maxHeight - 64.dp).coerceAtLeast(160.dp)
                    Column(Modifier.heightIn(max = availableHeight).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("This backup passed archive, schema, record relationship, attachment and checksum validation.")
                        Text("Created: ${DateFormat.getDateTimeInstance().format(Date(preview.createdAtUtc))}")
                        Text("App version: ${preview.appVersion}")
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }
                        preview.recordCounts.filterValues { it > 0 }.forEach { (table, count) ->
                            Text("${table.replace('_', ' ')}: $count")
                        }
                        Text("Receipt files: ${preview.attachmentCount}")
                        Text("Restoration will replace all vehicles, service records, settings, reminders and receipts on this installation. This cannot be undone here. Export a backup first if you need the current data.", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val uri = pendingRestoreUri
                    if (uri != null) {
                        scope.launch {
                            isBusy = true
                            error = null
                            try {
                                val input = context.contentResolver.openInputStream(uri)
                                    ?: throw IllegalStateException("The selected backup could not be reopened.")
                                val result = input.use { repository.restoreBackup(it, confirmReplace = true) }
                                message = "Restore completed · ${result.recordCounts.values.sum()} records · ${result.attachmentCount} receipts."
                                restorePreview = null
                                pendingRestoreUri = null
                                refreshCount++
                            } catch (failure: Exception) {
                                error = failure.message ?: "Restore failed; the previous data was retained."
                                refreshCount++
                            } finally {
                                isBusy = false
                            }
                        }
                    }
                }, enabled = !isBusy) { Text("Replace data") }
            },
            dismissButton = { TextButton(onClick = { restorePreview = null; pendingRestoreUri = null }, enabled = !isBusy) { Text("Cancel") } }
        )
    }
}
