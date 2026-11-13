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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.gllavoie.vehicletracker.R
import ca.gllavoie.vehicletracker.data.local.FuelFillUpEntity
import ca.gllavoie.vehicletracker.data.local.VehicleEntity
import ca.gllavoie.vehicletracker.data.repository.FuelRepository
import ca.gllavoie.vehicletracker.data.repository.VehicleRepository
import ca.gllavoie.vehicletracker.domain.calculation.DistanceUnit
import ca.gllavoie.vehicletracker.ui.common.DateTextField
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
                    Text(stringResource(R.string.fuel_tracking), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
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
    val vehicles by remember(vehicleRepository) { vehicleRepository.observeActiveVehicles() }.collectAsStateWithLifecycle(emptyList())
    var selectedVehicleId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(vehicles) {
        if (vehicles.none { it.id == selectedVehicleId }) selectedVehicleId = vehicles.firstOrNull()?.id
    }
    val vehicle = vehicles.firstOrNull { it.id == selectedVehicleId }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.fuel_economy), style = MaterialTheme.typography.headlineMedium)
        if (vehicle == null) {
            Text(stringResource(R.string.add_active_vehicle_hint))
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
        Text(stringResource(R.string.vehicle_picker_format, selected.nickname?.takeIf(String::isNotBlank) ?: stringResource(R.string.two_part_format, selected.make, selected.model)))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        vehicles.forEach { vehicle ->
            DropdownMenuItem(
                text = {
                    Text(
                        vehicle.nickname?.takeIf(String::isNotBlank)
                            ?: stringResource(R.string.vehicle_name_with_year_format, vehicle.modelYear?.toString()?.plus(" ") ?: "", vehicle.make, vehicle.model)
                    )
                },
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
    val loadingVehicleCd = stringResource(R.string.loading_vehicle)
    val savingFuelFillUpCd = stringResource(R.string.saving_fuel_fill_up_cd)
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
        if (state.isLoading) {
            CircularProgressIndicator(Modifier.semantics { contentDescription = loadingVehicleCd })
        } else {
            Text(stringResource(R.string.vehicle_no_longer_available), style = MaterialTheme.typography.bodyMedium)
        }
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
        Text(stringResource(R.string.three_part_dot_format, vehicle.make, vehicle.model, stringResource(if (vehicle.odometerUnit == "MILE") R.string.unit_mi else R.string.unit_km)), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.fuel_record_hint), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.add_fill_up), style = MaterialTheme.typography.titleLarge)
        DateTextField(value = date, onValueChange = { date = it }, label = stringResource(R.string.date_ymd_label), enabled = !state.isSaving)
        OutlinedTextField(value = odometer, onValueChange = { odometer = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.odometer_label, stringResource(if (vehicle.odometerUnit == "MILE") R.string.unit_mi else R.string.unit_km))) }, singleLine = true, enabled = !state.isSaving)
        OutlinedTextField(value = volume, onValueChange = { volume = it.filter { char -> char.isDigit() || char == '.' } }, label = { Text(stringResource(R.string.fuel_volume_label, volumeUnitLabel(volumeUnit))) }, singleLine = true, enabled = !state.isSaving)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = volumeUnit == "L", onClick = { volumeUnit = "L" }, label = { Text(stringResource(R.string.unit_litre)) }, enabled = !state.isSaving)
            FilterChip(selected = volumeUnit == "US_GAL", onClick = { volumeUnit = "US_GAL" }, label = { Text(stringResource(R.string.unit_us_gal)) }, enabled = !state.isSaving)
            FilterChip(selected = volumeUnit == "IMP_GAL", onClick = { volumeUnit = "IMP_GAL" }, label = { Text(stringResource(R.string.unit_imp_gal)) }, enabled = !state.isSaving)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = cost, onValueChange = { cost = it.filter { char -> char.isDigit() || char == '.' } }, label = { Text(stringResource(R.string.cost_label)) }, singleLine = true, enabled = !state.isSaving, modifier = Modifier.weight(1f))
            OutlinedTextField(value = currency, onValueChange = { currency = it.uppercase().take(3) }, label = { Text(stringResource(R.string.currency_label)) }, singleLine = true, enabled = !state.isSaving, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(value = fuelType, onValueChange = { fuelType = it }, label = { Text(stringResource(R.string.fuel_type_label)) }, singleLine = true, enabled = !state.isSaving)
        OutlinedTextField(value = station, onValueChange = { station = it }, label = { Text(stringResource(R.string.station_label)) }, singleLine = true, enabled = !state.isSaving)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.full_tank), modifier = Modifier.padding(top = 12.dp))
            Switch(checked = isFullTank, onCheckedChange = { isFullTank = it }, enabled = !state.isSaving)
        }
        OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(stringResource(R.string.notes_label)) }, singleLine = true, enabled = !state.isSaving)
        state.error?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
            TextButton(onClick = onClearError) { Text(stringResource(R.string.dismiss_error)) }
        }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        Button(
            onClick = { onSave(FuelDraft(date, odometer, volume, volumeUnit, cost, currency, fuelType, station, isFullTank, notes), false) },
            enabled = !state.isSaving && date.length == 10 && odometer.isNotBlank() && volume.isNotBlank()
        ) {
            if (state.isSaving) CircularProgressIndicator(Modifier.semantics { contentDescription = savingFuelFillUpCd }) else Text(stringResource(R.string.save_fill_up))
        }
        HorizontalDivider()
        Text(stringResource(R.string.fuel_economy), style = MaterialTheme.typography.titleLarge)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            EconomyUnit.entries.forEach { unit ->
                FilterChip(
                    selected = economyUnit == unit,
                    onClick = { economyUnitName = unit.name },
                    label = { Text(economyUnitLabel(unit)) }
                )
            }
        }
        if (economy == null) Text(stringResource(R.string.economy_needs_two_fill_ups))
        else Text(stringResource(R.string.latest_complete_interval_format, DecimalFormat("0.0").format(economy), economyUnitLabel(economyUnit)), style = MaterialTheme.typography.titleMedium)
        HorizontalDivider()
        Text(stringResource(R.string.fill_up_history), style = MaterialTheme.typography.titleLarge)
        if (state.fillUps.isEmpty()) Text(stringResource(R.string.no_fuel_purchases))
        state.fillUps.forEach { fillUp -> FuelRecordRow(fillUp) }
    }

    state.review?.let { review ->
        AlertDialog(
            onDismissRequest = onDismissReview,
            title = { Text(stringResource(R.string.confirm_fuel_odometer_title)) },
            text = { Text(stringResource(R.string.confirm_fuel_message, review.reason)) },
            confirmButton = { TextButton(onClick = onConfirmReview) { Text(stringResource(R.string.save_fill_up)) } },
            dismissButton = { TextButton(onClick = onDismissReview) { Text(stringResource(R.string.review_entry)) } }
        )
    }
}

@Composable
private fun FuelRecordRow(fillUp: FuelFillUpEntity) {
    val date = Instant.ofEpochMilli(fillUp.filledAtUtc).atZone(ZoneId.systemDefault()).toLocalDate()
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(stringResource(R.string.dot_three_part_format, date.toString(), fillUp.odometerValue.toString(), stringResource(if (fillUp.odometerUnit == "MILE") R.string.unit_mi else R.string.unit_km)), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(
                R.string.fuel_row_volume_format,
                DecimalFormat("0.000").format(fillUp.volumeMillilitres / 1000.0),
                stringResource(R.string.unit_litre),
                stringResource(if (fillUp.isFullTank) R.string.full_tank else R.string.partial_tank),
                fillUp.fuelType ?: stringResource(R.string.fuel_default)
            )
        )
        fillUp.totalCostMinor?.let {
            Text(stringResource(R.string.two_part_format, fillUp.currencyCode, BigDecimal(it).divide(BigDecimal(100)).setScale(2).toPlainString()))
        }
        fillUp.stationName?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        fillUp.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        HorizontalDivider()
    }
}

@Composable
private fun volumeUnitLabel(value: String) = when (value) {
    "US_GAL" -> stringResource(R.string.unit_us_gal)
    "IMP_GAL" -> stringResource(R.string.unit_imp_gal)
    else -> stringResource(R.string.unit_litre)
}

@Composable
private fun economyUnitLabel(unit: EconomyUnit): String = when (unit) {
    EconomyUnit.L_PER_100_KM -> stringResource(R.string.economy_l_per_100km)
    EconomyUnit.KM_PER_LITRE -> stringResource(R.string.economy_km_per_l)
    EconomyUnit.MPG_US -> stringResource(R.string.economy_us_mpg)
    EconomyUnit.MPG_IMPERIAL -> stringResource(R.string.economy_imp_mpg)
}
