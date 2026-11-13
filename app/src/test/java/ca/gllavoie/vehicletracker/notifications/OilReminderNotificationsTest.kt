package ca.gllavoie.vehicletracker.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ca.gllavoie.vehicletracker.domain.calculation.MaintenanceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OilReminderNotificationsTest {
    @Test fun postsOnlyForApproachingOrPastOilChangeDeadlines() {
        assertFalse(OilReminderNotifications.shouldNotify(MaintenanceStatus.UNAVAILABLE))
        assertFalse(OilReminderNotifications.shouldNotify(MaintenanceStatus.UP_TO_DATE))
        assertTrue(OilReminderNotifications.shouldNotify(MaintenanceStatus.DUE_SOON))
        assertTrue(OilReminderNotifications.shouldNotify(MaintenanceStatus.DUE))
        assertTrue(OilReminderNotifications.shouldNotify(MaintenanceStatus.OVERDUE))
    }

    @Test fun reminderCopyNamesTheVehicle() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Change oil soon", OilReminderNotifications.title(context))
        assertEquals("Change oil soon for Daily Driver.", OilReminderNotifications.message(context, "Daily Driver"))
    }
}
