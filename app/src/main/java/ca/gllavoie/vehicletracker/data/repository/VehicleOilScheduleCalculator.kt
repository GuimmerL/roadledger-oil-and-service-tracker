package ca.gllavoie.vehicletracker.data.repository

import ca.gllavoie.vehicletracker.data.local.OdometerReadingEntity
import ca.gllavoie.vehicletracker.data.local.OilChangeEntity
import ca.gllavoie.vehicletracker.data.local.VehicleEntity
import ca.gllavoie.vehicletracker.data.local.VehicleSpecificationEntity
import ca.gllavoie.vehicletracker.data.local.VehicleSpecificationOverrideEntity
import ca.gllavoie.vehicletracker.domain.calculation.OilSchedule
import ca.gllavoie.vehicletracker.domain.calculation.OilScheduleInput
import ca.gllavoie.vehicletracker.domain.calculation.OilIntervalMode
import ca.gllavoie.vehicletracker.domain.calculation.calculateOilSchedule
import ca.gllavoie.vehicletracker.preferences.convertDailyDistance
import ca.gllavoie.vehicletracker.preferences.convertDistanceIntervalToUnit
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

fun estimateDailyDistancePerDay(
    readings: List<OdometerReadingEntity>,
    unit: String,
    zoneId: ZoneId = ZoneId.systemDefault(),
    startingDailyUsageKmPerDay: Double? = null
): Double? {
    val ordered = readings.filter { it.unit == unit }.sortedBy { it.recordedAtUtc }
    val observedAverage = if (ordered.size >= 2) {
        val first = ordered.first()
        val last = ordered.last()
        val firstDate = Instant.ofEpochMilli(first.recordedAtUtc).atZone(zoneId).toLocalDate()
        val lastDate = Instant.ofEpochMilli(last.recordedAtUtc).atZone(zoneId).toLocalDate()
        val days = ChronoUnit.DAYS.between(firstDate, lastDate)
        val monotonic = ordered.zipWithNext().all { (previous, next) -> next.value >= previous.value }
        if (days > 0 && monotonic && last.value > first.value) (last.value - first.value).toDouble() / days else null
    } else null
    return observedAverage ?: startingDailyUsageKmPerDay?.let { convertDailyDistance(it, "KM", unit) }
}

fun calculateVehicleOilSchedule(
    vehicle: VehicleEntity,
    record: OilChangeEntity,
    readings: List<OdometerReadingEntity>,
    override: VehicleSpecificationOverrideEntity?,
    catalog: VehicleSpecificationEntity?,
    zoneId: ZoneId = ZoneId.systemDefault(),
    asOfDate: LocalDate = LocalDate.now(zoneId)
): OilSchedule {
    require(record.vehicleId == vehicle.id) { "Oil service record does not belong to this vehicle." }
    val currentReading = readings.filter { it.unit == record.odometerUnit }.maxByOrNull { it.recordedAtUtc }
    val currentDate = currentReading?.let { Instant.ofEpochMilli(it.recordedAtUtc).atZone(zoneId).toLocalDate() }
    val averageDistancePerDay = estimateDailyDistancePerDay(
        readings,
        record.odometerUnit,
        zoneId,
        vehicle.startingDailyUsageKmPerDay
    )
    val intervalMode = OilIntervalMode.fromStoredValue(vehicle.oilIntervalMode)
    val verifiedCatalogDistance = catalog
        ?.takeIf { VehicleCatalogOptions.isOilIntervalVerified(it.verificationStatus) }
        ?.let { spec ->
            val selectedInterval = if (intervalMode == OilIntervalMode.SEVERE) spec.severeDistanceInterval else spec.distanceInterval
            selectedInterval?.let { convertDistanceIntervalToUnit(it, spec.distanceUnit, record.odometerUnit)?.toLong() }
        }
    val verifiedCatalogMonths = catalog?.takeIf { VehicleCatalogOptions.isOilIntervalVerified(it.verificationStatus) }?.let {
        if (intervalMode == OilIntervalMode.SEVERE) it.severeTimeIntervalMonths else it.timeIntervalMonths
    }
    val distanceInterval = record.distanceIntervalOverride?.toLong()
        ?: override?.takeIf { it.distanceUnit == record.odometerUnit }?.distanceInterval?.toLong()
        ?: verifiedCatalogDistance
    val timeInterval = record.timeIntervalMonthsOverride?.toLong()
        ?: override?.timeIntervalMonths?.toLong()
        ?: verifiedCatalogMonths?.toLong()

    return calculateOilSchedule(
        OilScheduleInput(
            serviceDate = LocalDate.parse(record.serviceDate),
            serviceOdometer = record.odometerValue,
            currentOdometer = currentReading?.value,
            currentOdometerDate = currentDate,
            distanceInterval = distanceInterval,
            timeIntervalMonths = timeInterval,
            averageDistancePerDay = averageDistancePerDay,
            dueSoonDistance = 1_000L,
            dueSoonDays = 30L,
            asOfDate = asOfDate
        )
    )
}
