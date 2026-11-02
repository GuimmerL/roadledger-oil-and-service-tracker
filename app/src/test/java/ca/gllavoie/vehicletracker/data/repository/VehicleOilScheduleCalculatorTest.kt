package ca.gllavoie.vehicletracker.data.repository

import ca.gllavoie.vehicletracker.data.local.OdometerReadingEntity
import ca.gllavoie.vehicletracker.data.local.OilChangeEntity
import ca.gllavoie.vehicletracker.data.local.VehicleEntity
import ca.gllavoie.vehicletracker.data.local.VehicleSpecificationEntity
import ca.gllavoie.vehicletracker.data.local.VehicleSpecificationOverrideEntity
import ca.gllavoie.vehicletracker.domain.calculation.MaintenanceStatus
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VehicleOilScheduleCalculatorTest {
    private val vehicle = VehicleEntity(id = "car", make = "Toyota", model = "Prius", createdAtUtc = 1L, updatedAtUtc = 1L)
    private val service = OilChangeEntity(
        id = "service", vehicleId = "car", serviceDate = "2026-01-01", odometerValue = 1_000L,
        odometerUnit = "KM", createdAtUtc = 1L, updatedAtUtc = 1L
    )
    private val readings = listOf(
        OdometerReadingEntity(
            id = "reading", vehicleId = "car", value = 1_500L, unit = "KM",
            recordedAtUtc = LocalDate.of(2026, 10, 20).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            sourceType = "MANUAL", createdAtUtc = 1L, updatedAtUtc = 1L
        )
    )

    @Test fun doesNotUseImportedIntervalUntilUserVerifiesIt() {
        val aiCatalog = spec("AI_GENERATED_UNVERIFIED")

        val schedule = calculateVehicleOilSchedule(
            vehicle, service, readings, override = null, catalog = aiCatalog,
            zoneId = ZoneOffset.UTC, asOfDate = LocalDate.of(2026, 10, 20)
        )

        assertEquals(MaintenanceStatus.UNAVAILABLE, schedule.status)
    }

    @Test fun verifiedImportedIntervalDrivesDueSoonSchedule() {
        val verifiedCatalog = spec("USER_VERIFIED")

        val schedule = calculateVehicleOilSchedule(
            vehicle, service, readings, override = null, catalog = verifiedCatalog,
            zoneId = ZoneOffset.UTC, asOfDate = LocalDate.of(2026, 10, 20)
        )

        assertEquals(MaintenanceStatus.DUE_SOON, schedule.status)
        assertEquals(2_000L, schedule.nextDueOdometer)
    }

    @Test fun officialIntervalResearchRequiresUserConfirmationBeforeScheduling() {
        val researchedCatalog = spec("OFFICIAL_INTERVAL_RESEARCHED")

        val schedule = calculateVehicleOilSchedule(
            vehicle, service, readings, override = null, catalog = researchedCatalog,
            zoneId = ZoneOffset.UTC, asOfDate = LocalDate.of(2026, 10, 20)
        )

        assertEquals(MaintenanceStatus.UNAVAILABLE, schedule.status)
    }

    @Test fun severeModeSelectsVerifiedSevereCatalogInterval() {
        val catalog = spec("USER_VERIFIED").copy(severeDistanceInterval = 500)

        val normal = calculateVehicleOilSchedule(
            vehicle, service, readings, override = null, catalog = catalog,
            zoneId = ZoneOffset.UTC, asOfDate = LocalDate.of(2026, 10, 20)
        )
        val severe = calculateVehicleOilSchedule(
            vehicle.copy(oilIntervalMode = "SEVERE"), service, readings, override = null, catalog = catalog,
            zoneId = ZoneOffset.UTC, asOfDate = LocalDate.of(2026, 10, 20)
        )

        assertEquals(2_000L, normal.nextDueOdometer)
        assertEquals(1_500L, severe.nextDueOdometer)
        assertEquals(0L, severe.distanceRemaining)
        assertEquals(MaintenanceStatus.DUE, severe.status)
    }

    @Test fun serviceAndVehicleManualIntervalsStillTakePrecedenceOverSevereCatalogMode() {
        val catalog = spec("USER_VERIFIED").copy(severeDistanceInterval = 500)
        val severeVehicle = vehicle.copy(oilIntervalMode = "SEVERE")
        val vehicleOverride = VehicleSpecificationOverrideEntity(
            id = "override", vehicleId = vehicle.id, distanceInterval = 750,
            distanceUnit = "KM", updatedAtUtc = 1L
        )
        val vehicleSchedule = calculateVehicleOilSchedule(
            severeVehicle, service, readings, override = vehicleOverride, catalog = catalog,
            zoneId = ZoneOffset.UTC, asOfDate = LocalDate.of(2026, 10, 20)
        )
        val serviceSchedule = calculateVehicleOilSchedule(
            severeVehicle, service.copy(distanceIntervalOverride = 900), readings, override = vehicleOverride, catalog = catalog,
            zoneId = ZoneOffset.UTC, asOfDate = LocalDate.of(2026, 10, 20)
        )

        assertEquals(1_750L, vehicleSchedule.nextDueOdometer)
        assertEquals(1_900L, serviceSchedule.nextDueOdometer)
    }

    @Test fun convertsVerifiedImportedIntervalToVehicleOdometerUnit() {
        val mileVehicle = vehicle.copy(odometerUnit = "MILE")
        val mileService = service.copy(odometerValue = 1_000L, odometerUnit = "MILE")
        val mileReading = readings.map { it.copy(value = 1_500L, unit = "MILE") }
        val metricCatalog = VehicleSpecificationEntity(
            id = "metric", make = "Toyota", model = "Prius", distanceInterval = 16_094,
            distanceUnit = "KM", verificationStatus = "USER_VERIFIED", createdAtUtc = 1L, updatedAtUtc = 1L
        )

        val schedule = calculateVehicleOilSchedule(
            mileVehicle, mileService, mileReading, override = null, catalog = metricCatalog,
            zoneId = ZoneOffset.UTC, asOfDate = LocalDate.of(2026, 10, 20)
        )

        assertEquals(11_000L, schedule.nextDueOdometer)
    }

    @Test fun oneOdometerReadingWithoutStartingDailyUseCannotProjectADueDate() {
        val schedule = calculateVehicleOilSchedule(
            vehicle, service, readings, override = null, catalog = spec("USER_VERIFIED"),
            zoneId = ZoneOffset.UTC, asOfDate = LocalDate.of(2026, 10, 20)
        )

        assertEquals(2_000L, schedule.nextDueOdometer)
        assertNull(schedule.estimatedDueDate)
    }

    @Test fun startingDailyUsageProvidesProjectionUntilReadingsCanEstimateIt() {
        val vehicleWithBaseline = vehicle.copy(startingDailyUsageKmPerDay = 50.0)
        val schedule = calculateVehicleOilSchedule(
            vehicleWithBaseline, service, readings, override = null, catalog = spec("USER_VERIFIED"),
            zoneId = ZoneOffset.UTC, asOfDate = LocalDate.of(2026, 10, 20)
        )

        assertEquals(LocalDate.of(2026, 10, 30), schedule.estimatedDueDate)
    }

    @Test fun updatedDatedReadingsRecalculateDailyUsageAndOverrideStartingValue() {
        val first = reading("first", 1_000L, LocalDate.of(2026, 10, 1))
        val tenDaysLater = reading("update-1", 1_500L, LocalDate.of(2026, 10, 11))
        val twentyDaysLater = reading("update-2", 1_600L, LocalDate.of(2026, 10, 21))

        assertEquals(50.0, estimateDailyDistancePerDay(listOf(first, tenDaysLater), "KM", ZoneOffset.UTC, 80.0)!!, 0.0001)
        assertEquals(30.0, estimateDailyDistancePerDay(listOf(first, tenDaysLater, twentyDaysLater), "KM", ZoneOffset.UTC, 80.0)!!, 0.0001)
    }

    private fun reading(id: String, value: Long, date: LocalDate) = OdometerReadingEntity(
        id = id, vehicleId = "car", value = value, unit = "KM",
        recordedAtUtc = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        sourceType = "MANUAL", createdAtUtc = 1L, updatedAtUtc = 1L
    )

    private fun spec(status: String) = VehicleSpecificationEntity(
        id = "spec", make = "Toyota", model = "Prius", distanceInterval = 1_000,
        distanceUnit = "KM", verificationStatus = status, createdAtUtc = 1L, updatedAtUtc = 1L
    )
}
