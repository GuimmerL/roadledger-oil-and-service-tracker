package ca.gllavoie.vehicletracker.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ca.gllavoie.vehicletracker.MainActivity
import ca.gllavoie.vehicletracker.R
import ca.gllavoie.vehicletracker.VehicleTrackerApplication
import ca.gllavoie.vehicletracker.data.local.VehicleEntity
import ca.gllavoie.vehicletracker.data.repository.calculateVehicleOilSchedule
import ca.gllavoie.vehicletracker.domain.calculation.MaintenanceStatus
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

object OilReminderNotifications {
    const val CHANNEL_ID = "oil_change_reminders"
    const val PERIODIC_WORK_NAME = "daily_oil_change_reminder_check"
    const val INITIAL_WORK_NAME = "initial_oil_change_reminder_check"
    private const val PREFS_NAME = "oil_reminder_preferences"
    private const val ENABLED_KEY = "enabled"
    private const val FINGERPRINT_PREFIX = "last_notice_"
    internal const val NOTIFICATION_TAG_PREFIX = "oil-reminder-"
    const val EXTRA_VEHICLE_ID = "ca.gllavoie.vehicletracker.EXTRA_VEHICLE_ID"
    const val EXTRA_VEHICLE_NAME = "ca.gllavoie.vehicletracker.EXTRA_VEHICLE_NAME"

    fun isEnabled(context: Context): Boolean = preferences(context).getBoolean(ENABLED_KEY, false)

    fun enable(context: Context) {
        preferences(context).edit().putBoolean(ENABLED_KEY, true).apply()
        createChannel(context)
        val workManager = WorkManager.getInstance(context)
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<OilReminderWorker>(24, TimeUnit.HOURS).build()
        )
        workManager.enqueueUniqueWork(
            INITIAL_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<OilReminderWorker>().build()
        )
    }

    fun disable(context: Context) {
        val prefs = preferences(context)
        val edit = prefs.edit().putBoolean(ENABLED_KEY, false)
        prefs.all.keys.filter { it.startsWith(FINGERPRINT_PREFIX) }.forEach(edit::remove)
        edit.apply()
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
        workManager.cancelUniqueWork(INITIAL_WORK_NAME)
        notificationManager(context).activeNotifications
            .filter { it.tag?.startsWith(NOTIFICATION_TAG_PREFIX) == true }
            .forEach { notificationManager(context).cancel(it.tag, it.id) }
    }

    fun rescheduleIfEnabled(context: Context) {
        if (isEnabled(context)) enable(context)
    }

    fun canPostNotifications(context: Context): Boolean =
        NotificationManagerCompatProxy.areNotificationsEnabled(context) &&
            (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.oil_reminder_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.oil_reminder_channel_description) }
            notificationManager(context).createNotificationChannel(channel)
        }
    }

    internal fun shouldNotify(status: MaintenanceStatus): Boolean = status in setOf(
        MaintenanceStatus.DUE_SOON,
        MaintenanceStatus.DUE,
        MaintenanceStatus.OVERDUE
    )

    internal fun title(context: Context): String = context.getString(R.string.oil_reminder_title)
    internal fun message(context: Context, vehicleName: String): String = context.getString(R.string.oil_reminder_message, vehicleName)

    internal fun postForVehicle(context: Context, vehicle: VehicleEntity, fingerprint: String) {
        val tag = NOTIFICATION_TAG_PREFIX + vehicle.id
        val prefs = preferences(context)
        val preferenceKey = FINGERPRINT_PREFIX + vehicle.id
        if (prefs.getString(preferenceKey, null) == fingerprint) return
        if (!canPostNotifications(context)) return
        val label = vehicle.nickname?.takeIf(String::isNotBlank)
            ?: listOfNotNull(vehicle.modelYear?.toString(), vehicle.make, vehicle.model).joinToString(" ")
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_VEHICLE_ID, vehicle.id)
            .putExtra(EXTRA_VEHICLE_NAME, label)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            context,
            vehicle.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_oil)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentTitle(title(context))
            .setContentText(message(context, label))
            .setStyle(Notification.BigTextStyle().bigText(message(context, label)))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        notificationManager(context).notify(tag, 0, notification)
        prefs.edit().putString(preferenceKey, fingerprint).apply()
    }

    internal fun cancelForVehicle(context: Context, vehicleId: String) {
        notificationManager(context).cancel(NOTIFICATION_TAG_PREFIX + vehicleId, 0)
        preferences(context).edit().remove(FINGERPRINT_PREFIX + vehicleId).apply()
    }

    private fun preferences(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private fun notificationManager(context: Context) = context.getSystemService(NotificationManager::class.java)
}

private object NotificationManagerCompatProxy {
    fun areNotificationsEnabled(context: Context): Boolean =
        androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
}

class OilReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (!OilReminderNotifications.isEnabled(applicationContext)) return Result.success()
        if (!OilReminderNotifications.canPostNotifications(applicationContext)) return Result.success()
        val container = (applicationContext as VehicleTrackerApplication).appContainer
        val vehicles = try {
            container.vehicleRepository.observeActiveVehicles().first()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return Result.retry()
        }
        val activeIds = vehicles.mapTo(mutableSetOf(), VehicleEntity::id)
        androidx.core.app.NotificationManagerCompat.from(applicationContext).activeNotifications
            .filter { it.tag?.startsWith(OilReminderNotifications.NOTIFICATION_TAG_PREFIX) == true }
            .mapNotNull { it.tag?.removePrefix(OilReminderNotifications.NOTIFICATION_TAG_PREFIX) }
            .filter { it !in activeIds }
            .forEach { OilReminderNotifications.cancelForVehicle(applicationContext, it) }
        var failed = false
        for (vehicle in vehicles) {
            try {
                val latestService = container.oilChangeRepository.observeRecords(vehicle.id).first().firstOrNull()
                if (latestService == null) {
                    OilReminderNotifications.cancelForVehicle(applicationContext, vehicle.id)
                    continue
                }
                val readings = container.odometerRepository.observeReadings(vehicle.id).first()
                val override = container.specificationRepository.observeOverride(vehicle.id).first()
                val catalog = vehicle.defaultSpecificationId?.let {
                    container.specificationRepository.observeCatalogEntry(it).first()
                }
                val schedule = calculateVehicleOilSchedule(vehicle, latestService, readings, override, catalog)
                if (!OilReminderNotifications.shouldNotify(schedule.status)) {
                    OilReminderNotifications.cancelForVehicle(applicationContext, vehicle.id)
                    continue
                }
                val fingerprint = listOf(
                    latestService.id,
                    schedule.status.name,
                    schedule.nextDueOdometer?.toString().orEmpty(),
                    schedule.estimatedDueDate?.toString().orEmpty()
                ).joinToString("|")
                OilReminderNotifications.postForVehicle(applicationContext, vehicle, fingerprint)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed = true
            }
        }
        return if (failed) Result.retry() else Result.success()
    }
}
