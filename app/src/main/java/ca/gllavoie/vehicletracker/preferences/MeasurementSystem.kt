package ca.gllavoie.vehicletracker.preferences

import android.content.Context
import kotlin.math.floor

/** Preferred display system; stored vehicle and service readings keep their original units. */
enum class MeasurementSystem(val distanceUnit: String, val label: String) {
    METRIC("KM", "Metric (km)"),
    IMPERIAL("MILE", "Imperial (mi)")
}

object MeasurementSystemPreferences {
    private const val PREFS_NAME = "vehicle_display_preferences"
    private const val SYSTEM_KEY = "measurement_system"

    fun get(context: Context): MeasurementSystem = runCatching {
        MeasurementSystem.valueOf(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(SYSTEM_KEY, null) ?: "METRIC")
    }.getOrDefault(MeasurementSystem.METRIC)

    fun set(context: Context, system: MeasurementSystem) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(SYSTEM_KEY, system.name)
            .apply()
    }
}

/** Converts intervals conservatively by rounding down to a whole display unit. */
fun convertDistanceInterval(value: Int, sourceUnit: String?, targetSystem: MeasurementSystem): Int? =
    convertDistanceIntervalToUnit(value, sourceUnit, targetSystem.distanceUnit)

fun convertDistanceIntervalToUnit(value: Int, sourceUnit: String?, targetUnit: String?): Int? {
    if (value <= 0 || sourceUnit !in setOf("KM", "MILE") || targetUnit !in setOf("KM", "MILE")) return null
    if (sourceUnit == targetUnit) return value
    val kilometers = if (sourceUnit == "MILE") value * KM_PER_MILE else value.toDouble()
    val converted = if (targetUnit == "MILE") kilometers / KM_PER_MILE else kilometers
    return floor(converted).toInt().coerceAtLeast(1)
}

fun convertDailyDistance(value: Double, sourceUnit: String?, targetUnit: String?): Double? {
    if (!value.isFinite() || value <= 0.0 || sourceUnit !in setOf("KM", "MILE") || targetUnit !in setOf("KM", "MILE")) return null
    if (sourceUnit == targetUnit) return value
    return if (sourceUnit == "MILE") value * KM_PER_MILE else value / KM_PER_MILE
}

fun formatDailyDistance(value: Double): String =
    java.math.BigDecimal.valueOf(value).setScale(1, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

private const val KM_PER_MILE = 1.609344
