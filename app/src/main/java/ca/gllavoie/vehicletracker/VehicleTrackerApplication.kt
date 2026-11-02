package ca.gllavoie.vehicletracker

import android.app.Application
import ca.gllavoie.vehicletracker.notifications.OilReminderNotifications

class VehicleTrackerApplication : Application() {
    val appContainer: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        OilReminderNotifications.createChannel(this)
        OilReminderNotifications.rescheduleIfEnabled(this)
    }
}
