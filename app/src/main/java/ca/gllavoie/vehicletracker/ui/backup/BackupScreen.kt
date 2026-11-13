package ca.gllavoie.vehicletracker.ui.backup

import android.content.Intent
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ca.gllavoie.vehicletracker.R
import ca.gllavoie.vehicletracker.data.repository.BackupPreview
import ca.gllavoie.vehicletracker.data.repository.BackupRepository
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@Composable
fun BackupRoute(repository: BackupRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val creatingValidatingBackupCd = stringResource(R.string.creating_validating_backup_cd)
    var history by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var refreshCount by rememberSaveable { mutableIntStateOf(0) }
    var isBusy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingRestoreUri by rememberSaveable { mutableStateOf<Uri?>(null) }
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
                        ?: throw IllegalStateException(context.getString(R.string.backup_open_output_error))
                    val result = output.use { repository.createBackup(it) }
                    message = context.getString(R.string.backup_created_format, result.recordCounts.values.sum(), result.attachmentCount)
                    refreshCount++
                } catch (failure: Exception) {
                    runCatching { context.contentResolver.delete(destination, null, null) }
                    error = failure.message ?: context.getString(R.string.backup_creation_failed)
                    refreshCount++
                } finally {
                    isBusy = false
                }
            }
        }
    }

    val chooseBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { selected ->
        if (selected != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(selected, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            pendingRestoreUri = selected
            error = null
            message = null
        }
    }

    LaunchedEffect(pendingRestoreUri) {
        val uri = pendingRestoreUri ?: return@LaunchedEffect
        isBusy = true
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException(context.getString(R.string.backup_open_input_error))
            restorePreview = input.use { repository.validateBackup(it) }
        } catch (failure: Exception) {
            error = failure.message ?: context.getString(R.string.backup_validation_failed)
            restorePreview = null
        } finally {
            isBusy = false
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.backup_description))
        Button(onClick = { createBackup.launch("vehicle_tracker_backup_${System.currentTimeMillis()}.zip") }, enabled = !isBusy) {
            if (isBusy) CircularProgressIndicator(Modifier.semantics { contentDescription = creatingValidatingBackupCd }) else Text(stringResource(R.string.create_backup))
        }
        Button(onClick = { chooseBackup.launch(arrayOf("application/zip", "application/octet-stream")) }, enabled = !isBusy) {
            Text(stringResource(R.string.choose_backup_to_restore))
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }
        HorizontalDivider()
        Text(stringResource(R.string.backup_history), style = MaterialTheme.typography.titleLarge)
        val lastSuccess = history["backup.last_success_utc"]?.toLongOrNull()
        Text(stringResource(R.string.last_successful_backup_format, lastSuccess?.let { DateFormat.getDateTimeInstance().format(Date(it)) } ?: stringResource(R.string.never)))
        history["backup.last_restore_utc"]?.toLongOrNull()?.let {
            Text(stringResource(R.string.last_restore_format, DateFormat.getDateTimeInstance().format(Date(it))))
        }
        history["backup.last_error"]?.takeIf(String::isNotBlank)?.let {
            Text(stringResource(R.string.last_backup_error_format, it), color = MaterialTheme.colorScheme.error)
        }
        Text(stringResource(R.string.backup_offsite_hint), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.backup_encryption_hint), style = MaterialTheme.typography.bodySmall)
    }

    restorePreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { restorePreview = null; pendingRestoreUri = null },
            title = { Text(stringResource(R.string.replace_garage_data_title)) },
            text = {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val availableHeight = if (maxHeight == Dp.Infinity) 400.dp else (maxHeight - 64.dp).coerceAtLeast(160.dp)
                    Column(Modifier.heightIn(max = availableHeight).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.backup_validation_passed))
                        Text(stringResource(R.string.created_format, DateFormat.getDateTimeInstance().format(Date(preview.createdAtUtc))))
                        Text(stringResource(R.string.app_version_format, preview.appVersion))
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }
                        preview.recordCounts.filterValues { it > 0 }.forEach { (table, count) ->
                            Text(stringResource(R.string.record_count_format, table.replace('_', ' '), count))
                        }
                        Text(stringResource(R.string.receipt_files_format, preview.attachmentCount))
                        Text(stringResource(R.string.restore_warning), color = MaterialTheme.colorScheme.error)
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
                                    ?: throw IllegalStateException(context.getString(R.string.backup_reopen_error))
                                val result = input.use { repository.restoreBackup(it, confirmReplace = true) }
                                message = context.getString(R.string.restore_completed_format, result.recordCounts.values.sum(), result.attachmentCount)
                                restorePreview = null
                                pendingRestoreUri = null
                                refreshCount++
                            } catch (failure: Exception) {
                                error = failure.message ?: context.getString(R.string.restore_failed)
                                refreshCount++
                            } finally {
                                isBusy = false
                            }
                        }
                    }
                }, enabled = !isBusy) { Text(stringResource(R.string.replace_data)) }
            },
            dismissButton = { TextButton(onClick = { restorePreview = null; pendingRestoreUri = null }, enabled = !isBusy) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
