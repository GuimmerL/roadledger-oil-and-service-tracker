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
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
                loadError = failure.message ?: "Unable to load vehicle catalog."
            } finally {
                loading = false
            }
        } else loading = false
    }
    LaunchedEffect(entries.isEmpty()) { if (entries.isNotEmpty()) loading = false }
    val filtered = remember(entries, query) {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) entries
        else entries.filter { entry ->
            listOf(entry.make, entry.model, entry.engine.orEmpty(), entry.trim.orEmpty(), entry.serviceNotes.orEmpty())
                .any { it.lowercase().contains(needle) }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modify vehicle catalog") },
        text = {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val availableHeight = if (maxHeight == Dp.Infinity) 520.dp else (maxHeight - 70.dp).coerceAtLeast(200.dp)
                Column(Modifier.fillMaxWidth().heightIn(max = availableHeight), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search make, model, year range, or engine") }, singleLine = true)
                    loadError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }
                    if (loading) {
                        CircularProgressIndicator(Modifier.semantics { contentDescription = "Loading vehicle catalog" })
                    } else if (filtered.isEmpty()) {
                        Text("No matching catalog entry. Close this screen and use Other in the vehicle dropdowns to enter it manually.")
                    } else {
                        Text("Select a car using the ordered dropdowns on the vehicle form. This screen only edits catalog entries.", style = MaterialTheme.typography.bodySmall)
                        Text("${filtered.size} configurations · AI rows remain unverified; official interval checks are labeled", style = MaterialTheme.typography.labelMedium)
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
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
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
    val intervalUnit = if (measurementSystem.distanceUnit == "MILE") "mi" else "km"
    val yearRange = rawField(entry.serviceNotes, "Years") ?: entry.modelYear?.toString() ?: "Year not specified"
    val market = rawField(entry.serviceNotes, "Market / Region")
    val rawOil = rawField(entry.serviceNotes, "Raw oil recommendation")
    val normalInterval = buildList {
        entry.distanceInterval?.let { distance ->
            convertDistanceInterval(distance, entry.distanceUnit, measurementSystem)?.let {
                add("${NumberFormat.getIntegerInstance().format(it)} $intervalUnit")
            }
        }
        entry.timeIntervalMonths?.let { add("$it months") }
    }.joinToString(" · ").ifBlank { rawField(entry.serviceNotes, "Raw normal-service interval") ?: "Not specified" }
    val severeInterval = buildList {
        entry.severeDistanceInterval?.let { distance ->
            convertDistanceInterval(distance, entry.distanceUnit, measurementSystem)?.let {
                add("${NumberFormat.getIntegerInstance().format(it)} $intervalUnit")
            }
        }
        entry.severeTimeIntervalMonths?.let { add("$it months") }
    }.joinToString(" · ").ifBlank { rawField(entry.serviceNotes, "Raw severe-service interval") ?: "Not specified" }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("$yearRange · ${entry.make} ${entry.model}", style = MaterialTheme.typography.titleSmall)
        entry.engine?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        market?.let { Text("Market: $it", style = MaterialTheme.typography.bodySmall) }
        Text("Normal interval: $normalInterval", style = MaterialTheme.typography.bodySmall)
        Text("Severe / heavy-use interval: $severeInterval", style = MaterialTheme.typography.bodySmall)
        rawOil?.let { Text("Catalog oil text: $it", style = MaterialTheme.typography.bodySmall) }
        val status = when (entry.verificationStatus) {
            "USER_VERIFIED" -> "User-marked verified · ${entry.verifiedOn ?: "date not recorded"}"
            "OFFICIAL_INTERVAL_RESEARCHED" -> "OEM interval source researched · review and confirm before scheduling · ${entry.verifiedOn ?: "date not recorded"}"
            "USER_ENTERED_UNVERIFIED" -> "User-edited · NOT VERIFIED"
            else -> "Unverified catalog data"
        }
        val statusColor = when (entry.verificationStatus) {
            "USER_VERIFIED" -> MaterialTheme.colorScheme.primary
            "OFFICIAL_INTERVAL_RESEARCHED" -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.error
        }
        Text(status, color = statusColor, style = MaterialTheme.typography.labelSmall)
        TextButton(onClick = onEdit) { Text("Modify data") }
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
            loadedSource = true
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Modify catalog configuration") },
        text = {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val availableHeight = if (maxHeight == Dp.Infinity) 520.dp else (maxHeight - 70.dp).coerceAtLeast(200.dp)
                Column(Modifier.fillMaxWidth().heightIn(max = availableHeight).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Original source text is retained in the notes.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = make, onValueChange = { make = it }, label = { Text("Make") }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model") }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = modelYear, onValueChange = { modelYear = it.filter(Char::isDigit).take(4) }, label = { Text("Exact model year (leave blank for a range)") }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = engine, onValueChange = { engine = it }, label = { Text("Engine") }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = trim, onValueChange = { trim = it }, label = { Text("Trim") }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = viscosity, onValueChange = { viscosity = it }, label = { Text("Oil viscosity grade") }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = approval, onValueChange = { approval = it }, label = { Text("Required manufacturer approval/specification") }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = capacityLitres, onValueChange = { capacityLitres = it }, label = { Text("Capacity with filter (litres)") }, singleLine = true, enabled = !busy)
                    HorizontalDivider()
                    Text("Oil change interval", style = MaterialTheme.typography.titleSmall)
                    Text("Normal service", style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(value = interval, onValueChange = { interval = it.filter(Char::isDigit) }, label = { Text("Distance interval") }, singleLine = true, enabled = !busy)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { intervalUnit = "KM" }, enabled = !busy) { Text(if (intervalUnit == "KM") "✓ KM" else "KM") }
                        TextButton(onClick = { intervalUnit = "MILE" }, enabled = !busy) { Text(if (intervalUnit == "MILE") "✓ Mile" else "Mile") }
                    }
                    OutlinedTextField(value = months, onValueChange = { months = it.filter(Char::isDigit) }, label = { Text("Time interval (months)") }, singleLine = true, enabled = !busy)
                    Text("Severe service", style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(value = severeDistance, onValueChange = { severeDistance = it.filter(Char::isDigit) }, label = { Text("Distance interval") }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = severeMonths, onValueChange = { severeMonths = it.filter(Char::isDigit) }, label = { Text("Time interval (months)") }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = filterPart, onValueChange = { filterPart = it }, label = { Text("Oil filter part number") }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Raw catalog and service notes") }, minLines = 3, enabled = !busy)
                    HorizontalDivider()
                    if (entry.verificationStatus == "OFFICIAL_INTERVAL_RESEARCHED") {
                        Text("An OEM source was researched for the interval only. Review the cited guide and check the box below yourself before this interval can affect scheduling; other fields may remain unverified. Editing the vehicle identity, oil grade, or interval resets this source-research status.", style = MaterialTheme.typography.bodySmall)
                    }
                    Row {
                        Checkbox(checked = markVerified, onCheckedChange = { markVerified = it }, enabled = !busy)
                        Text("I checked these values against official manufacturer documentation", modifier = Modifier.padding(top = 12.dp))
                    }
                    OutlinedTextField(value = sourceName, onValueChange = { sourceName = it }, label = { Text("Source name") }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = sourceUrl, onValueChange = { sourceUrl = it }, label = { Text("Official source URL") }, singleLine = true, enabled = !busy)
                    OutlinedTextField(value = verifiedOn, onValueChange = { verifiedOn = it.take(10) }, label = { Text("Date verified (YYYY-MM-DD)") }, singleLine = true, enabled = !busy)
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
                        sourceName = sourceName.trim().ifEmpty { "User-entered source" },
                        sourceUrl = sourceUrl.trim().ifEmpty { null },
                        verifiedOn = when (verificationStatus) {
                            "USER_VERIFIED" -> verifiedOn.trim()
                            "OFFICIAL_INTERVAL_RESEARCHED" -> entry.verifiedOn
                            else -> null
                        },
                        verificationStatus = spec.verificationStatus,
                        notes = when (verificationStatus) {
                            "USER_VERIFIED" -> "User marked values verified against an official source."
                            "OFFICIAL_INTERVAL_RESEARCHED" -> "OEM source researched for oil interval only; user confirmation is required before scheduling can use it."
                            else -> "User-entered source note; values remain unverified."
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
                        error = failure.message ?: "Unable to save catalog entry."
                    } finally {
                        busy = false
                    }
                }
            }, enabled = !busy) {
                if (busy) CircularProgressIndicator(Modifier.semantics { contentDescription = "Saving catalog entry" }) else Text("Save changes")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } }
    )
}

private fun rawField(notes: String?, name: String): String? =
    notes?.lineSequence()?.firstOrNull { it.startsWith("$name:") }?.substringAfter(":")?.trim()?.ifEmpty { null }

private fun editableCatalogNotes(notes: String?): String = notes.orEmpty().lineSequence()
    .filterNot { it.startsWith("AI-generated raw catalog row") || it.startsWith("This dataset is AI-generated") || it.startsWith("Catalog metric status:") }
    .joinToString("\n")
    .trim()

private fun String.toOptionalInt(): Int? = if (isBlank()) null else trim().toIntOrNull() ?: -1
