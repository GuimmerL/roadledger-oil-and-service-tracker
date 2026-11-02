package ca.gllavoie.vehicletracker.data.repository

import ca.gllavoie.vehicletracker.data.local.FuelFillUpEntity
import ca.gllavoie.vehicletracker.data.local.OdometerReadingEntity
import ca.gllavoie.vehicletracker.data.local.VehicleDao
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

sealed interface FuelRecordResult {
    data class Saved(val fillUp: FuelFillUpEntity) : FuelRecordResult
    data class NeedsConfirmation(val reason: String) : FuelRecordResult
}

interface FuelRepository {
    fun observeFillUps(vehicleId: String): Flow<List<FuelFillUpEntity>>
    suspend fun addFillUp(fillUp: FuelFillUpEntity, confirmAnomaly: Boolean = false): FuelRecordResult
}

class RoomFuelRepository(
    private val dao: VehicleDao,
    private val vehicleRepository: VehicleRepository,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val now: () -> Long = System::currentTimeMillis
) : FuelRepository {
    override fun observeFillUps(vehicleId: String): Flow<List<FuelFillUpEntity>> = dao.observeFuelFillUps(vehicleId)

    override suspend fun addFillUp(fillUp: FuelFillUpEntity, confirmAnomaly: Boolean): FuelRecordResult {
        val vehicle = requireNotNull(vehicleRepository.getVehicle(fillUp.vehicleId)) { "Vehicle was not found." }
        require(vehicle.archivedAtUtc == null) { "Restore this vehicle before adding fuel records." }
        require(fillUp.filledAtUtc in 0..now()) { "Fuel date cannot be in the future." }
        require(fillUp.createdAtUtc >= 0L && fillUp.updatedAtUtc >= fillUp.createdAtUtc) { "Fuel record timestamps are invalid." }
        require(fillUp.odometerValue >= 0L) { "Fuel odometer cannot be negative." }
        require(fillUp.odometerUnit == vehicle.odometerUnit) { "Fuel odometer unit must match the vehicle." }
        require(fillUp.odometerUnit == "KM" || fillUp.odometerUnit == "MILE") { "Vehicle odometer unit is invalid." }
        require(fillUp.volumeMillilitres > 0L) { "Fuel volume must be greater than zero." }
        require(fillUp.totalCostMinor == null || fillUp.totalCostMinor >= 0L) { "Fuel cost cannot be negative." }
        require(fillUp.currencyCode.matches(Regex("[A-Z]{3}"))) { "Currency must use a three-letter code such as CAD or USD." }

        val history = dao.observeOdometerReadings(fillUp.vehicleId).first()
        val latest = history.maxByOrNull(OdometerReadingEntity::recordedAtUtc)
        val sameDate = history.any {
            java.time.Instant.ofEpochMilli(it.recordedAtUtc).atZone(zoneId).toLocalDate() ==
                java.time.Instant.ofEpochMilli(fillUp.filledAtUtc).atZone(zoneId).toLocalDate()
        }
        val anomalies = buildList {
            if (latest != null && fillUp.filledAtUtc < latest.recordedAtUtc) add("This fill-up date predates a newer odometer entry.")
            if (latest != null && fillUp.odometerValue < latest.value) add("This odometer is lower than the latest reading; confirm a reset or rollover.")
            if (sameDate) add("An odometer entry already exists for this date.")
        }
        if (anomalies.isNotEmpty() && !confirmAnomaly) return FuelRecordResult.NeedsConfirmation(anomalies.joinToString(" "))

        val recordId = fillUp.id
        val timestamp = if (sameDate && latest != null) Math.addExact(maxOf(fillUp.filledAtUtc, latest.recordedAtUtc), 1L) else fillUp.filledAtUtc
        val saved = fillUp.copy(
            filledAtUtc = timestamp,
            odometerReadingId = java.util.UUID.randomUUID().toString(),
            updatedAtUtc = now()
        )
        val reading = OdometerReadingEntity(
            id = saved.odometerReadingId!!,
            vehicleId = saved.vehicleId,
            value = saved.odometerValue,
            unit = saved.odometerUnit,
            recordedAtUtc = timestamp,
            sourceType = if (anomalies.isEmpty()) "FUEL_FILL_UP" else "FUEL_FILL_UP_CONFIRMED",
            sourceRecordId = recordId,
            notes = anomalies.takeIf { confirmAnomaly }?.joinToString(" "),
            createdAtUtc = saved.createdAtUtc,
            updatedAtUtc = saved.updatedAtUtc
        )
        dao.addFuelRecordAndOdometer(saved, reading)
        return FuelRecordResult.Saved(saved)
    }
}
