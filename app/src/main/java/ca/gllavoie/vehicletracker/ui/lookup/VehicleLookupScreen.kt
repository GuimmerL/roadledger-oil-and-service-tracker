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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.gllavoie.vehicletracker.R
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
    val entries by remember(repository) {
        repository.observeSpecificationsForDataset(VehicleCatalogSeeder.CATALOG_ID_PREFIX)
    }.collectAsStateWithLifecycle(emptyList())
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
            .onFailure { loadError = it.message ?: context.getString(R.string.vehicle_catalog_load_error) }
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
        Text(stringResource(R.string.lookup_hint), style = MaterialTheme.typography.bodyLarge)
        loadError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }
        LookupDropdownField(
            label = stringResource(R.string.year_label),
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
        if (selectedYear != null && makeOptions.isEmpty()) Text(stringResource(R.string.no_makes_for_year), style = MaterialTheme.typography.bodySmall)
        LookupDropdownField(
            label = stringResource(R.string.make_label),
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
        if (selectedMake.isNotBlank() && modelOptions.isEmpty()) Text(stringResource(R.string.no_models_for_make_year), style = MaterialTheme.typography.bodySmall)
        LookupDropdownField(
            label = stringResource(R.string.model_label),
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
        if (selectedModel.isNotBlank() && engineOptions.isEmpty()) Text(stringResource(R.string.no_engine_configs_for_model), style = MaterialTheme.typography.bodySmall)
        LookupDropdownField(
            label = stringResource(R.string.engine_configuration_label),
            value = selectedEntry?.let { engineOptionLabel(it.copy(engine = selectedEngine.ifBlank { it.engine.orEmpty() })) }.orEmpty(),
            options = engineOptions,
            optionLabel = { engineOptionLabel(it) },
            enabled = selectedYear != null && selectedMake.isNotBlank() && selectedModel.isNotBlank() && engineOptions.isNotEmpty(),
            onSelect = {
                selectedId = it.id
                selectedEngine = it.engine.orEmpty()
                showDetails = false
            }
        )
        when {
            !seedAttempted && entries.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            entries.isEmpty() -> Text(stringResource(R.string.no_catalog_entries), style = MaterialTheme.typography.bodyMedium)
            selectedEntry != null -> {
                Text(stringResource(R.string.normal_oil_interval_format, normalIntervalLabel(selectedEntry)), style = MaterialTheme.typography.bodyMedium)
                Text(statusLabel(selectedEntry.verificationStatus), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { showDetails = true }) { Text(stringResource(R.string.review_data_and_source)) }
            }
            else -> Text(stringResource(R.string.lookup_choose_engine_hint), style = MaterialTheme.typography.bodyMedium)
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
    optionLabel: @Composable (T) -> String,
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
            placeholder = { Text(stringResource(R.string.select_label_format, label)) },
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

@Composable
private fun engineOptionLabel(entry: VehicleSpecificationEntity): String {
    val notSpecified = stringResource(R.string.not_specified)
    val normal = normalIntervalLabel(entry)
    return listOfNotNull(
        entry.engine?.takeIf(String::isNotBlank),
        VehicleCatalogOptions.marketRegion(entry),
        entry.viscosityGrade?.let { stringResource(R.string.oil_format, it) },
        normal.takeIf { it != notSpecified }?.let { stringResource(R.string.normal_format, it) },
        statusLabel(entry.verificationStatus)
    ).joinToString(" · ").ifBlank { stringResource(R.string.engine_configuration_not_specified) }
}

@Composable
private fun normalIntervalLabel(entry: VehicleSpecificationEntity): String {
    val distanceText = entry.distanceInterval?.let {
        stringResource(R.string.two_part_format, NumberFormat.getIntegerInstance().format(it), entry.distanceUnit.orEmpty().lowercase())
    }
    val monthsText = entry.timeIntervalMonths?.let { stringResource(R.string.months_format, it) }
    return listOfNotNull(distanceText, monthsText).joinToString(" · ").ifBlank { stringResource(R.string.not_specified) }
}

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
    val normalDistance = entry.distanceInterval?.let {
        stringResource(R.string.two_part_format, NumberFormat.getIntegerInstance().format(it), entry.distanceUnit.orEmpty().lowercase())
    }
    val normalMonths = entry.timeIntervalMonths?.let { stringResource(R.string.months_format, it) }
    val normal = listOfNotNull(normalDistance, normalMonths)
        .joinToString(" · ").ifBlank { noteValue(entry, "Raw normal-service interval") ?: stringResource(R.string.not_specified) }
    val severeDistance = entry.severeDistanceInterval?.let {
        stringResource(R.string.two_part_format, NumberFormat.getIntegerInstance().format(it), entry.distanceUnit.orEmpty().lowercase())
    }
    val severeMonths = entry.severeTimeIntervalMonths?.let { stringResource(R.string.months_format, it) }
    val severe = listOfNotNull(severeDistance, severeMonths)
        .joinToString(" · ").ifBlank { noteValue(entry, "Raw severe-service interval") ?: stringResource(R.string.not_specified) }
    val verificationMessage = when (entry.verificationStatus) {
        VehicleCatalogSeeder.OFFICIAL_INTERVAL_RESEARCHED -> stringResource(R.string.lookup_oem_source_message)
        VehicleCatalogSeeder.AI_GENERATED_UNVERIFIED -> stringResource(R.string.lookup_ai_message)
        "USER_VERIFIED" -> stringResource(R.string.lookup_user_verified_message)
        else -> stringResource(R.string.lookup_unverified_message)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lookup_title_format, year, entry.make, entry.model)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(verificationMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.lookup_status_market_format, statusLabel(entry.verificationStatus), VehicleCatalogOptions.marketRegion(entry) ?: stringResource(R.string.market_not_specified)), style = MaterialTheme.typography.labelLarge)
                detail(stringResource(R.string.engine_configuration_label), entry.engine ?: raw.optString("engine_trim"))
                detail(stringResource(R.string.engine_code_label), entry.engineCode ?: raw.optString("oem_engine_code"))
                detail(stringResource(R.string.engine_displacement_label), raw.optString("engine_displacement_l").takeIf(String::isNotBlank)?.let { stringResource(R.string.litre_format, it) })
                detail(stringResource(R.string.cylinders_label), raw.optString("cylinder_config"))
                detail(stringResource(R.string.induction_label), raw.optString("induction_type"))
                detail(stringResource(R.string.fuel_type_label), raw.optString("fuel_type"))
                HorizontalDivider()
                Text(stringResource(R.string.oil_service), style = MaterialTheme.typography.titleSmall)
                detail(stringResource(R.string.recommended_oil_label), raw.optString("oil_recommendation").takeIf(String::isNotBlank) ?: entry.viscosityGrade)
                detail(stringResource(R.string.viscosity_label), entry.viscosityGrade)
                detail(stringResource(R.string.api_service_category_label), raw.optString("api_service_category"))
                detail(stringResource(R.string.oil_type_required_label), raw.optString("oil_type_required"))
                detail(stringResource(R.string.oem_approval_label), entry.manufacturerApproval ?: raw.optString("oem_approval_spec"))
                detail(stringResource(R.string.filter_label), entry.oilFilterPartNumber ?: raw.optString("filter_description"))
                detail(stringResource(R.string.oil_capacity_label), capacityText(raw))
                detail(stringResource(R.string.normal_service_interval_label), normal)
                detail(stringResource(R.string.severe_service_interval_label), severe)
                detail(stringResource(R.string.oil_life_monitor_label), raw.optString("uses_oil_life_monitor"))
                detail(stringResource(R.string.oil_reset_procedure_label), noteValue(entry, "Oil reset procedure") ?: raw.optString("oil_reset_procedure"))
                detail(stringResource(R.string.drain_plug_torque_label), raw.optString("drain_plug_torque_ft_lbs").takeIf(String::isNotBlank)?.let { stringResource(R.string.ft_lb_format, it) })
                detail(stringResource(R.string.drain_plug_socket_label), raw.optString("drain_plug_socket_mm").takeIf(String::isNotBlank)?.let { stringResource(R.string.mm_format, it) })
                detail(stringResource(R.string.filter_wrench_size_label), raw.optString("filter_wrench_size"))
                if (source != null) {
                    HorizontalDivider()
                    Text(stringResource(R.string.source_provenance), style = MaterialTheme.typography.titleSmall)
                    Text(source.sourceName, style = MaterialTheme.typography.bodyMedium)
                    source.sourceUrl?.takeIf(String::isNotBlank)?.let { url ->
                        TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) { Text(stringResource(R.string.open_source)) }
                    }
                }
                Text(stringResource(R.string.lookup_no_garage_record), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}

@Composable
private fun detail(label: String, value: String?) {
    value?.takeIf(String::isNotBlank)?.let {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.detail_label_format, label), style = MaterialTheme.typography.labelMedium)
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

@Composable
private fun capacityText(row: JSONObject): String? {
    val litres = row.optString("oil_capacity_liters").takeIf(String::isNotBlank)?.let { stringResource(R.string.litre_format, it) }
    val quarts = row.optString("oil_capacity_quarts").takeIf(String::isNotBlank)?.let { stringResource(R.string.quart_format, it) }
    return listOfNotNull(litres, quarts).joinToString(" / ").takeIf(String::isNotBlank)
}

@Composable
private fun statusLabel(status: String): String = when (status) {
    VehicleCatalogSeeder.AI_GENERATED_UNVERIFIED -> stringResource(R.string.status_ai_unverified)
    VehicleCatalogSeeder.OFFICIAL_INTERVAL_RESEARCHED -> stringResource(R.string.status_interval_source_found)
    "USER_VERIFIED" -> stringResource(R.string.user_verified)
    else -> stringResource(R.string.unverified)
}
