package ca.gllavoie.vehicletracker.domain.calculation

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

enum class MaintenanceStatus { UNAVAILABLE, UP_TO_DATE, DUE_SOON, DUE, OVERDUE }
enum class DueLimit { MILEAGE, TIME, UNKNOWN }

data class OilScheduleInput(
    val serviceDate: LocalDate,
    val serviceOdometer: Long,
    val currentOdometer: Long?,
    val currentOdometerDate: LocalDate?,
    val distanceInterval: Long?,
    val timeIntervalMonths: Long?,
    val averageDistancePerDay: Double?,
    val dueSoonDistance: Long,
    val dueSoonDays: Long,
    val asOfDate: LocalDate,
    val staleOdometerAfterDays: Long = 90
)

data class OilSchedule(
    val nextDueOdometer: Long?,
    val mileageDeadlineDateEstimate: LocalDate?,
    val timeDeadlineDate: LocalDate?,
    val estimatedDueDate: LocalDate?,
    val distanceRemaining: Long?,
    val daysRemaining: Long?,
    val status: MaintenanceStatus,
    val determiningLimit: DueLimit,
    val assumptions: List<String>
)

/** Mileage and elapsed time are independent deadlines; reaching either one makes service due. */
fun calculateOilSchedule(input: OilScheduleInput): OilSchedule {
    val assumptions = mutableListOf<String>()
    val validDistanceInterval = input.distanceInterval?.takeIf { it > 0 && input.serviceOdometer >= 0 }
    val validTimeInterval = input.timeIntervalMonths?.takeIf { it > 0 }
    val dueOdometer = validDistanceInterval?.let { runCatching { Math.addExact(input.serviceOdometer, it) }.getOrNull() }
    val timeDeadline = validTimeInterval?.let { runCatching { input.serviceDate.plusMonths(it) }.getOrNull() }
    val current = input.currentOdometer
    if (input.serviceOdometer < 0 || input.dueSoonDistance < 0 || input.dueSoonDays < 0) {
        assumptions += "Invalid negative odometer or reminder threshold; verify the inputs."
    } else if (validDistanceInterval == null && input.distanceInterval != null) {
        assumptions += "Distance interval must be greater than zero."
    }
    if (validTimeInterval == null && input.timeIntervalMonths != null) assumptions += "Time interval must be greater than zero."
    if (validDistanceInterval != null && dueOdometer == null) assumptions += "Next odometer deadline exceeds supported range."
    if (validTimeInterval != null && timeDeadline == null) assumptions += "Time deadline exceeds supported calendar range."
    val readingDate = input.currentOdometerDate
    val inconsistentOdometer = current != null && current < input.serviceOdometer
    if (inconsistentOdometer) assumptions += "Current odometer is below the last service reading; confirm the readings."
    val remaining = if (current != null && dueOdometer != null && !inconsistentOdometer) {
        runCatching { Math.subtractExact(dueOdometer, current) }.getOrNull().also {
            if (it == null) assumptions += "Distance remaining exceeds supported range."
        }
    } else null
    val mileageDate = if (remaining != null && input.averageDistancePerDay != null && input.averageDistancePerDay.isFinite() && input.averageDistancePerDay > 0.0 && readingDate != null) {
        runCatching {
            readingDate.plusDays(ceil(remaining.coerceAtLeast(0).toDouble() / input.averageDistancePerDay).toLong())
        }.getOrNull().also { if (it == null) assumptions += "Mileage deadline exceeds supported calendar range." }
    } else {
        assumptions += "Mileage-based date cannot be reliably predicted; using time deadline where available."
        null
    }
    if (current == null || readingDate == null) assumptions += "Current odometer reading or its date is missing."
    if (readingDate != null && ChronoUnit.DAYS.between(readingDate, input.asOfDate) > input.staleOdometerAfterDays) {
        assumptions += "Odometer reading is stale; estimates may be inaccurate."
    }
    val dueDate = listOfNotNull(mileageDate, timeDeadline).minOrNull()
    val mileageDue = remaining != null && remaining <= 0L
    val timeDue = timeDeadline != null && !input.asOfDate.isBefore(timeDeadline)
    val distanceNear = remaining != null && remaining in 1..input.dueSoonDistance
    val daysToDeadline = dueDate?.let { ChronoUnit.DAYS.between(input.asOfDate, it) }
    val timeNear = daysToDeadline != null && daysToDeadline in 0..input.dueSoonDays
    val status = when {
        (remaining != null && remaining < 0L) || (timeDeadline != null && input.asOfDate.isAfter(timeDeadline)) -> MaintenanceStatus.OVERDUE
        mileageDue || timeDue -> MaintenanceStatus.DUE
        dueOdometer == null && timeDeadline == null -> MaintenanceStatus.UNAVAILABLE
        distanceNear || timeNear -> MaintenanceStatus.DUE_SOON
        else -> MaintenanceStatus.UP_TO_DATE
    }
    val limit = when {
        mileageDate != null && timeDeadline != null && !mileageDate.isAfter(timeDeadline) -> DueLimit.MILEAGE
        timeDeadline != null -> DueLimit.TIME
        mileageDate != null -> DueLimit.MILEAGE
        else -> DueLimit.UNKNOWN
    }
    return OilSchedule(dueOdometer, mileageDate, timeDeadline, dueDate, remaining, daysToDeadline, status, limit, assumptions)
}
