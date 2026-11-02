package ca.gllavoie.vehicletracker.preferences

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MeasurementSystemPreferencesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun clearSavedPreference() {
        context.getSharedPreferences("vehicle_display_preferences", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun defaultsToMetric() {
        assertEquals(MeasurementSystem.METRIC, MeasurementSystemPreferences.get(context))
    }

    @Test fun selectionPersistsAsImperial() {
        MeasurementSystemPreferences.set(context, MeasurementSystem.IMPERIAL)
        assertEquals(MeasurementSystem.IMPERIAL, MeasurementSystemPreferences.get(context))
    }
}
