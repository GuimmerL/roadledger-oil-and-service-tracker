package ca.gllavoie.vehicletracker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import ca.gllavoie.vehicletracker.preferences.ButtonColorTheme
import ca.gllavoie.vehicletracker.preferences.ThemeMode

private data class ButtonAccent(
    val lightPrimary: Color,
    val lightOnPrimary: Color,
    val lightContainer: Color,
    val lightOnContainer: Color,
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val darkContainer: Color,
    val darkOnContainer: Color
)

private fun ButtonColorTheme.accent(): ButtonAccent? = when (this) {
    ButtonColorTheme.SYSTEM -> null
    ButtonColorTheme.BLUE -> ButtonAccent(
        Color(0xFF005CBB), Color.White, Color(0xFFD7E2FF), Color(0xFF001B3D),
        Color(0xFFADC6FF), Color(0xFF002F65), Color(0xFF00458F), Color(0xFFD7E2FF)
    )
    ButtonColorTheme.TEAL -> ButtonAccent(
        Color(0xFF006A60), Color.White, Color(0xFF73F8E5), Color(0xFF00201C),
        Color(0xFF53DBC9), Color(0xFF003731), Color(0xFF005047), Color(0xFF73F8E5)
    )
    ButtonColorTheme.VIOLET -> ButtonAccent(
        Color(0xFF6750A4), Color.White, Color(0xFFEADDFF), Color(0xFF21005D),
        Color(0xFFD0BCFF), Color(0xFF381E72), Color(0xFF4F378B), Color(0xFFEADDFF)
    )
    ButtonColorTheme.GREEN -> ButtonAccent(
        Color(0xFF376B1E), Color.White, Color(0xFFC4F0A1), Color(0xFF0D2000),
        Color(0xFFA7D981), Color(0xFF1D3710), Color(0xFF2C4E1C), Color(0xFFC4F0A1)
    )
    ButtonColorTheme.AMBER -> ButtonAccent(
        Color(0xFF765900), Color.White, Color(0xFFFFDEA1), Color(0xFF261900),
        Color(0xFFF0C149), Color(0xFF3F2E00), Color(0xFF5A4200), Color(0xFFFFDEA1)
    )
    ButtonColorTheme.ROSE -> ButtonAccent(
        Color(0xFF9C4148), Color.White, Color(0xFFFFDADB), Color(0xFF3B0810),
        Color(0xFFFFB2B5), Color(0xFF5F121B), Color(0xFF7D2930), Color(0xFFFFDADB)
    )
}

@Composable
fun VehicleTrackerTheme(
    themeMode: ThemeMode,
    buttonColorTheme: ButtonColorTheme,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val baseScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    val accent = buttonColorTheme.accent()
    val scheme = if (accent == null) baseScheme else if (dark) {
        baseScheme.copy(
            primary = accent.darkPrimary,
            onPrimary = accent.darkOnPrimary,
            primaryContainer = accent.darkContainer,
            onPrimaryContainer = accent.darkOnContainer
        )
    } else {
        baseScheme.copy(
            primary = accent.lightPrimary,
            onPrimary = accent.lightOnPrimary,
            primaryContainer = accent.lightContainer,
            onPrimaryContainer = accent.lightOnContainer
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
