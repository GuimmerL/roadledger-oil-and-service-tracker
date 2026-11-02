package ca.gllavoie.vehicletracker.data.repository

import ca.gllavoie.vehicletracker.data.local.OdometerReadingEntity
import ca.gllavoie.vehicletracker.data.local.VehicleDao
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

sealed interface OdometerRecordResult {
    data class Saved(val reading: OdometerReadingEntity) : OdometerRecordResult
    data class NeedsConfirmation(val reason: String) : OdometerRecordResult
}

interface OdometerRepository {
    fun observeReadings(vehicleId: String): Flow<List<OdometerReadingEntity>>
    suspend fun addReading(
        vehicleId: String,
        value: Long,
        date: LocalDate,
        notes: String?,
        confirmAnomaly: Boolean = false
    ): OdometerRecordResult
}

class RoomOdometerRepository(
    private val dao: VehicleDao,
    private val vehicleRepository: VehicleRepository,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val today: () -> LocalDate = { LocalDate.now(zoneId) }
) : OdometerRepository {
    override fun observeReadings(vehicleId: String): Flow<List<OdometerReadingEntity>> =
        dao.observeOdometerReadings(vehicleId)

    override suspend fun addReading(
        vehicleId: String,
        value: Long,
        date: LocalDate,
        notes: String?,
        confirmAnomaly: Boolean
    ): OdometerRecordResult {
        require(value >= 0L) { "Odometer cannot be negative." }
        require(!date.isAfter(today())) { "Odometer date cannot be in the future." }
        val vehicle = requireNotNull(vehicleRepository.getVehicle(vehicleId)) { "Vehicle was not found." }
        require(vehicle.archivedAtUtc == null) { "Restore this vehicle before adding an odometer reading." }
        val unit = vehicle.odometerUnit
        require(unit == "KM" || unit == "MILE") { "Vehicle odometer unit is invalid." }

        val history = dao.observeOdometerReadings(vehicleId).first()
        val timestamp = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val latest = history.maxByOrNull(OdometerReadingEntity::recordedAtUtc)
        val sameDateReadings = history.filter {
            Instant.ofEpochMilli(it.recordedAtUtc).atZone(zoneId).toLocalDate() == date
        }
        if (sameDateReadings.any { it.value == value }) {
            throw IllegalArgumentException("This odometer reading is already recorded for that date.")
        }

        val issues = buildList {
            if (latest != null && timestamp < latest.recordedAtUtc) add("This reading predates a more recent odometer entry.")
            if (sameDateReadings.isNotEmpty()) add("Another odometer reading already exists for this date.")
            if (latest != null && value < latest.value) add("This value is lower than the latest reading; confirm an odometer reset or rollover.")
        }
        if (issues.isNotEmpty() && !confirmAnomaly) return OdometerRecordResult.NeedsConfirmation(issues.joinToString(" "))

        val storedTimestamp = if (latest != null && timestamp == latest.recordedAtUtc) {
            Math.addExact(timestamp, 1L)
        } else {
            timestamp
        }
        val now = System.currentTimeMillis()
        val reading = OdometerReadingEntity(
            vehicleId = vehicleId,
            value = value,
            unit = unit,
            recordedAtUtc = storedTimestamp,
            sourceType = if (issues.isEmpty()) "MANUAL" else "MANUAL_CONFIRMED",
            notes = listOfNotNull(notes?.trim()?.ifEmpty { null }, issues.takeIf { confirmAnomaly }?.joinToString(" ")).joinToString(" · ").ifEmpty { null },
            createdAtUtc = now,
            updatedAtUtc = now
        )
        dao.insertOdometerReading(reading)
        return OdometerRecordResult.Saved(reading)
    }
}
