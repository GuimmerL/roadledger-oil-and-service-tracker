package ca.gllavoie.vehicletracker.domain.calculation

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculationTest {
    @Test fun fullTankExampleCalculatesLitresPer100Km() {
        val result = calculateEconomy(
            FillUp(10_000.0, DistanceUnit.KILOMETRE, 50.0, VolumeUnit.LITRE, true),
            FillUp(10_520.0, DistanceUnit.KILOMETRE, 46.0, VolumeUnit.LITRE, true),
            EconomyUnit.L_PER_100_KM
        )
        assertEquals(46.0 / 520.0 * 100.0, result!!, 0.0001)
    }

    @Test fun fuelEconomyConvertsUsAndImperialGallonsSeparately() {
        val us = calculateEconomy(
            FillUp(0.0, DistanceUnit.MILE, 10.0, VolumeUnit.US_GALLON, true),
            FillUp(300.0, DistanceUnit.MILE, 10.0, VolumeUnit.US_GALLON, true),
            EconomyUnit.MPG_US
        )
        val imperial = calculateEconomy(
            FillUp(0.0, DistanceUnit.MILE, 10.0, VolumeUnit.IMPERIAL_GALLON, true),
            FillUp(300.0, DistanceUnit.MILE, 10.0, VolumeUnit.IMPERIAL_GALLON, true),
            EconomyUnit.MPG_IMPERIAL
        )
        assertEquals(30.0, us!!, 0.0001)
        assertEquals(30.0, imperial!!, 0.0001)
    }

    @Test fun partialFillsAreAggregatedBetweenFullTankEndpoints() {
        val result = calculateEconomyFromSeries(
            listOf(
                FillUp(1_000.0, DistanceUnit.KILOMETRE, 40.0, VolumeUnit.LITRE, true),
                FillUp(1_200.0, DistanceUnit.KILOMETRE, 12.0, VolumeUnit.LITRE, false),
                FillUp(1_400.0, DistanceUnit.KILOMETRE, 9.0, VolumeUnit.LITRE, false),
                FillUp(1_500.0, DistanceUnit.KILOMETRE, 10.0, VolumeUnit.LITRE, true)
            ),
            EconomyUnit.KM_PER_LITRE
        )
        assertEquals(500.0 / 31.0, result!!, 0.0001)
    }

    @Test fun incompleteFuelSeriesIsUnavailable() {
        assertNull(calculateEconomyFromSeries(listOf(FillUp(1.0, DistanceUnit.MILE, 5.0, VolumeUnit.LITRE, false)), EconomyUnit.L_PER_100_KM))
    }

    @Test fun seriesAddsPartialAndEndingRefillButNotInitialTank() {
        val result = calculateEconomyFromSeries(
            listOf(
                FillUp(0.0, DistanceUnit.KILOMETRE, 50.0, VolumeUnit.LITRE, true),
                FillUp(100.0, DistanceUnit.KILOMETRE, 10.0, VolumeUnit.LITRE, false),
                FillUp(200.0, DistanceUnit.KILOMETRE, 10.0, VolumeUnit.LITRE, true)
            ),
            EconomyUnit.L_PER_100_KM
        )
        assertEquals(10.0, result!!, 0.0001)
    }

    @Test fun singleIntervalUsesEndingRefillAndConvertsUnits() {
        val result = calculateEconomy(
            FillUp(0.0, DistanceUnit.MILE, 40.0, VolumeUnit.LITRE, true),
            FillUp(300.0, DistanceUnit.MILE, 10.0, VolumeUnit.US_GALLON, true),
            EconomyUnit.MPG_US
        )
        assertEquals(30.0, result!!, 0.0001)
    }

    @Test fun seriesAllowsDifferentDistanceUnitsAndRejectsOdometerRollback() {
        val mixedUnits = listOf(
            FillUp(0.0, DistanceUnit.KILOMETRE, 10.0, VolumeUnit.LITRE, true),
            FillUp(100.0, DistanceUnit.KILOMETRE, 10.0, VolumeUnit.LITRE, false),
            FillUp(124.274238, DistanceUnit.MILE, 10.0, VolumeUnit.LITRE, true)
        )
        assertEquals(10.0, calculateEconomyFromSeries(mixedUnits, EconomyUnit.L_PER_100_KM)!!, 0.001)
        val rollback = mixedUnits.toMutableList().also { it[1] = it[1].copy(odometer = -1.0) }
        assertNull(calculateEconomyFromSeries(rollback, EconomyUnit.L_PER_100_KM))
    }

    @Test fun invalidIntervalsReturnUnavailable() {
        assertNull(calculateEconomy(
            FillUp(100.0, DistanceUnit.KILOMETRE, 10.0, VolumeUnit.LITRE, true),
            FillUp(100.0, DistanceUnit.KILOMETRE, 10.0, VolumeUnit.LITRE, true),
            EconomyUnit.L_PER_100_KM
        ))
    }

    @Test fun imperialGallonConversionUsesCorrectFactor() {
        assertEquals(4.54609, volumeToLitres(1.0, VolumeUnit.IMPERIAL_GALLON), 0.000001)
        assertEquals(3.785411784, volumeToLitres(1.0, VolumeUnit.US_GALLON), 0.000001)
    }

    @Test fun zeroIntervalAndMissingOdometerAreUnavailable() {
        val zero = calculateOilSchedule(input(
            currentOdometer = 0, serviceOdometer = 0, distanceInterval = 0,
            timeMonths = null, averagePerDay = 1.0
        ))
        val missing = calculateOilSchedule(input(currentOdometer = null, averagePerDay = 100.0))
        assertEquals(MaintenanceStatus.UNAVAILABLE, zero.status)
        assertEquals(LocalDate.parse("2026-07-01"), missing.estimatedDueDate)
        assertTrue(missing.assumptions.any { it.contains("missing") })
    }

    @Test fun staleOdometerAndLeapMonthEndAreDisclosed() {
        val stale = calculateOilSchedule(input(currentReadingDate = LocalDate.parse("2026-01-01"), asOf = LocalDate.parse("2026-10-12")))
        val leap = calculateOilSchedule(input(
            serviceDate = LocalDate.parse("2024-08-31"), currentOdometer = 1_000,
            currentReadingDate = LocalDate.parse("2024-08-31"), averagePerDay = 1.0,
            timeMonths = 6, asOf = LocalDate.parse("2024-09-01")
        ))
        assertTrue(stale.assumptions.any { it.contains("stale") })
        assertEquals(LocalDate.parse("2025-02-28"), leap.timeDeadlineDate)
    }

    @Test fun dueSoonThresholdIsApplied() {
        val schedule = calculateOilSchedule(input(
            serviceDate = LocalDate.parse("2026-10-01"), currentOdometer = 7_500,
            currentReadingDate = LocalDate.parse("2026-10-01"), averagePerDay = 100.0,
            timeMonths = 6, asOf = LocalDate.parse("2026-10-01")
        ))
        assertEquals(MaintenanceStatus.DUE_SOON, schedule.status)
    }

    @Test fun invalidFuelQuantitiesReturnUnavailable() {
        assertNull(calculateEconomy(
            FillUp(100.0, DistanceUnit.KILOMETRE, 0.0, VolumeUnit.LITRE, true),
            FillUp(200.0, DistanceUnit.KILOMETRE, 10.0, VolumeUnit.LITRE, true),
            EconomyUnit.L_PER_100_KM
        ))
    }

    @Test fun kilometresPerLitreFormulaIsCorrect() {
        val result = calculateEconomy(
            FillUp(0.0, DistanceUnit.KILOMETRE, 40.0, VolumeUnit.LITRE, true),
            FillUp(520.0, DistanceUnit.KILOMETRE, 46.0, VolumeUnit.LITRE, true),
            EconomyUnit.KM_PER_LITRE
        )
        assertEquals(520.0 / 46.0, result!!, 0.0001)
    }

    @Test fun timeLimitCanMakeServiceOverdueBeforeMileage() {
        val schedule = calculateOilSchedule(input(
            serviceDate = LocalDate.parse("2025-01-01"), currentOdometer = 1_000,
            currentReadingDate = LocalDate.parse("2026-10-01"), averagePerDay = 1.0,
            timeMonths = 6, asOf = LocalDate.parse("2026-10-12")
        ))
        assertEquals(MaintenanceStatus.OVERDUE, schedule.status)
        assertEquals(DueLimit.TIME, schedule.determiningLimit)
    }

    @Test fun milesAndKilometresUseUnitSpecificIntervals() {
        val kmSchedule = calculateOilSchedule(input(currentOdometer = 7_500, averagePerDay = 100.0, distanceInterval = 8_000))
        val miSchedule = calculateOilSchedule(input(
            currentOdometer = 4_660, currentReadingDate = LocalDate.parse("2026-10-01"),
            averagePerDay = 62.137, distanceInterval = 4_971
        ))
        assertEquals(8_000L, kmSchedule.nextDueOdometer)
        assertEquals(4_971L, miSchedule.nextDueOdometer)
    }

    @Test fun serviceDateIsTheTimeDeadlineAnchor() {
        val schedule = calculateOilSchedule(input(
            serviceDate = LocalDate.parse("2024-02-29"), currentOdometer = 0,
            currentReadingDate = LocalDate.parse("2024-02-29"), averagePerDay = 1.0,
            timeMonths = 12, asOf = LocalDate.parse("2024-03-01")
        ))
        assertEquals(LocalDate.parse("2025-02-28"), schedule.timeDeadlineDate)
    }

    @Test fun averageDailyDistanceAndRemainingDistanceDetermineEstimate() {
        val schedule = calculateOilSchedule(input(
            serviceDate = LocalDate.parse("2026-10-01"), currentOdometer = 7_000,
            currentReadingDate = LocalDate.parse("2026-10-01"), averagePerDay = 125.0,
            timeMonths = 6, asOf = LocalDate.parse("2026-10-01")
        ))
        assertEquals(LocalDate.parse("2026-10-09"), schedule.mileageDeadlineDateEstimate)
        assertEquals(1_000L, schedule.distanceRemaining)
    }

    @Test fun timeLimitOverdueWithUnknownMileagePredictionUsesTimeDeadline() {
        val schedule = calculateOilSchedule(input(
            serviceDate = LocalDate.parse("2026-01-01"), currentOdometer = null,
            currentReadingDate = null, averagePerDay = null, timeMonths = 6,
            asOf = LocalDate.parse("2026-07-02")
        ))
        assertEquals(MaintenanceStatus.OVERDUE, schedule.status)
        assertEquals(LocalDate.parse("2026-07-01"), schedule.estimatedDueDate)
    }

    @Test fun zeroAverageUsesTimeLimitButNoIntervalsIsUnavailable() {
        val timeOnly = calculateOilSchedule(input(currentOdometer = 1_000, averagePerDay = 0.0, timeMonths = 6))
        val noIntervals = calculateOilSchedule(input(currentOdometer = 1_000, averagePerDay = 0.0, timeMonths = null, distanceInterval = null))
        assertEquals(LocalDate.parse("2026-07-01"), timeOnly.estimatedDueDate)
        assertEquals(MaintenanceStatus.UNAVAILABLE, noIntervals.status)
    }

    @Test fun exactTimeDeadlineIsDueButNotOverdue() {
        val schedule = calculateOilSchedule(input(
            serviceDate = LocalDate.parse("2026-01-01"), currentOdometer = 0,
            currentReadingDate = LocalDate.parse("2026-01-01"), averagePerDay = 1.0,
            timeMonths = 6, asOf = LocalDate.parse("2026-07-01")
        ))
        assertEquals(MaintenanceStatus.DUE, schedule.status)
    }

    @Test fun missingTimeIntervalCanStillUseMileageEstimate() {
        val schedule = calculateOilSchedule(input(
            serviceDate = LocalDate.parse("2026-10-01"), currentOdometer = 7_000,
            currentReadingDate = LocalDate.parse("2026-10-01"), averagePerDay = 100.0,
            timeMonths = null, asOf = LocalDate.parse("2026-10-01")
        ))
        assertEquals(LocalDate.parse("2026-10-11"), schedule.estimatedDueDate)
        assertEquals(DueLimit.MILEAGE, schedule.determiningLimit)
    }

    @Test fun nonFiniteUsageDoesNotProduceDateEstimate() {
        val schedule = calculateOilSchedule(input(currentOdometer = 7_000, averagePerDay = Double.NaN))
        assertNull(schedule.mileageDeadlineDateEstimate)
    }

    @Test fun odometerReadingsOlderThanCurrentDateFlagStaleness() {
        val schedule = calculateOilSchedule(input(
            currentReadingDate = LocalDate.parse("2026-01-01"), asOf = LocalDate.parse("2026-10-12")
        ))
        assertTrue(schedule.assumptions.any { it.contains("stale") })
    }

    @Test fun inputUnitConversionConstantsAreStable() {
        assertEquals(1.609344, distanceToKilometres(1.0, DistanceUnit.MILE), 0.000001)
        assertEquals(1.0, distanceToKilometres(1.0, DistanceUnit.KILOMETRE), 0.000001)
    }

    @Test fun futureTimeLimitProducesPositiveDaysRemaining() {
        val schedule = calculateOilSchedule(input(
            serviceDate = LocalDate.parse("2026-01-01"), currentOdometer = 0,
            currentReadingDate = LocalDate.parse("2026-10-01"), averagePerDay = 0.0,
            timeMonths = 6, asOf = LocalDate.parse("2026-06-01")
        ))
        assertEquals(30L, schedule.daysRemaining)
    }

    @Test fun negativeRemainingDistanceMeansMileageOverdue() {
        val schedule = calculateOilSchedule(input(currentOdometer = 8_001, averagePerDay = 10.0))
        assertEquals(MaintenanceStatus.OVERDUE, schedule.status)
        assertEquals(-1L, schedule.distanceRemaining)
    }

    @Test fun exactMileageDeadlineIsDueButNotOverdue() {
        val schedule = calculateOilSchedule(input(
            serviceDate = LocalDate.parse("2026-10-01"), currentOdometer = 8_000,
            currentReadingDate = LocalDate.parse("2026-10-01"), averagePerDay = 10.0,
            timeMonths = null, asOf = LocalDate.parse("2026-10-01")
        ))
        assertEquals(MaintenanceStatus.DUE, schedule.status)
    }

    @Test fun currentOdometerBelowServiceOdometerIsFlaggedAndMileageEstimateOmitted() {
        val schedule = calculateOilSchedule(input(serviceOdometer = 1_000, currentOdometer = 100, averagePerDay = 10.0))
        assertNull(schedule.distanceRemaining)
        assertNull(schedule.mileageDeadlineDateEstimate)
        assertTrue(schedule.assumptions.any { it.contains("below the last service") })
    }

    @Test fun invalidIntervalDoesNotBecomeAnImmediateDeadline() {
        val schedule = calculateOilSchedule(input(currentOdometer = 1_000, distanceInterval = 0, timeMonths = null))
        assertNull(schedule.nextDueOdometer)
        assertEquals(MaintenanceStatus.UNAVAILABLE, schedule.status)
        assertTrue(schedule.assumptions.any { it.contains("greater than zero") })
    }

    @Test fun distanceDeadlineOverflowIsFlaggedWithoutWrapping() {
        val schedule = calculateOilSchedule(input(
            serviceDate = LocalDate.parse("2026-10-01"), serviceOdometer = Long.MAX_VALUE - 1_000,
            currentOdometer = Long.MAX_VALUE - 1_000, currentReadingDate = LocalDate.parse("2026-10-01"), averagePerDay = 1.0,
            distanceInterval = 8_000, timeMonths = null
        ))
        assertNull(schedule.nextDueOdometer)
        assertTrue(schedule.assumptions.any { it.contains("exceeds supported range") })
    }

    @Test fun tooLargeMonthIntervalDoesNotCrashScheduleCalculation() {
        val schedule = calculateOilSchedule(input(
            serviceDate = LocalDate.parse("2026-10-01"), currentOdometer = null,
            currentReadingDate = null, averagePerDay = null, distanceInterval = null,
            timeMonths = Long.MAX_VALUE
        ))
        assertNull(schedule.timeDeadlineDate)
        assertTrue(schedule.assumptions.any { it.contains("supported calendar range") })
    }

    @Test fun incompleteFuelIntervalsNeverInventEconomy() {
        val badOrder = listOf(
            FillUp(1_000.0, DistanceUnit.KILOMETRE, 20.0, VolumeUnit.LITRE, true),
            FillUp(900.0, DistanceUnit.KILOMETRE, 20.0, VolumeUnit.LITRE, false),
            FillUp(1_100.0, DistanceUnit.KILOMETRE, 20.0, VolumeUnit.LITRE, true)
        )
        assertNull(calculateEconomyFromSeries(badOrder, EconomyUnit.L_PER_100_KM))
    }

    @Test fun outputsAreEquivalentForSamePhysicalFuelEconomy() {
        val start = FillUp(0.0, DistanceUnit.KILOMETRE, 30.0, VolumeUnit.LITRE, true)
        val end = FillUp(100.0, DistanceUnit.KILOMETRE, 10.0, VolumeUnit.LITRE, true)
        val lPer100 = calculateEconomy(start, end, EconomyUnit.L_PER_100_KM)!!
        val kmPerL = calculateEconomy(start, end, EconomyUnit.KM_PER_LITRE)!!
        assertEquals(10.0, lPer100, 0.0001)
        assertEquals(10.0, kmPerL, 0.0001)
    }

    @Test fun multipleFullTankWindowsUseTheFirstAdjacentPair() {
        val series = listOf(
            FillUp(0.0, DistanceUnit.KILOMETRE, 30.0, VolumeUnit.LITRE, true),
            FillUp(100.0, DistanceUnit.KILOMETRE, 10.0, VolumeUnit.LITRE, true),
            FillUp(200.0, DistanceUnit.KILOMETRE, 10.0, VolumeUnit.LITRE, true)
        )
        assertEquals(10.0, calculateEconomyFromSeries(series, EconomyUnit.L_PER_100_KM)!!, 0.0001)
    }

    @Test fun distantFutureScheduleRemainsUpToDate() {
        val schedule = calculateOilSchedule(input(
            serviceDate = LocalDate.parse("2026-10-01"), currentOdometer = 0,
            currentReadingDate = LocalDate.parse("2026-10-01"), averagePerDay = 10.0,
            timeMonths = 60, asOf = LocalDate.parse("2026-10-02")
        ))
        assertEquals(MaintenanceStatus.UP_TO_DATE, schedule.status)
    }

    @Test fun intervalOverrideIsRepresentedByCalculationInput() {
        val overridden = input(currentOdometer = 4_000, distanceInterval = 5_000)
        assertEquals(5_000L, calculateOilSchedule(overridden).nextDueOdometer)
    }

    @Test fun serviceWithNoUsageAndNoTimeLimitDoesNotCreateDueDate() {
        val schedule = calculateOilSchedule(input(
            currentOdometer = 0, averagePerDay = null, timeMonths = null, distanceInterval = 8_000
        ))
        assertNull(schedule.estimatedDueDate)
        assertEquals(DueLimit.UNKNOWN, schedule.determiningLimit)
    }

    @Test fun fillUpsWithDifferentVolumeUnitsAggregateToLitres() {
        val series = listOf(
            FillUp(0.0, DistanceUnit.KILOMETRE, 1.0, VolumeUnit.LITRE, true),
            FillUp(50.0, DistanceUnit.KILOMETRE, 1.0, VolumeUnit.US_GALLON, false),
            FillUp(100.0, DistanceUnit.KILOMETRE, 1.0, VolumeUnit.IMPERIAL_GALLON, true)
        )
        val expected = (3.785411784 + 4.54609) / 100.0 * 100.0
        assertEquals(expected, calculateEconomyFromSeries(series, EconomyUnit.L_PER_100_KM)!!, 0.0001)
    }

    @Test fun serviceStatusIsVehicleSpecificThroughIndependentInputs() {
        val first = calculateOilSchedule(input(currentOdometer = 7_000, distanceInterval = 8_000))
        val second = calculateOilSchedule(input(currentOdometer = 1_000, distanceInterval = 12_000))
        assertEquals(8_000L, first.nextDueOdometer)
        assertEquals(12_000L, second.nextDueOdometer)
    }

    @Test fun mileageLimitMayBeEarlierThanTimeLimit() {
        val schedule = calculateOilSchedule(input(
            serviceDate = LocalDate.parse("2026-01-01"), currentOdometer = 7_500,
            currentReadingDate = LocalDate.parse("2026-10-01"), averagePerDay = 100.0,
            timeMonths = 12, asOf = LocalDate.parse("2026-10-01")
        ))
        assertEquals(LocalDate.parse("2026-10-06"), schedule.estimatedDueDate)
        assertEquals(DueLimit.MILEAGE, schedule.determiningLimit)
        assertEquals(MaintenanceStatus.DUE_SOON, schedule.status)
    }

    @Test fun timeLimitMayBeEarlierThanMileageLimitAndHandlesMonthEnd() {
        val schedule = calculateOilSchedule(input(
            serviceDate = LocalDate.parse("2026-01-31"), currentOdometer = 1_000,
            currentReadingDate = LocalDate.parse("2026-01-31"), averagePerDay = 1.0,
            timeMonths = 1, asOf = LocalDate.parse("2026-02-01")
        ))
        assertEquals(LocalDate.parse("2026-02-28"), schedule.timeDeadlineDate)
        assertEquals(DueLimit.TIME, schedule.determiningLimit)
    }

    @Test fun overdueByEitherLimitIsOverdueAndZeroUsageUsesTimeDeadline() {
        val schedule = calculateOilSchedule(input(currentOdometer = 11_000, averagePerDay = 0.0))
        assertEquals(MaintenanceStatus.OVERDUE, schedule.status)
        assertEquals(LocalDate.parse("2026-07-01"), schedule.estimatedDueDate)
        assertTrue(schedule.assumptions.any { it.contains("cannot be reliably predicted") })
    }

    private fun input(
        serviceDate: LocalDate = LocalDate.parse("2026-01-01"),
        serviceOdometer: Long = 0,
        currentOdometer: Long? = 7_500,
        currentReadingDate: LocalDate? = LocalDate.parse("2026-10-01"),
        averagePerDay: Double? = 100.0,
        distanceInterval: Long? = 8_000,
        timeMonths: Long? = 6,
        asOf: LocalDate = LocalDate.parse("2026-10-12")
    ) = OilScheduleInput(
        serviceDate = serviceDate,
        serviceOdometer = serviceOdometer,
        currentOdometer = currentOdometer,
        currentOdometerDate = currentReadingDate,
        distanceInterval = distanceInterval,
        timeIntervalMonths = timeMonths,
        averageDistancePerDay = averagePerDay,
        dueSoonDistance = 1_000,
        dueSoonDays = 30,
        asOfDate = asOf
    )
}
