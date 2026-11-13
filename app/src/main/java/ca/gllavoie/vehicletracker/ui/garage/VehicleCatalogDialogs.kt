package ca.gllavoie.vehicletracker.ui.garage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ca.gllavoie.vehicletracker.R
import ca.gllavoie.vehicletracker.data.local.SpecificationSourceEntity
import ca.gllavoie.vehicletracker.data.local.VehicleSpecificationEntity
import ca.gllavoie.vehicletracker.data.repository.VehicleCatalogSeeder
import ca.gllavoie.vehicletracker.data.repository.VehicleSpecificationRepository
import java.math.BigDecimal
import java.text.NumberFormat
import ca.gllavoie.vehicletracker.preferences.MeasurementSystemPreferences
import ca.gllavoie.vehicletracker.preferences.convertDistanceInterval
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun VehicleCatalogPickerDialog(
    entries: List<VehicleSpecificationEntity>,
    isLoading: Boolean,
    repository: VehicleSpecificationRepository,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val unableToLoadCatalog = stringResource(R.string.unable_to_load_catalog)
    val loadingVehicleCatalogCd = stringResource(R.string.loading_vehicle_catalog)
    var query by rememberSaveable { mutableStateOf("") }
    var editingEntry by remember { mutableStateOf<VehicleSpecificationEntity?>(null) }
    var loading by remember { mutableStateOf(isLoading || entries.isEmpty()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (entries.isEmpty()) {
            loading = true
            try {
                VehicleCatalogSeeder(context.applicationContext, repository).seedIfMissing()
            } catch (failure: Exception) {
                loadError = failure.message ?: unableToLoadCatalog
            } finally {
                loading = false
            }
        } else loading = false
    }
    LaunchedEffect(entries.isEmpty()) { if (entries.isNotEmpty()) loading = false }
    val searchableText = remember(entries) {
        entries.map { entry ->
            listOf(entry.make, entry.model, entry.engine.orEmpty(), entry.trim.orEmpty(), entry.serviceNotes.orEmpty())
                .joinToString(" ")
                .lowercase()
        }
    }
    val filtered = remember(entries, query, searchableText) {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) entries
        else entries.filterIndexed { index, _ -> searchableText[index].contains(needle) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.modify_vehicle_catalog_title)) },
        text = {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val availableHeight = if (maxHeight == Dp.Infinity) 520.dp else (maxHeight - 70.dp).coerceAtLeast(200.dp)
                Column(Modifier.fillMaxWidth().heightIn(max = availableHeight), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text(stringResource(R.string.catalog_search_label)) }, singleLine = true)
                    loadError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }
                    if (loading) {
                        CircularProgressIndicator(Modifier.semantics { contentDescription = loadingVehicleCatalogCd })
                    } else if (filtered.isEmpty()) {
                        Text(stringResource(R.string.no_matching_catalog_entry))
                    } else {
                        Text(stringResource(R.string.catalog_picker_hint), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.catalog_config_count_format, filtered.size), style = MaterialTheme.typography.labelMedium)
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(filtered, key = VehicleSpecificationEntity::id) { entry ->
                                CatalogRow(
                                    entry = entry,
                                    onEdit = { editingEntry = entry }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } }
    )
    editingEntry?.let { entry ->
        VehicleCatalogEditorDialog(
            entry = entry,
            repository = repository,
            onDismiss = { editingEntry = null },
            onSaved = { editingEntry = null }
        )
    }
}

@Composable
private fun CatalogRow(
    entry: VehicleSpecificationEntity,
    onEdit: () -> Unit
) {
    val context = LocalContext.current
    val measurementSystem = remember { MeasurementSystemPreferences.get(context) }
    val intervalUnit = stringResource(if (measurementSystem.distanceUnit == "MILE") R.string.unit_mi else R.string.unit_km)
    val yearRange = rawField(entry.serviceNotes, "Years") ?: entry.modelYear?.toString() ?: stringResource(R.string.year_not_specified)
    val market = rawField(entry.serviceNotes, "Market / Region")
    val rawOil = rawField(entry.serviceNotes, "Raw oil recommendation")
    val normalDistance = entry.distanceInterval?.let { distance ->
        convertDistanceInterval(distance, entry.distanceUnit, measurementSystem)
    }?.let { stringResource(R.string.two_part_format, NumberFormat.getIntegerInstance().format(it), intervalUnit) }
    val normalMonths = entry.timeIntervalMonths?.let { stringResource(R.string.months_format, it) }
    val normalInterval = listOfNotNull(normalDistance, normalMonths)
        .joinToString(" · ").ifBlank { rawField(entry.serviceNotes, "Raw normal-service interval") ?: stringResource(R.string.not_specified) }
    val severeDistance = entry.severeDistanceInterval?.let { distance ->
        convertDistanceInterval(distance, entry.distanceUnit, measurementSystem)
    }?.let { stringResource(R.string.two_part_format, NumberFormat.getIntegerInstance().format(it), intervalUnit) }
    val severeMonths = entry.severeTimeIntervalMonths?.let { stringResource(R.string.months_format, it) }
    val severeInterval = listOfNotNull(severeDistance, severeMonths)
        .joinToString(" · ").ifBlank { rawField(entry.serviceNotes, "Raw severe-service interval") ?: stringResource(R.string.not_specified) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.dot_three_part_format, yearRange, entry.make, entry.model), style = MaterialTheme.typography.titleSmall)
        entry.engine?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        market?.let { Text(stringResource(R.string.market_format, it), style = MaterialTheme.typography.bodySmall) }
        Text(stringResource(R.string.normal_interval_format, normalInterval), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.severe_interval_format, severeInterval), style = MaterialTheme.typography.bodySmall)
        rawOil?.let { Text(stringResource(R.string.catalog_oil_text_format, it), style = MaterialTheme.typography.bodySmall) }
        val verifiedOn = entry.verifiedOn ?: stringResource(R.string.date_not_recorded)
        val status = when (entry.verificationStatus) {
            "USER_VERIFIED" -> stringResource(R.string.user_marked_verified_format, verifiedOn)
            "OFFICIAL_INTERVAL_RESEARCHED" -> stringResource(R.string.oem_interval_source_format, verifiedOn)
            "USER_ENTERED_UNVERIFIED" -> stringResource(R.string.user_edited_unverified)
            else -> stringResource(R.string.unverified_catalog_data)
        }
        val statusColor = when (entry.verificationStatus) {
            "USER_VERIFIED" -> MaterialTheme.colorScheme.primary
            "OFFICIAL_INTERVAL_RESEARCHED" -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.error
        }
        Text(status, color = statusColor, style = MaterialTheme.typography.labelSmall)
        TextButton(onClick = onEdit) { Text(stringResource(R.string.modify_data)) }
        HorizontalDivider()
    }
}

@Composable
private fun VehicleCatalogEditorDialog(
    entry: VehicleSpecificationEntity,
    repository: VehicleSpecificationRepository,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val unableToLoadSource = stringResource(R.string.unable_to_load_catalog_source)
    val unableToSaveCatalogEntry = stringResource(R.string.unable_to_save_catalog_entry)
    val savingCatalogEntryCd = stringResource(R.string.saving_catalog_entry_cd)
    val userEnteredSource = stringResource(R.string.user_entered_source)
    val sourceNoteUserVerified = stringResource(R.string.source_note_user_verified)
    val sourceNoteOem = stringResource(R.string.source_note_oem)
    val sourceNoteUnverified = stringResource(R.string.source_note_unverified)
    var make by rememberSaveable(entry.id) { mutableStateOf(entry.make) }
    var model by rememberSaveable(entry.id) { mutableStateOf(entry.model) }
    var modelYear by rememberSaveable(entry.id) { mutableStateOf(entry.modelYear?.toString().orEmpty()) }
    var engine by rememberSaveable(entry.id) { mutableStateOf(entry.engine.orEmpty()) }
    var trim by rememberSaveable(entry.id) { mutableStateOf(entry.trim.orEmpty()) }
    var viscosity by rememberSaveable(entry.id) { mutableStateOf(entry.viscosityGrade.orEmpty()) }
    var approval by rememberSaveable(entry.id) { mutableStateOf(entry.manufacturerApproval.orEmpty()) }
    var capacityLitres by rememberSaveable(entry.id) { mutableStateOf(entry.capacityWithFilterMl?.let { BigDecimal(it).divide(BigDecimal(1_000)).stripTrailingZeros().toPlainString() }.orEmpty()) }
    var interval by rememberSaveable(entry.id) { mutableStateOf(entry.distanceInterval?.toString().orEmpty()) }
    var intervalUnit by rememberSaveable(entry.id) { mutableStateOf(entry.distanceUnit ?: "MILE") }
    var months by rememberSaveable(entry.id) { mutableStateOf(entry.timeIntervalMonths?.toString().orEmpty()) }
    var severeDistance by rememberSaveable(entry.id) { mutableStateOf(entry.severeDistanceInterval?.toString().orEmpty()) }
    var severeMonths by rememberSaveable(entry.id) { mutableStateOf(entry.severeTimeIntervalMonths?.toString().orEmpty()) }
    var filterPart by rememberSaveable(entry.id) { mutableStateOf(entry.oilFilterPartNumber.orEmpty()) }
    var notes by rememberSaveable(entry.id) { mutableStateOf(editableCatalogNotes(entry.serviceNotes)) }
    var sourceName by rememberSaveable(entry.id) { mutableStateOf("") }
    var sourceUrl by rememberSaveable(entry.id) { mutableStateOf("") }
    var verifiedOn by rememberSaveable(entry.id) { mutableStateOf("") }
    var markVerified by rememberSaveable(entry.id) { mutableStateOf(false) }
    var busy by rememberSaveable(entry.id) { mutableStateOf(false) }
    var error by rememberSaveable(entry.id) { mutableStateOf<String?>(null) }
    var loadedSource by remember(entry.id) { mutableStateOf(false) }

    LaunchedEffect(entry.id) {
        if (!loadedSource) {
            try {
                val sources = repository.getSources(entry.id)
                val source = sources.firstOrNull { it.verificationStatus == "USER_VERIFIED" }
                    ?: sources.firstOrNull { it.verificationStatus == "OFFICIAL_INTERVAL_RESEARCHED" }
                    ?: sources.firstOrNull { it.verificationStatus != "AI_GENERATED_UNVERIFIED" }
                source?.let {
                    sourceName = it.sourceName
                    sourceUrl = it.sourceUrl.orEmpty()
                    verifiedOn = if (it.verificationStatus == "USER_VERIFIED") it.verifiedOn.orEmpty() else ""
                    markVerified = it.verificationStatus == "USER_VERIFIED"
                }
            } catch (failure: Exception) {
                error = failure.message ?: unableToLoadSource
            } finally {
                loadedSource = true
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.modify_catalog_configuration_title)) },
        text = {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val availableHeight = if (maxHeight == Dp.Infinity) 520.dp else (maxHeight - 70.dp).coerceAtLeast(200.dp)
                Column(Modifier.fillMaxWidth().heightIn(max = availableHeight).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.original_source_retained), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = make, onValueChange = { make = it }, label = { Text(stringResource(R.string.make_label)) }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text(stringResource(R.string.model_label)) }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = modelYear, onValueChange = { modelYear = it.filter(Char::isDigit).take(4) }, label = { Text(stringResource(R.string.exact_model_year_label)) }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = engine, onValueChange = { engine = it }, label = { Text(stringResource(R.string.engine_label)) }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = trim, onValueChange = { trim = it }, label = { Text(stringResource(R.string.trim_label)) }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = viscosity, onValueChange = { viscosity = it }, label = { Text(stringResource(R.string.oil_viscosity_grade_label)) }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = approval, onValueChange = { approval = it }, label = { Text(stringResource(R.string.required_manufacturer_approval_label)) }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = capacityLitres, onValueChange = { capacityLitres = it }, label = { Text(stringResource(R.string.capacity_with_filter_litres_label)) }, singleLine = true, enabled = !busy)
                    HorizontalDivider()
                    Text(stringResource(R.string.oil_change_interval), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.normal_service), style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(value = interval, onValueChange = { interval = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.distance_interval_label)) }, singleLine = true, enabled = !busy)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { intervalUnit = "KM" }, enabled = !busy) { Text(stringResource(if (intervalUnit == "KM") R.string.unit_km_checked else R.string.unit_km_short)) }
                        TextButton(onClick = { intervalUnit = "MILE" }, enabled = !busy) { Text(stringResource(if (intervalUnit == "MILE") R.string.unit_mile_checked else R.string.unit_mile_short)) }
                    }
                    OutlinedTextField(value = months, onValueChange = { months = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.time_interval_months_label)) }, singleLine = true, enabled = !busy)
                    Text(stringResource(R.string.severe_service), style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(value = severeDistance, onValueChange = { severeDistance = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.distance_interval_label)) }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = severeMonths, onValueChange = { severeMonths = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.time_interval_months_label)) }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = filterPart, onValueChange = { filterPart = it }, label = { Text(stringResource(R.string.oil_filter_part_number_label)) }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(stringResource(R.string.raw_catalog_notes_label)) }, minLines = 3, enabled = !busy)
                    HorizontalDivider()
                    if (entry.verificationStatus == "OFFICIAL_INTERVAL_RESEARCHED") {
                        Text(stringResource(R.string.oem_source_researched_warning), style = MaterialTheme.typography.bodySmall)
                    }
                    Row {
                        Checkbox(checked = markVerified, onCheckedChange = { markVerified = it }, enabled = !busy)
                        Text(stringResource(R.string.checked_official_documentation), modifier = Modifier.padding(top = 12.dp))
                    }
                    OutlinedTextField(value = sourceName, onValueChange = { sourceName = it }, label = { Text(stringResource(R.string.source_name_label)) }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = sourceUrl, onValueChange = { sourceUrl = it }, label = { Text(stringResource(R.string.official_source_url_label)) }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = verifiedOn, onValueChange = { verifiedOn = it.take(10) }, label = { Text(stringResource(R.string.date_verified_label)) }, singleLine = true, enabled = !busy)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val year = modelYear.takeIf(String::isNotBlank)?.toIntOrNull()
                val capacityMl = capacityLitres.takeIf(String::isNotBlank)?.let { raw ->
                    runCatching { BigDecimal(raw).movePointRight(3).intValueExact() }.getOrElse { -1 }
                }
                val distanceInterval = interval.toOptionalInt()
                val timeInterval = months.toOptionalInt()
                val severeDistanceInterval = severeDistance.toOptionalInt()
                val severeTimeInterval = severeMonths.toOptionalInt()
                val officialIntervalUnchanged = entry.verificationStatus == "OFFICIAL_INTERVAL_RESEARCHED" &&
                    make.trim() == entry.make && model.trim() == entry.model && year == entry.modelYear &&
                    engine.trim().ifEmpty { null } == entry.engine && trim.trim().ifEmpty { null } == entry.trim &&
                    viscosity.trim().ifEmpty { null } == entry.viscosityGrade && distanceInterval == entry.distanceInterval &&
                    (distanceInterval?.let { intervalUnit } == entry.distanceUnit) && timeInterval == entry.timeIntervalMonths &&
                    severeDistanceInterval == entry.severeDistanceInterval && severeTimeInterval == entry.severeTimeIntervalMonths
                val verificationStatus = when {
                    markVerified -> "USER_VERIFIED"
                    officialIntervalUnchanged -> "OFFICIAL_INTERVAL_RESEARCHED"
                    else -> "USER_ENTERED_UNVERIFIED"
                }
                val spec = entry.copy(
                    make = make.trim(), model = model.trim(), modelYear = year,
                    engine = engine.trim().ifEmpty { null }, trim = trim.trim().ifEmpty { null },
                    viscosityGrade = viscosity.trim().ifEmpty { null },
                    manufacturerApproval = approval.trim().ifEmpty { null },
                    capacityWithFilterMl = capacityMl,
                    distanceInterval = distanceInterval,
                    distanceUnit = distanceInterval?.let { intervalUnit },
                    timeIntervalMonths = timeInterval,
                    severeDistanceInterval = severeDistanceInterval,
                    severeTimeIntervalMonths = severeTimeInterval,
                    oilFilterPartNumber = filterPart.trim().ifEmpty { null },
                    serviceNotes = notes.trim().ifEmpty { null },
                    verificationStatus = verificationStatus,
                    verifiedOn = when (verificationStatus) {
                        "USER_VERIFIED" -> verifiedOn.trim()
                        "OFFICIAL_INTERVAL_RESEARCHED" -> entry.verifiedOn
                        else -> null
                    },
                    updatedAtUtc = System.currentTimeMillis()
                )
                val source = if (sourceName.isNotBlank() || sourceUrl.isNotBlank() || markVerified || verificationStatus == "OFFICIAL_INTERVAL_RESEARCHED") {
                    SpecificationSourceEntity(
                        id = if (verificationStatus == "OFFICIAL_INTERVAL_RESEARCHED") "official-source-${entry.id}" else "user-source-${entry.id}",
                        specificationId = entry.id,
                        sourceName = sourceName.trim().ifEmpty { userEnteredSource },
                        sourceUrl = sourceUrl.trim().ifEmpty { null },
                        verifiedOn = when (verificationStatus) {
                            "USER_VERIFIED" -> verifiedOn.trim()
                            "OFFICIAL_INTERVAL_RESEARCHED" -> entry.verifiedOn
                            else -> null
                        },
                        verificationStatus = spec.verificationStatus,
                        notes = when (verificationStatus) {
                            "USER_VERIFIED" -> sourceNoteUserVerified
                            "OFFICIAL_INTERVAL_RESEARCHED" -> sourceNoteOem
                            else -> sourceNoteUnverified
                        }
                    )
                } else null
                busy = true
                error = null
                scope.launch {
                    try {
                        repository.saveCatalogEntry(spec, source)
                        onSaved()
                    } catch (failure: Exception) {
                        error = failure.message ?: unableToSaveCatalogEntry
                    } finally {
                        busy = false
                    }
                }
            }, enabled = !busy) {
                if (busy) CircularProgressIndicator(Modifier.semantics { contentDescription = savingCatalogEntryCd }) else Text(stringResource(R.string.save_changes))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) } }
    )
}

private fun rawField(notes: String?, name: String): String? =
    notes?.lineSequence()?.firstOrNull { it.startsWith("$name:") }?.substringAfter(":")?.trim()?.ifEmpty { null }

private fun editableCatalogNotes(notes: String?): String = notes.orEmpty().lineSequence()
    .filterNot { it.startsWith("AI-generated raw catalog row") || it.startsWith("This dataset is AI-generated") || it.startsWith("Catalog metric status:") }
    .joinToString("\n")
    .trim()

private fun String.toOptionalInt(): Int? = if (isBlank()) null else trim().toIntOrNull() ?: -1
