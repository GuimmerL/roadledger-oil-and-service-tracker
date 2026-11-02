package ca.gllavoie.vehicletracker.ui.fuel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.gllavoie.vehicletracker.data.local.FuelFillUpEntity
import ca.gllavoie.vehicletracker.data.local.VehicleEntity
import ca.gllavoie.vehicletracker.data.repository.FuelRepository
import ca.gllavoie.vehicletracker.data.repository.VehicleRepository
import ca.gllavoie.vehicletracker.domain.calculation.DistanceUnit
import ca.gllavoie.vehicletracker.domain.calculation.EconomyUnit
import ca.gllavoie.vehicletracker.domain.calculation.FillUp
import ca.gllavoie.vehicletracker.domain.calculation.VolumeUnit
import ca.gllavoie.vehicletracker.domain.calculation.calculateEconomyFromSeries
import java.math.BigDecimal
import java.text.DateFormat
import java.text.DecimalFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

@Composable
fun FuelTrackingDialog(
    vehicleId: String,
    vehicleRepository: VehicleRepository,
    fuelRepository: FuelRepository,
    onDismiss: () -> Unit
) {
    val fuelViewModel: FuelViewModel = viewModel(
        key = "fuel-dialog-$vehicleId",
        factory = FuelViewModel.Factory(vehicleId, vehicleRepository, fuelRepository)
    )
    val state by fuelViewModel.uiState.collectAsStateWithLifecycle()
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 800.dp),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Fuel tracking", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onDismiss) { Text("Done") }
                }
                FuelScreen(
                    state = state,
                    onSave = fuelViewModel::save,
                    onConfirmReview = fuelViewModel::confirmReview,
                    onDismissReview = fuelViewModel::dismissReview,
                    onClearError = fuelViewModel::clearError
                )
            }
        }
    }
}

@Composable
fun FuelRoute(vehicleRepository: VehicleRepository, fuelRepository: FuelRepository) {
    val vehicles by vehicleRepository.observeActiveVehicles().collectAsStateWithLifecycle(emptyList())
    var selectedVehicleId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(vehicles) {
        if (vehicles.none { it.id == selectedVehicleId }) selectedVehicleId = vehicles.firstOrNull()?.id
    }
    val vehicle = vehicles.firstOrNull { it.id == selectedVehicleId }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Fuel economy", style = MaterialTheme.typography.headlineMedium)
        if (vehicle == null) {
            Text("Add an active vehicle in My Garage to record fuel purchases.")
        } else {
            VehiclePicker(vehicles, vehicle, onSelect = { selectedVehicleId = it.id })
            val fuelViewModel: FuelViewModel = viewModel(
                key = "fuel-${vehicle.id}",
                factory = FuelViewModel.Factory(vehicle.id, vehicleRepository, fuelRepository)
            )
            val state by fuelViewModel.uiState.collectAsStateWithLifecycle()
            FuelScreen(
                state = state,
                onSave = fuelViewModel::save,
                onConfirmReview = fuelViewModel::confirmReview,
                onDismissReview = fuelViewModel::dismissReview,
                onClearError = fuelViewModel::clearError
            )
        }
    }
}

@Composable
private fun VehiclePicker(vehicles: List<VehicleEntity>, selected: VehicleEntity, onSelect: (VehicleEntity) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = true }) {
        Text("Vehicle: ${selected.nickname?.takeIf(String::isNotBlank) ?: "${selected.make} ${selected.model}"} ▾")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        vehicles.forEach { vehicle ->
            DropdownMenuItem(
                text = { Text(vehicle.nickname?.takeIf(String::isNotBlank) ?: "${vehicle.modelYear?.toString()?.plus(" ") ?: ""}${vehicle.make} ${vehicle.model}") },
                onClick = { expanded = false; onSelect(vehicle) }
            )
        }
    }
}

@Composable
private fun FuelScreen(
    state: FuelUiState,
    onSave: (FuelDraft, Boolean) -> Unit,
    onConfirmReview: () -> Unit,
    onDismissReview: () -> Unit,
    onClearError: () -> Unit
) {
    val vehicle = state.vehicle
    val today = rememberSaveable { LocalDate.now().toString() }
    var date by rememberSaveable(vehicle?.id) { mutableStateOf(today) }
    var odometer by rememberSaveable(vehicle?.id) { mutableStateOf("") }
    var volume by rememberSaveable(vehicle?.id) { mutableStateOf("") }
    var volumeUnit by rememberSaveable(vehicle?.id) { mutableStateOf("L") }
    var cost by rememberSaveable(vehicle?.id) { mutableStateOf("") }
    var currency by rememberSaveable(vehicle?.id) { mutableStateOf("CAD") }
    var fuelType by rememberSaveable(vehicle?.id) { mutableStateOf("Gasoline") }
    var station by rememberSaveable(vehicle?.id) { mutableStateOf("") }
    var isFullTank by rememberSaveable(vehicle?.id) { mutableStateOf(true) }
    var notes by rememberSaveable(vehicle?.id) { mutableStateOf("") }
    var economyUnitName by rememberSaveable(vehicle?.id) { mutableStateOf(EconomyUnit.L_PER_100_KM.name) }
    var previousSuccessCount by rememberSaveable(vehicle?.id) { androidx.compose.runtime.mutableIntStateOf(state.saveSuccessCount) }
    LaunchedEffect(state.saveSuccessCount) {
        if (state.saveSuccessCount > previousSuccessCount) {
            previousSuccessCount = state.saveSuccessCount
            date = LocalDate.now().toString()
            odometer = ""
            volume = ""
            cost = ""
            station = ""
            notes = ""
        }
    }
    if (vehicle == null) {
        CircularProgressIndicator(Modifier.semantics { contentDescription = "Loading vehicle" })
        return
    }
    val economyUnit = runCatching { EconomyUnit.valueOf(economyUnitName) }.getOrDefault(EconomyUnit.L_PER_100_KM)
    val fillUpsAscending = state.fillUps.asReversed()
    val normalizedFillUps = fillUpsAscending.map { fillUp ->
        FillUp(
            odometer = fillUp.odometerValue.toDouble(),
            distanceUnit = if (fillUp.odometerUnit == "MILE") DistanceUnit.MILE else DistanceUnit.KILOMETRE,
            volume = fillUp.volumeMillilitres.toDouble() / 1_000.0,
            volumeUnit = VolumeUnit.LITRE,
            isFullTank = fillUp.isFullTank
        )
    }
    val latestIntervalStart = normalizedFillUps.indices.filter { normalizedFillUps[it].isFullTank }.let { fullIndices ->
        if (fullIndices.size >= 2) fullIndices[fullIndices.lastIndex - 1] else null
    }
    val economy = latestIntervalStart?.let { calculateEconomyFromSeries(normalizedFillUps.drop(it), economyUnit) }

    Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("${vehicle.make} ${vehicle.model} · ${if (vehicle.odometerUnit == "MILE") "mi" else "km"}", style = MaterialTheme.typography.titleMedium)
        Text("Record each fill-up at the pump. Fuel economy is shown only for intervals bounded by full-tank readings.", style = MaterialTheme.typography.bodySmall)
        Text("Add fill-up", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(value = date, onValueChange = { date = it.take(10) }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true, enabled = !state.isSaving)
        OutlinedTextField(value = odometer, onValueChange = { odometer = it.filter(Char::isDigit) }, label = { Text("Odometer (${if (vehicle.odometerUnit == "MILE") "mi" else "km"})") }, singleLine = true, enabled = !state.isSaving)
        OutlinedTextField(value = volume, onValueChange = { volume = it.filter { char -> char.isDigit() || char == '.' } }, label = { Text("Fuel volume (${volumeUnitLabel(volumeUnit)})") }, singleLine = true, enabled = !state.isSaving)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = volumeUnit == "L", onClick = { volumeUnit = "L" }, label = { Text("L") }, enabled = !state.isSaving)
            FilterChip(selected = volumeUnit == "US_GAL", onClick = { volumeUnit = "US_GAL" }, label = { Text("US gal") }, enabled = !state.isSaving)
            FilterChip(selected = volumeUnit == "IMP_GAL", onClick = { volumeUnit = "IMP_GAL" }, label = { Text("Imp gal") }, enabled = !state.isSaving)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = cost, onValueChange = { cost = it.filter { char -> char.isDigit() || char == '.' } }, label = { Text("Cost") }, singleLine = true, enabled = !state.isSaving, modifier = Modifier.weight(1f))
            OutlinedTextField(value = currency, onValueChange = { currency = it.uppercase().take(3) }, label = { Text("Currency") }, singleLine = true, enabled = !state.isSaving, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(value = fuelType, onValueChange = { fuelType = it }, label = { Text("Fuel type") }, singleLine = true, enabled = !state.isSaving)
        OutlinedTextField(value = station, onValueChange = { station = it }, label = { Text("Station") }, singleLine = true, enabled = !state.isSaving)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Full tank", modifier = Modifier.padding(top = 12.dp))
            Switch(checked = isFullTank, onCheckedChange = { isFullTank = it }, enabled = !state.isSaving)
        }
        OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, singleLine = true, enabled = !state.isSaving)
        state.error?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
            TextButton(onClick = onClearError) { Text("Dismiss error") }
        }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        Button(
            onClick = { onSave(FuelDraft(date, odometer, volume, volumeUnit, cost, currency, fuelType, station, isFullTank, notes), false) },
            enabled = !state.isSaving && date.length == 10 && odometer.isNotBlank() && volume.isNotBlank()
        ) {
            if (state.isSaving) CircularProgressIndicator(Modifier.semantics { contentDescription = "Saving fuel fill-up" }) else Text("Save fill-up")
        }
        HorizontalDivider()
        Text("Fuel economy", style = MaterialTheme.typography.titleLarge)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            EconomyUnit.entries.forEach { unit ->
                FilterChip(
                    selected = economyUnit == unit,
                    onClick = { economyUnitName = unit.name },
                    label = { Text(economyUnitLabel(unit)) }
                )
            }
        }
        if (economy == null) Text("Add at least two full-tank fill-ups with increasing odometer readings to calculate an interval.")
        else Text("Latest complete interval: ${DecimalFormat("0.0").format(economy)} ${economyUnitLabel(economyUnit)}", style = MaterialTheme.typography.titleMedium)
        HorizontalDivider()
        Text("Fill-up history", style = MaterialTheme.typography.titleLarge)
        if (state.fillUps.isEmpty()) Text("No fuel purchases recorded yet.")
        state.fillUps.forEach { fillUp -> FuelRecordRow(fillUp) }
    }

    state.review?.let { review ->
        AlertDialog(
            onDismissRequest = onDismissReview,
            title = { Text("Confirm fuel odometer") },
            text = { Text("${review.reason}\n\nSave this fill-up anyway? Confirm only if the reading/date is correct (for example, an odometer reset).") },
            confirmButton = { TextButton(onClick = onConfirmReview) { Text("Save fill-up") } },
            dismissButton = { TextButton(onClick = onDismissReview) { Text("Review entry") } }
        )
    }
}

@Composable
private fun FuelRecordRow(fillUp: FuelFillUpEntity) {
    val date = Instant.ofEpochMilli(fillUp.filledAtUtc).atZone(ZoneId.systemDefault()).toLocalDate()
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("$date · ${fillUp.odometerValue} ${if (fillUp.odometerUnit == "MILE") "mi" else "km"}", style = MaterialTheme.typography.titleSmall)
        Text("${DecimalFormat("0.000").format(fillUp.volumeMillilitres / 1000.0)} L · ${if (fillUp.isFullTank) "Full tank" else "Partial tank"} · ${fillUp.fuelType ?: "Fuel"}")
        fillUp.totalCostMinor?.let { Text("${fillUp.currencyCode} ${BigDecimal(it).divide(BigDecimal(100)).setScale(2)}") }
        fillUp.stationName?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        fillUp.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        HorizontalDivider()
    }
}

private fun volumeUnitLabel(value: String) = when (value) {
    "US_GAL" -> "US gal"
    "IMP_GAL" -> "Imp gal"
    else -> "L"
}

private fun economyUnitLabel(unit: EconomyUnit): String = when (unit) {
    EconomyUnit.L_PER_100_KM -> "L/100km"
    EconomyUnit.KM_PER_LITRE -> "km/L"
    EconomyUnit.MPG_US -> "US mpg"
    EconomyUnit.MPG_IMPERIAL -> "Imp mpg"
}
