package ca.gllavoie.vehicletracker.preferences

import android.content.Context
import androidx.annotation.StringRes
import ca.gllavoie.vehicletracker.R

enum class ThemeMode(@StringRes val labelRes: Int, @StringRes val descriptionRes: Int) {
    SYSTEM(R.string.theme_mode_system, R.string.theme_mode_system_description),
    LIGHT(R.string.theme_mode_light, R.string.theme_mode_light_description),
    DARK(R.string.theme_mode_dark, R.string.theme_mode_dark_description);

    companion object {
        fun fromStoredValue(value: String?): ThemeMode = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

enum class ButtonColorTheme(@StringRes val labelRes: Int) {
    SYSTEM(R.string.button_color_system),
    BLUE(R.string.button_color_blue),
    TEAL(R.string.button_color_teal),
    VIOLET(R.string.button_color_violet),
    GREEN(R.string.button_color_green),
    AMBER(R.string.button_color_amber),
    ROSE(R.string.button_color_rose);

    companion object {
        fun fromStoredValue(value: String?): ButtonColorTheme = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

object AppearancePreferences {
    private const val PREFERENCES = "appearance"
    private const val THEME_MODE = "theme_mode"
    private const val BUTTON_COLOR_THEME = "button_color_theme"

    fun themeMode(context: Context): ThemeMode = ThemeMode.fromStoredValue(
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(THEME_MODE, null)
    )

    fun setThemeMode(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putString(THEME_MODE, mode.name).apply()
    }

    fun buttonColorTheme(context: Context): ButtonColorTheme = ButtonColorTheme.fromStoredValue(
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(BUTTON_COLOR_THEME, null)
    )

    fun setButtonColorTheme(context: Context, theme: ButtonColorTheme) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putString(BUTTON_COLOR_THEME, theme.name).apply()
    }
}
