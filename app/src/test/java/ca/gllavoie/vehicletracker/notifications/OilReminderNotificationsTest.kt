package ca.gllavoie.vehicletracker.notifications

import ca.gllavoie.vehicletracker.domain.calculation.MaintenanceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OilReminderNotificationsTest {
    @Test fun postsOnlyForApproachingOrPastOilChangeDeadlines() {
        assertFalse(OilReminderNotifications.shouldNotify(MaintenanceStatus.UNAVAILABLE))
        assertFalse(OilReminderNotifications.shouldNotify(MaintenanceStatus.UP_TO_DATE))
        assertTrue(OilReminderNotifications.shouldNotify(MaintenanceStatus.DUE_SOON))
        assertTrue(OilReminderNotifications.shouldNotify(MaintenanceStatus.DUE))
        assertTrue(OilReminderNotifications.shouldNotify(MaintenanceStatus.OVERDUE))
    }

    @Test fun reminderCopyNamesTheVehicle() {
        assertEquals("Change oil soon", OilReminderNotifications.title())
        assertEquals("Change oil soon for Daily Driver.", OilReminderNotifications.message("Daily Driver"))
    }
}
