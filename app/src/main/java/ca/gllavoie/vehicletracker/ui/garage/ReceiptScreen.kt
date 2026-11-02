package ca.gllavoie.vehicletracker.ui.garage

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.gllavoie.vehicletracker.data.local.ReceiptAttachmentEntity
import ca.gllavoie.vehicletracker.data.repository.ReceiptRepository
import java.io.IOException
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ReceiptRoute(
    vehicleId: String,
    serviceId: String,
    receiptRepository: ReceiptRepository,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val attachments by receiptRepository.observeForService(serviceId).collectAsStateWithLifecycle(emptyList())
    var error by rememberSaveable(serviceId) { mutableStateOf<String?>(null) }
    var busy by rememberSaveable(serviceId) { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ReceiptAttachmentEntity?>(null) }
    var pendingDownload by remember { mutableStateOf<ReceiptAttachmentEntity?>(null) }
    val openDocuments = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                busy = true
                error = null
                try {
                    uris.forEach { uri ->
                        val name = queryDisplayName(context, uri)
                        receiptRepository.addForOilChange(vehicleId, serviceId, uri, name)
                    }
                } catch (failure: Exception) {
                    error = failure.message ?: "Unable to add receipt."
                } finally {
                    busy = false
                }
            }
        }
    }
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { targetUri ->
        val attachment = pendingDownload
        if (targetUri != null && attachment != null) {
            scope.launch {
                busy = true
                error = null
                try {
                    val source = receiptRepository.getVerifiedFile(attachment.id)
                    withContext(Dispatchers.IO) {
                        val output = context.contentResolver.openOutputStream(targetUri)
                            ?: throw IOException("The selected download location could not be opened.")
                        output.use { out -> source.inputStream().use { input -> input.copyTo(out) } }
                    }
                } catch (failure: Exception) {
                    error = failure.message ?: "Unable to download receipt."
                } finally {
                    busy = false
                    pendingDownload = null
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Receipts") },
        text = {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val availableHeight = if (maxHeight == Dp.Infinity) 400.dp else (maxHeight - 64.dp).coerceAtLeast(180.dp)
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = availableHeight).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("PDF, JPG and PNG · up to 10 MB each · stored privately on this device.")
                    Button(onClick = { openDocuments.launch(arrayOf("application/pdf", "image/jpeg", "image/png")) }, enabled = !busy) {
                        if (busy) CircularProgressIndicator(Modifier.semantics { contentDescription = "Processing receipts" }) else Text("Add receipt")
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }
                    HorizontalDivider()
                    if (attachments.isEmpty()) Text("No receipts attached to this service.")
                    attachments.forEach { attachment ->
                        ReceiptRow(
                            attachment = attachment,
                            onPreview = {
                                scope.launch {
                                    try {
                                        val file = receiptRepository.getVerifiedFile(attachment.id)
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, attachment.mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                                    } catch (failure: ActivityNotFoundException) {
                                        error = "No application is available to preview this file. Use Download instead."
                                    } catch (failure: Exception) {
                                        error = failure.message ?: "Unable to open receipt."
                                    }
                                }
                            },
                            onDownload = { pendingDownload = attachment; createDocument.launch(attachment.originalFileName) },
                            onDelete = { deleteTarget = attachment }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Done") } }
    )

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Remove receipt?") },
            text = { Text("${target.originalFileName} will be removed. The oil change and its other receipts will remain.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        try {
                            receiptRepository.delete(target.id)
                            deleteTarget = null
                        } catch (failure: Exception) {
                            error = failure.message ?: "Unable to remove receipt."
                        } finally {
                            busy = false
                        }
                    }
                }, enabled = !busy) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }, enabled = !busy) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ReceiptRow(
    attachment: ReceiptAttachmentEntity,
    onPreview: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(attachment.originalFileName, style = MaterialTheme.typography.bodyLarge)
        Text("${attachment.mimeType} · ${attachment.sizeBytes} bytes · ${DateFormat.getDateTimeInstance().format(Date(attachment.addedAtUtc))}", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onPreview) { Text("Preview") }
            TextButton(onClick = onDownload) { Text("Download") }
            TextButton(onClick = onDelete) { Text("Remove") }
        }
        HorizontalDivider()
    }
}

private fun queryDisplayName(context: android.content.Context, uri: android.net.Uri): String {
    val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
    cursor?.use { if (it.moveToFirst()) return it.getString(0).orEmpty().ifBlank { "receipt" } }
    return uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "receipt" } ?: "receipt"
}
