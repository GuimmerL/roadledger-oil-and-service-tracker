package ca.gllavoie.vehicletracker.preferences

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppearancePreferencesTest {
    @Test fun themeAndButtonColorChoicesPersist() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        AppearancePreferences.setThemeMode(context, ThemeMode.DARK)
        AppearancePreferences.setButtonColorTheme(context, ButtonColorTheme.TEAL)

        assertEquals(ThemeMode.DARK, AppearancePreferences.themeMode(context))
        assertEquals(ButtonColorTheme.TEAL, AppearancePreferences.buttonColorTheme(context))

        ButtonColorTheme.entries.forEach { theme ->
            AppearancePreferences.setButtonColorTheme(context, theme)
            assertEquals(theme, AppearancePreferences.buttonColorTheme(context))
        }
    }

    @Test fun unknownStoredChoicesFallBackSafely() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStoredValue("UNSUPPORTED"))
        assertEquals(ButtonColorTheme.SYSTEM, ButtonColorTheme.fromStoredValue("UNSUPPORTED"))
    }
}
