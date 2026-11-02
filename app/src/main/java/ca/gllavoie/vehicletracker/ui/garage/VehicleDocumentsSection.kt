package ca.gllavoie.vehicletracker.ui.garage

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.gllavoie.vehicletracker.data.local.ReceiptAttachmentEntity
import ca.gllavoie.vehicletracker.data.local.VehicleEntity
import ca.gllavoie.vehicletracker.data.repository.ReceiptRepository
import ca.gllavoie.vehicletracker.domain.document.VehicleDocumentType
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun VehicleDocumentsSection(
    vehicle: VehicleEntity,
    repository: ReceiptRepository,
    enabled: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val documents by repository.observeVehicleDocuments(vehicle.id).collectAsStateWithLifecycle(emptyList())
    var selectedType by rememberSaveable(vehicle.id) { mutableStateOf(VehicleDocumentType.INSURANCE.storedValue) }
    var showAddDialog by rememberSaveable(vehicle.id) { mutableStateOf(false) }
    var busy by rememberSaveable(vehicle.id) { mutableStateOf(false) }
    var error by rememberSaveable(vehicle.id) { mutableStateOf<String?>(null) }
    var message by rememberSaveable(vehicle.id) { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<ReceiptAttachmentEntity?>(null) }
    var cameraCapturePath by rememberSaveable(vehicle.id) { mutableStateOf<String?>(null) }

    fun saveDocument(uri: Uri, fallbackName: String, temporaryFile: File? = null) {
        scope.launch {
            busy = true
            error = null
            message = null
            try {
                val displayName = queryDisplayName(context, uri, fallbackName)
                repository.addForVehicle(vehicle.id, selectedType, uri, displayName)
                message = "${VehicleDocumentType.fromStoredValue(selectedType).label} document saved."
            } catch (failure: Exception) {
                error = failure.message ?: "Unable to save this document."
            } finally {
                temporaryFile?.delete()
                busy = false
            }
        }
    }

    val chooseImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) saveDocument(uri, "vehicle-image")
    }
    val choosePdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) saveDocument(uri, "vehicle-document.pdf")
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val path = cameraCapturePath
        cameraCapturePath = null
        if (path != null) {
            val file = File(path)
            if (saved) {
                runCatching {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    saveDocument(uri, "Vehicle-photo.jpg", temporaryFile = file)
                }.onFailure { failure ->
                    file.delete()
                    error = failure.message ?: "Unable to open the captured photo."
                }
            } else {
                file.delete()
            }
        }
    }

    fun launchCamera() {
        try {
            val directory = File(context.filesDir, "receipts")
            require(directory.mkdirs() || directory.isDirectory) { "Document storage is not available." }
            val file = File(directory, ".capture-${UUID.randomUUID()}.jpg")
            require(file.createNewFile()) { "A photo capture file could not be created." }
            cameraCapturePath = file.absolutePath
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            showAddDialog = false
            takePhoto.launch(uri)
        } catch (failure: Exception) {
            cameraCapturePath?.let { File(it).delete() }
            cameraCapturePath = null
            error = failure.message ?: "Unable to start the camera."
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Vehicle documents", style = MaterialTheme.typography.titleMedium)
        Text("Insurance, service records and other files · PDF or common image formats (JPG, PNG, WebP, GIF, HEIC/HEIF, AVIF) · up to 10 MB each · stored privately on this device.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { showAddDialog = true; error = null }, enabled = enabled && !busy) {
            if (busy) CircularProgressIndicator(Modifier.semantics { contentDescription = "Saving vehicle document" }) else Text("Add vehicle document")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        if (documents.isEmpty()) Text("No vehicle documents saved.", style = MaterialTheme.typography.bodySmall)
        documents.forEach { document ->
            VehicleDocumentRow(
                attachment = document,
                onOpen = {
                    scope.launch {
                        error = null
                        try {
                            val file = repository.getVerifiedFile(document.id)
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, document.mimeType)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            context.startActivity(Intent.createChooser(intent, "Open vehicle document"))
                        } catch (_: ActivityNotFoundException) {
                            error = "No application is available to open this file."
                        } catch (failure: Exception) {
                            error = failure.message ?: "Unable to open this document."
                        }
                    }
                },
                onDelete = { deleteTarget = document },
                enabled = enabled && !busy
            )
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { if (!busy) showAddDialog = false },
            title = { Text("Add vehicle document") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Choose a category, then take a picture or select a file.", style = MaterialTheme.typography.bodyMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(VehicleDocumentType.vehicleDocumentTypes, key = { it.storedValue }) { type ->
                            FilterChip(
                                selected = selectedType == type.storedValue,
                                onClick = { selectedType = type.storedValue },
                                enabled = !busy,
                                label = { Text(type.label) }
                            )
                        }
                    }
                    TextButton(onClick = { launchCamera() }, enabled = !busy) { Text("Take photo") }
                    TextButton(
                        onClick = {
                            showAddDialog = false
                            chooseImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        enabled = !busy
                    ) { Text("Choose image") }
                    TextButton(
                        onClick = {
                            showAddDialog = false
                            choosePdf.launch(arrayOf("application/pdf"))
                        },
                        enabled = !busy
                    ) { Text("Choose PDF") }
                }
            },
            confirmButton = { TextButton(onClick = { showAddDialog = false }, enabled = !busy) { Text("Cancel") } }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!busy) deleteTarget = null },
            title = { Text("Remove vehicle document?") },
            text = { Text("${target.originalFileName} will be removed from this vehicle. This does not change service records.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        try {
                            repository.delete(target.id)
                            deleteTarget = null
                        } catch (failure: Exception) {
                            error = failure.message ?: "Unable to remove this document."
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
private fun VehicleDocumentRow(
    attachment: ReceiptAttachmentEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    enabled: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("${VehicleDocumentType.fromStoredValue(attachment.documentType).label}: ${attachment.originalFileName}", style = MaterialTheme.typography.bodyLarge)
        Text("${attachment.mimeType} · ${attachment.sizeBytes} bytes · ${DateFormat.getDateTimeInstance().format(Date(attachment.addedAtUtc))}", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onOpen) { Text("Open") }
            TextButton(onClick = onDelete, enabled = enabled) { Text("Remove") }
        }
        HorizontalDivider()
    }
}

private fun queryDisplayName(context: android.content.Context, uri: Uri, fallback: String): String {
    val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
    cursor?.use { if (it.moveToFirst()) return it.getString(0).orEmpty().ifBlank { fallback } }
    return uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { fallback } ?: fallback
}
