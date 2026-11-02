package ca.gllavoie.vehicletracker.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ca.gllavoie.vehicletracker.ui.garage.GarageSortOrder
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GarageSortPreferencesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun clearSavedPreference() {
        context.getSharedPreferences("garage_preferences", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun defaultsToNameOrder() {
        assertEquals(GarageSortOrder.NAME, GarageSortPreferences.get(context))
    }

    @Test fun selectedSortOrderPersists() {
        GarageSortPreferences.set(context, GarageSortOrder.YEAR_NEWEST)
        assertEquals(GarageSortOrder.YEAR_NEWEST, GarageSortPreferences.get(context))
    }
}
