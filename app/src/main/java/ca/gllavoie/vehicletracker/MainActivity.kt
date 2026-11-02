package ca.gllavoie.vehicletracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ca.gllavoie.vehicletracker.ui.backup.BackupRoute
import ca.gllavoie.vehicletracker.ui.garage.GarageRoute
import ca.gllavoie.vehicletracker.ui.settings.OilReminderSettingsScreen
import ca.gllavoie.vehicletracker.ui.lookup.VehicleLookupScreen
import ca.gllavoie.vehicletracker.ui.theme.VehicleTrackerTheme
import ca.gllavoie.vehicletracker.preferences.AppearancePreferences
import ca.gllavoie.vehicletracker.preferences.ButtonColorTheme
import ca.gllavoie.vehicletracker.preferences.ThemeMode

private enum class AppPage { VEHICLES, VEHICLE_LOOKUP, BACKUP, SETTINGS }

class MainActivity : ComponentActivity() {
    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appContainer = (application as VehicleTrackerApplication).appContainer
        setContent {
            var themeMode by rememberSaveable { mutableStateOf(AppearancePreferences.themeMode(this@MainActivity)) }
            var buttonColorTheme by rememberSaveable { mutableStateOf(AppearancePreferences.buttonColorTheme(this@MainActivity)) }
            VehicleTrackerTheme(themeMode, buttonColorTheme) {
                var currentPage by rememberSaveable { mutableStateOf(AppPage.VEHICLES) }
                var selectedVehicleId by rememberSaveable { mutableStateOf<String?>(null) }
                var menuExpanded by rememberSaveable { mutableStateOf(false) }
                val pageTitle = when (currentPage) {
                    AppPage.VEHICLES -> if (selectedVehicleId == null) "My Garage" else "Vehicle details"
                    AppPage.VEHICLE_LOOKUP -> "Vehicle data lookup"
                    AppPage.BACKUP -> "Backup and Restore"
                    AppPage.SETTINGS -> "Settings"
                }
                BackHandler(enabled = currentPage != AppPage.VEHICLES || selectedVehicleId != null) {
                    if (currentPage != AppPage.VEHICLES) currentPage = AppPage.VEHICLES else selectedVehicleId = null
                }
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(pageTitle) },
                            navigationIcon = {
                                Box {
                                    IconButton(
                                        onClick = {
                                            if (currentPage == AppPage.VEHICLES && selectedVehicleId != null) selectedVehicleId = null
                                            else if (currentPage == AppPage.VEHICLES) menuExpanded = true
                                            else currentPage = AppPage.VEHICLES
                                        },
                                        modifier = Modifier.semantics {
                                            contentDescription = when {
                                                currentPage == AppPage.VEHICLES && selectedVehicleId != null -> "Back to vehicles"
                                                currentPage == AppPage.VEHICLES -> "Open backup and settings menu"
                                                else -> "Back to vehicles"
                                            }
                                        }
                                    ) {
                                        Text(if (currentPage == AppPage.VEHICLES && selectedVehicleId == null) "☰" else "‹", style = MaterialTheme.typography.headlineSmall)
                                    }
                                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                        DropdownMenuItem(
                                            text = { Text("Backup and restore") },
                                            onClick = { menuExpanded = false; currentPage = AppPage.BACKUP }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Vehicle data lookup") },
                                            onClick = { menuExpanded = false; currentPage = AppPage.VEHICLE_LOOKUP }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Settings") },
                                            onClick = { menuExpanded = false; currentPage = AppPage.SETTINGS }
                                        )
                                    }
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    Box(Modifier.fillMaxSize().padding(innerPadding)) {
                        when (currentPage) {
                            AppPage.VEHICLES -> GarageRoute(
                                appContainer.vehicleRepository,
                                appContainer.odometerRepository,
                                appContainer.oilChangeRepository,
                                appContainer.receiptRepository,
                                appContainer.specificationRepository,
                                appContainer.fuelRepository,
                                selectedVehicleId = selectedVehicleId,
                                onVehicleSelected = { selectedVehicleId = it },
                                onBackToVehicles = { selectedVehicleId = null }
                            )
                            AppPage.VEHICLE_LOOKUP -> VehicleLookupScreen(appContainer.specificationRepository)
                            AppPage.BACKUP -> BackupRoute(appContainer.backupRepository)
                            AppPage.SETTINGS -> OilReminderSettingsScreen(
                                themeMode = themeMode,
                                buttonColorTheme = buttonColorTheme,
                                onThemeModeChanged = {
                                    themeMode = it
                                    AppearancePreferences.setThemeMode(this@MainActivity, it)
                                },
                                onButtonColorThemeChanged = {
                                    buttonColorTheme = it
                                    AppearancePreferences.setButtonColorTheme(this@MainActivity, it)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
