package ca.gllavoie.vehicletracker.ui.lookup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.gllavoie.vehicletracker.data.local.SpecificationSourceEntity
import ca.gllavoie.vehicletracker.data.local.VehicleSpecificationEntity
import ca.gllavoie.vehicletracker.data.repository.VehicleCatalogOptions
import ca.gllavoie.vehicletracker.data.repository.VehicleCatalogSeeder
import ca.gllavoie.vehicletracker.data.repository.VehicleSpecificationRepository
import org.json.JSONObject
import java.text.NumberFormat

@Composable
fun VehicleLookupScreen(repository: VehicleSpecificationRepository) {
    val context = LocalContext.current
    val entries by repository.observeSpecificationsForDataset(VehicleCatalogSeeder.CATALOG_ID_PREFIX)
        .collectAsStateWithLifecycle(emptyList())
    var selectedYear by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedMake by rememberSaveable { mutableStateOf("") }
    var selectedModel by rememberSaveable { mutableStateOf("") }
    var selectedEngine by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var loadError by rememberSaveable { mutableStateOf<String?>(null) }
    var seedAttempted by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(repository) {
        runCatching { VehicleCatalogSeeder(context.applicationContext, repository).seedIfMissing() }
            .onFailure { loadError = it.message ?: "Vehicle catalog could not be loaded." }
        seedAttempted = true
    }

    val yearOptions = remember(entries) { VehicleCatalogOptions.modelYears(entries) }
    val makeOptions = remember(entries, selectedYear) {
        selectedYear?.let { VehicleCatalogOptions.makesForYear(entries, it) }.orEmpty()
    }
    val modelOptions = remember(entries, selectedYear, selectedMake) {
        selectedYear?.takeIf { selectedMake.isNotBlank() }
            ?.let { VehicleCatalogOptions.modelsForYearAndMake(entries, it, selectedMake) }.orEmpty()
    }
    val engineOptions = remember(entries, selectedYear, selectedMake, selectedModel) {
        if (selectedYear != null && selectedMake.isNotBlank() && selectedModel.isNotBlank()) {
            VehicleCatalogOptions.configurationsForVehicle(entries, selectedYear!!, selectedMake, selectedModel)
        } else emptyList()
    }
    val selectedEntry = entries.firstOrNull { it.id == selectedId }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Look up a vehicle without adding it to your garage. Select year, make, model, and engine/configuration to inspect the catalog entry.", style = MaterialTheme.typography.bodyLarge)
        loadError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }
        LookupDropdownField(
            label = "Year",
            value = selectedYear?.toString().orEmpty(),
            options = yearOptions,
            optionLabel = { it.toString() },
            enabled = entries.isNotEmpty(),
            onSelect = {
                selectedYear = it
                selectedMake = ""
                selectedModel = ""
                selectedEngine = ""
                selectedId = null
                showDetails = false
            }
        )
        if (selectedYear != null && makeOptions.isEmpty()) Text("No makes are listed for this year.", style = MaterialTheme.typography.bodySmall)
        LookupDropdownField(
            label = "Make",
            value = selectedMake,
            options = makeOptions,
            optionLabel = { it },
            enabled = selectedYear != null && makeOptions.isNotEmpty(),
            onSelect = {
                selectedMake = it
                selectedModel = ""
                selectedEngine = ""
                selectedId = null
                showDetails = false
            }
        )
        if (selectedMake.isNotBlank() && modelOptions.isEmpty()) Text("No models are listed for this make and year.", style = MaterialTheme.typography.bodySmall)
        LookupDropdownField(
            label = "Model",
            value = selectedModel,
            options = modelOptions,
            optionLabel = { it },
            enabled = selectedYear != null && selectedMake.isNotBlank() && modelOptions.isNotEmpty(),
            onSelect = {
                selectedModel = it
                selectedEngine = ""
                selectedId = null
                showDetails = false
            }
        )
        if (selectedModel.isNotBlank() && engineOptions.isEmpty()) Text("No engine/configuration entries are listed for this model.", style = MaterialTheme.typography.bodySmall)
        LookupDropdownField(
            label = "Engine / configuration",
            value = selectedEntry?.let { engineOptionLabel(it.copy(engine = selectedEngine.ifBlank { it.engine.orEmpty() })) }.orEmpty(),
            options = engineOptions,
            optionLabel = ::engineOptionLabel,
            enabled = selectedYear != null && selectedMake.isNotBlank() && selectedModel.isNotBlank() && engineOptions.isNotEmpty(),
            onSelect = {
                selectedId = it.id
                selectedEngine = it.engine.orEmpty()
                showDetails = false
            }
        )
        when {
            !seedAttempted && entries.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            entries.isEmpty() -> Text("No vehicle catalog entries are available.", style = MaterialTheme.typography.bodyMedium)
            selectedEntry != null -> {
                Text("Normal oil interval: ${normalIntervalLabel(selectedEntry)}", style = MaterialTheme.typography.bodyMedium)
                Text(statusLabel(selectedEntry.verificationStatus), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { showDetails = true }) { Text("Review data and source") }
            }
            else -> Text("Choose a catalog engine/configuration to review oil data and verification status. No garage record is created.", style = MaterialTheme.typography.bodyMedium)
        }
    }

    if (showDetails && selectedEntry != null) {
        VehicleLookupDetailsDialog(
            selectedEntry.copy(engine = selectedEngine.ifBlank { selectedEntry.engine.orEmpty() }),
            repository
        ) { showDetails = false }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun <T> LookupDropdownField(
    label: String,
    value: String,
    options: List<T>,
    optionLabel: (T) -> String,
    enabled: Boolean,
    onSelect: (T) -> Unit
) {
    var expanded by rememberSaveable(label) { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            placeholder = { Text("Select $label") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = enabled).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = { expanded = false; onSelect(option) }
                )
            }
        }
    }
}

private fun engineOptionLabel(entry: VehicleSpecificationEntity): String = listOfNotNull(
    entry.engine?.takeIf(String::isNotBlank),
    VehicleCatalogOptions.marketRegion(entry),
    entry.viscosityGrade?.let { "Oil $it" },
    normalIntervalLabel(entry).takeIf { it != "Not specified" }?.let { "Normal $it" },
    statusLabel(entry.verificationStatus)
).joinToString(" · ").ifBlank { "Engine/configuration not specified" }

private fun normalIntervalLabel(entry: VehicleSpecificationEntity): String = listOfNotNull(
    entry.distanceInterval?.let { "${NumberFormat.getIntegerInstance().format(it)} ${entry.distanceUnit.orEmpty().lowercase()}" },
    entry.timeIntervalMonths?.let { "$it months" }
).joinToString(" · ").ifBlank { "Not specified" }

@Composable
private fun VehicleLookupDetailsDialog(
    entry: VehicleSpecificationEntity,
    repository: VehicleSpecificationRepository,
    onDismiss: () -> Unit
) {
    var sources by remember(entry.id) { mutableStateOf<List<SpecificationSourceEntity>>(emptyList()) }
    LaunchedEffect(entry.id) { sources = runCatching { repository.getSources(entry.id) }.getOrDefault(emptyList()) }
    val source = sources.firstOrNull()
    val raw = remember(source?.notes) { parseCsvRow(source?.notes) }
    val uriHandler = LocalUriHandler.current
    val year = VehicleCatalogOptions.yearRange(entry)?.let { range ->
        if (range.first == range.last) range.first.toString() else "${range.first}–${range.last}"
    } ?: entry.modelYear?.toString().orEmpty()
    val normal = listOfNotNull(
        entry.distanceInterval?.let { "${NumberFormat.getIntegerInstance().format(it)} ${entry.distanceUnit.orEmpty().lowercase()}" },
        entry.timeIntervalMonths?.let { "$it months" }
    ).joinToString(" · ").ifBlank { noteValue(entry, "Raw normal-service interval") ?: "Not specified" }
    val severe = listOfNotNull(
        entry.severeDistanceInterval?.let { "${NumberFormat.getIntegerInstance().format(it)} ${entry.distanceUnit.orEmpty().lowercase()}" },
        entry.severeTimeIntervalMonths?.let { "$it months" }
    ).joinToString(" · ").ifBlank { noteValue(entry, "Raw severe-service interval") ?: "Not specified" }
    val verificationMessage = when (entry.verificationStatus) {
        VehicleCatalogSeeder.OFFICIAL_INTERVAL_RESEARCHED -> "An OEM interval source was found; the user must review and confirm it before scheduling can use it. Other catalog fields may still be unverified."
        VehicleCatalogSeeder.AI_GENERATED_UNVERIFIED -> "AI-generated and not manufacturer-verified. Check the exact year, market and engine against the owner's manual. This lookup does not verify the data."
        "USER_VERIFIED" -> "Marked verified by the user. Always check that the source applies to this exact vehicle."
        else -> "Catalog data is not verified. Check the owner's manual before relying on it."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$year ${entry.make} ${entry.model}") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(verificationMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Text("${statusLabel(entry.verificationStatus)} · ${VehicleCatalogOptions.marketRegion(entry) ?: "Market not specified"}", style = MaterialTheme.typography.labelLarge)
                detail("Engine / configuration", entry.engine ?: raw.optString("engine_trim"))
                detail("Engine code", entry.engineCode ?: raw.optString("oem_engine_code"))
                detail("Engine displacement", raw.optString("engine_displacement_l").takeIf(String::isNotBlank)?.let { "$it L" })
                detail("Cylinders", raw.optString("cylinder_config"))
                detail("Induction", raw.optString("induction_type"))
                detail("Fuel type", raw.optString("fuel_type"))
                HorizontalDivider()
                Text("Oil service", style = MaterialTheme.typography.titleSmall)
                detail("Recommended oil", raw.optString("oil_recommendation").takeIf(String::isNotBlank) ?: entry.viscosityGrade)
                detail("Viscosity", entry.viscosityGrade)
                detail("API service category", raw.optString("api_service_category"))
                detail("Oil type required", raw.optString("oil_type_required"))
                detail("OEM approval", entry.manufacturerApproval ?: raw.optString("oem_approval_spec"))
                detail("Filter", entry.oilFilterPartNumber ?: raw.optString("filter_description"))
                detail("Oil capacity", capacityText(raw))
                detail("Normal-service interval", normal)
                detail("Severe-service interval", severe)
                detail("Oil-life monitor", raw.optString("uses_oil_life_monitor"))
                detail("Oil reset procedure", noteValue(entry, "Oil reset procedure") ?: raw.optString("oil_reset_procedure"))
                detail("Drain plug torque", raw.optString("drain_plug_torque_ft_lbs").takeIf(String::isNotBlank)?.let { "$it ft-lb" })
                detail("Drain plug socket", raw.optString("drain_plug_socket_mm").takeIf(String::isNotBlank)?.let { "$it mm" })
                detail("Filter wrench size", raw.optString("filter_wrench_size"))
                if (source != null) {
                    HorizontalDivider()
                    Text("Source / provenance", style = MaterialTheme.typography.titleSmall)
                    Text(source.sourceName, style = MaterialTheme.typography.bodyMedium)
                    source.sourceUrl?.takeIf(String::isNotBlank)?.let { url ->
                        TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) { Text("Open source") }
                    }
                }
                Text("Searching does not add this vehicle to My Garage.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun detail(label: String, value: String?) {
    value?.takeIf(String::isNotBlank)?.let {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$label:", style = MaterialTheme.typography.labelMedium)
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun parseCsvRow(notes: String?): JSONObject {
    val value = notes.orEmpty().substringAfter("CSV row:", "").trim()
    return runCatching { JSONObject(value) }.getOrDefault(JSONObject())
}

private fun noteValue(entry: VehicleSpecificationEntity, label: String): String? = entry.serviceNotes.orEmpty().lineSequence()
    .firstOrNull { it.startsWith("$label:") }
    ?.substringAfter(":")
    ?.trim()
    ?.takeIf(String::isNotBlank)

private fun capacityText(row: JSONObject): String? = listOfNotNull(
    row.optString("oil_capacity_liters").takeIf(String::isNotBlank)?.let { "$it L" },
    row.optString("oil_capacity_quarts").takeIf(String::isNotBlank)?.let { "$it qt" }
).joinToString(" / ").takeIf(String::isNotBlank)

private fun statusLabel(status: String): String = when (status) {
    VehicleCatalogSeeder.AI_GENERATED_UNVERIFIED -> "AI-generated · unverified"
    VehicleCatalogSeeder.OFFICIAL_INTERVAL_RESEARCHED -> "Interval source found · confirm"
    "USER_VERIFIED" -> "User verified"
    else -> "Unverified"
}
