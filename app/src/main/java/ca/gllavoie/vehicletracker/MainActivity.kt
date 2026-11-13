package ca.gllavoie.vehicletracker

import android.content.Intent
import android.content.res.Configuration
import androidx.annotation.StringRes
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ca.gllavoie.vehicletracker.R
import ca.gllavoie.vehicletracker.preferences.AppearancePreferences
import ca.gllavoie.vehicletracker.preferences.ButtonColorTheme
import ca.gllavoie.vehicletracker.preferences.ThemeMode
import ca.gllavoie.vehicletracker.ui.backup.BackupRoute
import ca.gllavoie.vehicletracker.ui.garage.GarageRoute
import ca.gllavoie.vehicletracker.ui.lookup.VehicleLookupScreen
import ca.gllavoie.vehicletracker.ui.settings.OilReminderSettingsScreen
import ca.gllavoie.vehicletracker.ui.theme.VehicleTrackerTheme
import ca.gllavoie.vehicletracker.notifications.OilReminderNotifications

private enum class AppPage { VEHICLES, VEHICLE_LOOKUP, BACKUP, SETTINGS }

private data class TopDestination(
    val page: AppPage,
    @StringRes val labelRes: Int,
    val filledIcon: ImageVector,
    val outlinedIcon: ImageVector
)

private val topDestinations = listOf(
    TopDestination(AppPage.VEHICLES, R.string.nav_garage, Icons.Filled.Home, Icons.Outlined.Home),
    TopDestination(AppPage.VEHICLE_LOOKUP, R.string.nav_lookup, Icons.Filled.Search, Icons.Outlined.Search),
    TopDestination(AppPage.BACKUP, R.string.nav_backup, Icons.Filled.Share, Icons.Outlined.Share),
    TopDestination(AppPage.SETTINGS, R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings)
)

class MainActivity : ComponentActivity() {
    private val pendingNotificationVehicleId = mutableStateOf<String?>(null)

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appContainer = (application as VehicleTrackerApplication).appContainer
        pendingNotificationVehicleId.value = intent?.getStringExtra(OilReminderNotifications.EXTRA_VEHICLE_ID)
        applyEdgeToEdge(AppearancePreferences.themeMode(this))
        setContent {
            var themeMode by rememberSaveable { mutableStateOf(AppearancePreferences.themeMode(this@MainActivity)) }
            var buttonColorTheme by rememberSaveable { mutableStateOf(AppearancePreferences.buttonColorTheme(this@MainActivity)) }
            LaunchedEffect(themeMode) { applyEdgeToEdge(themeMode) }
            VehicleTrackerTheme(themeMode, buttonColorTheme) {
                var currentPage by rememberSaveable { mutableStateOf(AppPage.VEHICLES) }
                var selectedVehicleId by rememberSaveable { mutableStateOf<String?>(null) }
                val notificationVehicleId by pendingNotificationVehicleId
                LaunchedEffect(notificationVehicleId) {
                    notificationVehicleId?.let {
                        selectedVehicleId = it
                        pendingNotificationVehicleId.value = null
                    }
                }
                val configuration = LocalConfiguration.current
                val useRail = configuration.screenWidthDp >= 600
                val pageTitle = when (currentPage) {
                    AppPage.VEHICLES -> if (selectedVehicleId == null) stringResource(R.string.garage_title_my_garage) else stringResource(R.string.vehicle_details_title)
                    AppPage.VEHICLE_LOOKUP -> stringResource(R.string.vehicle_lookup_title)
                    AppPage.BACKUP -> stringResource(R.string.backup_title)
                    AppPage.SETTINGS -> stringResource(R.string.nav_settings)
                }
                val canGoBack = currentPage != AppPage.VEHICLES || selectedVehicleId != null
                BackHandler(enabled = canGoBack) {
                    if (selectedVehicleId != null) selectedVehicleId = null else currentPage = AppPage.VEHICLES
                }
                MainScaffold(
                    pageTitle = pageTitle,
                    showBack = canGoBack,
                    onBack = { if (selectedVehicleId != null) selectedVehicleId = null else currentPage = AppPage.VEHICLES },
                    selectedPage = currentPage,
                    onSelectPage = { page ->
                        currentPage = page
                        if (page != AppPage.VEHICLES) selectedVehicleId = null
                    },
                    useRail = useRail
                ) {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingNotificationVehicleId.value = intent.getStringExtra(OilReminderNotifications.EXTRA_VEHICLE_ID)
    }

    private fun applyEdgeToEdge(themeMode: ThemeMode) {
        val dark = when (themeMode) {
            ThemeMode.SYSTEM -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(
    pageTitle: String,
    showBack: Boolean,
    onBack: () -> Unit,
    selectedPage: AppPage,
    onSelectPage: (AppPage) -> Unit,
    useRail: Boolean,
    content: @Composable () -> Unit
) {
    val backToVehiclesCd = stringResource(R.string.back_to_vehicles)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(pageTitle) },
                navigationIcon = {
                    if (showBack) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.semantics { contentDescription = backToVehiclesCd }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (!useRail) {
                NavigationBar {
                    topDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = selectedPage == destination.page,
                            onClick = { onSelectPage(destination.page) },
                            icon = {
                                Icon(
                                    if (selectedPage == destination.page) destination.filledIcon else destination.outlinedIcon,
                                    contentDescription = null
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Row(Modifier.fillMaxSize().padding(innerPadding)) {
            if (useRail) {
                NavigationRail {
                    topDestinations.forEach { destination ->
                        NavigationRailItem(
                            selected = selectedPage == destination.page,
                            onClick = { onSelectPage(destination.page) },
                            icon = {
                                Icon(
                                    if (selectedPage == destination.page) destination.filledIcon else destination.outlinedIcon,
                                    contentDescription = null
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) }
                        )
                    }
                }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                // Cap reading width on tablets/landscape; the cards and forms stay
                // comfortably narrow instead of stretching edge to edge.
                Box(Modifier.fillMaxWidth().widthIn(max = 840.dp)) {
                    content()
                }
            }
        }
    }
}
