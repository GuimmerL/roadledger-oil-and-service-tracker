package ca.gllavoie.vehicletracker.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.gllavoie.vehicletracker.data.repository.OdometerRepository
import ca.gllavoie.vehicletracker.data.repository.OilChangeRepository
import ca.gllavoie.vehicletracker.data.repository.VehicleRepository
import ca.gllavoie.vehicletracker.data.repository.VehicleSpecificationRepository
import ca.gllavoie.vehicletracker.domain.calculation.MaintenanceStatus
import java.text.NumberFormat

@Composable
fun DashboardRoute(
    vehicleRepository: VehicleRepository,
    oilChangeRepository: OilChangeRepository,
    odometerRepository: OdometerRepository,
    specificationRepository: VehicleSpecificationRepository,
    onOpenGarage: () -> Unit
) {
    val dashboardViewModel: DashboardViewModel = viewModel(
        factory = DashboardViewModel.Factory(vehicleRepository, oilChangeRepository, odometerRepository, specificationRepository)
    )
    val state by dashboardViewModel.uiState.collectAsStateWithLifecycle()
    DashboardScreen(state, onOpenGarage)
}

@Composable
private fun DashboardScreen(state: DashboardUiState, onOpenGarage: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Dashboard", style = MaterialTheme.typography.headlineMedium)
        if (state.isLoading) {
            CircularProgressIndicator(Modifier.semantics { contentDescription = "Loading maintenance dashboard" })
        } else {
            Text("${state.activeVehicleCount} active vehicle${if (state.activeVehicleCount == 1) "" else "s"}", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard("Due soon", state.dueSoonCount, Modifier.weight(1f))
                SummaryCard("Due", state.dueCount, Modifier.weight(1f))
                SummaryCard("Overdue", state.overdueCount, Modifier.weight(1f))
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.vehicles.isEmpty()) {
                Text("Add a vehicle to see maintenance status and upcoming estimates.")
                Button(onClick = onOpenGarage) { Text("Open My Garage") }
            } else {
                Text("Vehicle status", style = MaterialTheme.typography.titleLarge)
                state.vehicles.sortedWith(compareBy<DashboardVehicleSummary>({ statusRank(it.schedule?.status) }, { it.schedule?.estimatedDueDate })).forEach { summary ->
                    VehicleStatusCard(summary)
                }
                Button(onClick = onOpenGarage) { Text("Manage garage and service records") }
            }
            Text("Maintenance dates are estimates. Unknown oil intervals remain unverified; this dashboard does not substitute for manufacturer service information.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SummaryCard(title: String, count: Int, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(count.toString(), style = MaterialTheme.typography.headlineSmall)
            Text(title, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun VehicleStatusCard(summary: DashboardVehicleSummary) {
    val vehicle = summary.vehicle
    val title = vehicle.nickname?.takeIf(String::isNotBlank)
        ?: listOfNotNull(vehicle.modelYear?.toString(), vehicle.make, vehicle.model).joinToString(" ")
    val schedule = summary.schedule
    val statusText = when (schedule?.status) {
        MaintenanceStatus.UP_TO_DATE -> "Up to date"
        MaintenanceStatus.DUE_SOON -> "Due soon"
        MaintenanceStatus.DUE -> "Due"
        MaintenanceStatus.OVERDUE -> "Overdue"
        MaintenanceStatus.UNAVAILABLE -> "Schedule unavailable"
        null -> "No oil change recorded"
    }
    val statusColor = when (schedule?.status) {
        MaintenanceStatus.OVERDUE, MaintenanceStatus.DUE -> MaterialTheme.colorScheme.error
        MaintenanceStatus.DUE_SOON -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text("${vehicle.make} ${vehicle.model} · ${if (vehicle.odometerUnit == "MILE") "mi" else "km"}", style = MaterialTheme.typography.bodySmall)
            Text("Oil change: $statusText", color = statusColor, style = MaterialTheme.typography.bodyLarge)
            summary.currentOdometer?.let { Text("Odometer: ${NumberFormat.getIntegerInstance().format(it.value)} ${if (it.unit == "MILE") "mi" else "km"}") }
            summary.latestService?.let { Text("Last service: ${it.serviceDate} · ${NumberFormat.getIntegerInstance().format(it.odometerValue)} ${if (it.odometerUnit == "MILE") "mi" else "km"}", style = MaterialTheme.typography.bodySmall) }
            summary.catalogSpecification?.let { spec ->
                spec.viscosityGrade?.let { Text("Oil type: $it", style = MaterialTheme.typography.bodySmall) }
            }
            schedule?.nextDueOdometer?.let { Text("Next due: ${NumberFormat.getIntegerInstance().format(it)} ${if (vehicle.odometerUnit == "MILE") "mi" else "km"}") }
            schedule?.estimatedDueDate?.let { Text("Estimated date: $it") }
            if (schedule?.estimatedDueDate != null) Text("Time/distance remaining: ${schedule.daysRemaining ?: "—"} days · ${schedule.distanceRemaining?.let { remaining -> NumberFormat.getIntegerInstance().format(remaining) } ?: "—"} ${if (vehicle.odometerUnit == "MILE") "mi" else "km"}", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("Open the vehicle in My Garage to add a service or odometer reading.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun statusRank(status: MaintenanceStatus?): Int = when (status) {
    MaintenanceStatus.OVERDUE -> 0
    MaintenanceStatus.DUE -> 1
    MaintenanceStatus.DUE_SOON -> 2
    MaintenanceStatus.UP_TO_DATE -> 3
    MaintenanceStatus.UNAVAILABLE, null -> 4
}
