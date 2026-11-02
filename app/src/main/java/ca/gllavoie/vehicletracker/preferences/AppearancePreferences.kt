package ca.gllavoie.vehicletracker.preferences

import android.content.Context

enum class ThemeMode(val label: String, val description: String) {
    SYSTEM("Follow phone", "Use the phone's light or dark setting."),
    LIGHT("Light", "Always use the light appearance."),
    DARK("Dark", "Always use the dark appearance.");

    companion object {
        fun fromStoredValue(value: String?): ThemeMode = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

enum class ButtonColorTheme(val label: String) {
    SYSTEM("Phone colors"),
    BLUE("Blue"),
    TEAL("Teal"),
    VIOLET("Violet"),
    GREEN("Green"),
    AMBER("Amber"),
    ROSE("Rose");

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
