package ca.gllavoie.vehicletracker.domain.calculation

import java.time.LocalDate

fun main() {
    check(kotlin.math.abs(calculateEconomy(
        FillUp(10_000.0, DistanceUnit.KILOMETRE, 50.0, VolumeUnit.LITRE, true),
        FillUp(10_520.0, DistanceUnit.KILOMETRE, 46.0, VolumeUnit.LITRE, true),
        EconomyUnit.L_PER_100_KM
    )!! - 46.0 / 520.0 * 100.0) < 0.0001)
    check(kotlin.math.abs(calculateEconomy(
        FillUp(0.0, DistanceUnit.MILE, 10.0, VolumeUnit.US_GALLON, true),
        FillUp(300.0, DistanceUnit.MILE, 10.0, VolumeUnit.US_GALLON, true),
        EconomyUnit.MPG_US
    )!! - 30.0) < 0.0001)
    val imperialMpg = calculateEconomy(
        FillUp(0.0, DistanceUnit.MILE, 40.0, VolumeUnit.IMPERIAL_GALLON, true),
        FillUp(300.0, DistanceUnit.MILE, 10.0, VolumeUnit.IMPERIAL_GALLON, true),
        EconomyUnit.MPG_IMPERIAL
    )!!
    check(kotlin.math.abs(imperialMpg - 30.0) < 0.0001)
    val usMpgFromImperialFuel = calculateEconomy(
        FillUp(0.0, DistanceUnit.MILE, 40.0, VolumeUnit.IMPERIAL_GALLON, true),
        FillUp(300.0, DistanceUnit.MILE, 10.0, VolumeUnit.IMPERIAL_GALLON, true),
        EconomyUnit.MPG_US
    )!!
    check(kotlin.math.abs(usMpgFromImperialFuel - 30.0 * 3.785411784 / 4.54609) < 0.0001)
    val series = listOf(
        FillUp(0.0, DistanceUnit.KILOMETRE, 50.0, VolumeUnit.LITRE, true),
        FillUp(100.0, DistanceUnit.KILOMETRE, 10.0, VolumeUnit.US_GALLON, false),
        FillUp(200.0, DistanceUnit.KILOMETRE, 10.0, VolumeUnit.IMPERIAL_GALLON, true)
    )
    val expectedL100 = (10.0 * 3.785411784 + 10.0 * 4.54609) / 200.0 * 100.0
    check(kotlin.math.abs(calculateEconomyFromSeries(series, EconomyUnit.L_PER_100_KM)!! - expectedL100) < 0.0001)
    check(calculateEconomyFromSeries(series.mapIndexed { index, fill -> if (index == 1) fill.copy(odometer = -1.0) else fill }, EconomyUnit.L_PER_100_KM) == null)

    val mileageFirst = calculateOilSchedule(OilScheduleInput(
        serviceDate = LocalDate.parse("2026-01-01"), serviceOdometer = 0,
        currentOdometer = 7_500, currentOdometerDate = LocalDate.parse("2026-10-01"),
        distanceInterval = 8_000, timeIntervalMonths = 12, averageDistancePerDay = 100.0,
        dueSoonDistance = 1_000, dueSoonDays = 30, asOfDate = LocalDate.parse("2026-10-01")
    ))
    check(mileageFirst.estimatedDueDate == LocalDate.parse("2026-10-06"))
    check(mileageFirst.determiningLimit == DueLimit.MILEAGE)
    check(mileageFirst.status == MaintenanceStatus.DUE_SOON)

    val timeFirst = calculateOilSchedule(OilScheduleInput(
        serviceDate = LocalDate.parse("2026-08-01"), serviceOdometer = 0,
        currentOdometer = 1_000, currentOdometerDate = LocalDate.parse("2026-10-01"),
        distanceInterval = 8_000, timeIntervalMonths = 1, averageDistancePerDay = 10.0,
        dueSoonDistance = 1_000, dueSoonDays = 30, asOfDate = LocalDate.parse("2026-10-12")
    ))
    check(timeFirst.estimatedDueDate == LocalDate.parse("2026-09-01"))
    check(timeFirst.status == MaintenanceStatus.OVERDUE)

    val exactMileageDue = calculateOilSchedule(OilScheduleInput(
        serviceDate = LocalDate.parse("2026-01-01"), serviceOdometer = 0,
        currentOdometer = 8_000, currentOdometerDate = LocalDate.parse("2026-01-01"),
        distanceInterval = 8_000, timeIntervalMonths = 12, averageDistancePerDay = 1.0,
        dueSoonDistance = 1_000, dueSoonDays = 30, asOfDate = LocalDate.parse("2026-01-01")
    ))
    check(exactMileageDue.status == MaintenanceStatus.DUE)

    val invalidReading = calculateOilSchedule(OilScheduleInput(
        serviceDate = LocalDate.parse("2026-01-01"), serviceOdometer = 1_000,
        currentOdometer = 900, currentOdometerDate = LocalDate.parse("2026-10-01"),
        distanceInterval = 8_000, timeIntervalMonths = 12, averageDistancePerDay = 10.0,
        dueSoonDistance = 1_000, dueSoonDays = 30, asOfDate = LocalDate.parse("2026-10-12")
    ))
    check(invalidReading.distanceRemaining == null)
    check(invalidReading.assumptions.any { it.contains("below the last service") })

    val noUsage = calculateOilSchedule(OilScheduleInput(
        serviceDate = LocalDate.parse("2026-01-01"), serviceOdometer = 0,
        currentOdometer = null, currentOdometerDate = null,
        distanceInterval = 8_000, timeIntervalMonths = 6, averageDistancePerDay = 0.0,
        dueSoonDistance = 1_000, dueSoonDays = 30, asOfDate = LocalDate.parse("2026-10-12")
    ))
    check(noUsage.estimatedDueDate == LocalDate.parse("2026-07-01"))
    check(noUsage.status == MaintenanceStatus.OVERDUE)

    val leapDate = calculateOilSchedule(OilScheduleInput(
        serviceDate = LocalDate.parse("2024-02-29"), serviceOdometer = 0,
        currentOdometer = 0, currentOdometerDate = LocalDate.parse("2024-02-29"),
        distanceInterval = null, timeIntervalMonths = 12, averageDistancePerDay = null,
        dueSoonDistance = 1_000, dueSoonDays = 30, asOfDate = LocalDate.parse("2024-03-01")
    ))
    check(leapDate.timeDeadlineDate == LocalDate.parse("2025-02-28"))

    println("PASS: 10 executed Kotlin domain smoke assertions (fuel economy and maintenance)")
}
