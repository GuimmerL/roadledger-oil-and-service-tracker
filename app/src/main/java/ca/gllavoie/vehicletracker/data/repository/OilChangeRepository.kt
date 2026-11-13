package ca.gllavoie.vehicletracker.data.repository

import ca.gllavoie.vehicletracker.data.local.OilChangeEntity
import ca.gllavoie.vehicletracker.data.local.VehicleDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

interface OilChangeRepository {
    fun observeRecords(vehicleId: String): Flow<List<OilChangeEntity>>
    suspend fun save(record: OilChangeEntity)
}

class RoomOilChangeRepository(
    private val dao: VehicleDao,
    private val vehicleRepository: VehicleRepository,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val today: () -> LocalDate = { LocalDate.now(ZoneId.systemDefault()) }
) : OilChangeRepository {
    override fun observeRecords(vehicleId: String): Flow<List<OilChangeEntity>> = dao.observeOilChanges(vehicleId)

    override suspend fun save(record: OilChangeEntity) {
        val vehicle = requireNotNull(vehicleRepository.getVehicle(record.vehicleId)) { "Vehicle was not found." }
        require(vehicle.archivedAtUtc == null) { "Restore this vehicle before editing service records." }
        require(record.serviceDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) { "Enter a service date as YYYY-MM-DD." }
        val serviceDate = runCatching { LocalDate.parse(record.serviceDate) }.getOrElse {
            throw IllegalArgumentException("Enter a valid service date as YYYY-MM-DD.")
        }
        require(!serviceDate.isAfter(today())) { "A completed oil change cannot have a future date." }
        require(record.odometerValue >= 0L) { "Service odometer cannot be negative." }
        require(record.odometerUnit == vehicle.odometerUnit) { "Service odometer unit must match the vehicle." }
        require(record.odometerUnit == "KM" || record.odometerUnit == "MILE") { "Vehicle odometer unit is invalid." }
        require(record.quantityUsedMl == null || record.quantityUsedMl > 0) { "Oil quantity must be greater than zero." }
        require(record.distanceIntervalOverride == null || record.distanceIntervalOverride > 0) { "Distance interval must be greater than zero." }
        require(record.timeIntervalMonthsOverride == null || record.timeIntervalMonthsOverride > 0) { "Time interval must be greater than zero." }
        require(listOf(record.totalCostMinor, record.partsCostMinor, record.laborCostMinor).all { it == null || it >= 0L }) {
            "Service costs cannot be negative."
        }
        val currency = record.currencyCode.trim().uppercase()
        require(currency.matches(Regex("[A-Z]{3}"))) { "Currency must use a three-letter code such as CAD or USD." }
        require(record.serviceType.isNotBlank()) { "Service type is required." }
        require(record.createdAtUtc >= 0L && record.updatedAtUtc >= record.createdAtUtc) { "Service timestamps are invalid." }

        val existing = dao.observeOilChanges(record.vehicleId).first().firstOrNull { it.id == record.id }
        val priorOdometer = dao.getOdometerReadingForSource(record.id)
        val readingId = priorOdometer?.id ?: java.util.UUID.randomUUID().toString()
        val normalized = record.copy(
            odometerReadingId = readingId,
            oilBrand = record.oilBrand.cleanOrNull(),
            oilProduct = record.oilProduct.cleanOrNull(),
            viscosityGrade = record.viscosityGrade.cleanOrNull()?.uppercase(),
            manufacturerApproval = record.manufacturerApproval.cleanOrNull(),
            filterBrand = record.filterBrand.cleanOrNull(),
            filterPartNumber = record.filterPartNumber.cleanOrNull(),
            serviceProvider = record.serviceProvider.cleanOrNull(),
            serviceType = record.serviceType.trim().uppercase(),
            currencyCode = currency,
            notes = record.notes.cleanOrNull(),
            createdAtUtc = existing?.createdAtUtc ?: record.createdAtUtc
        )
        val serviceOdometer = ca.gllavoie.vehicletracker.data.local.OdometerReadingEntity(
            id = readingId,
            vehicleId = record.vehicleId,
            value = record.odometerValue,
            unit = record.odometerUnit,
            recordedAtUtc = serviceDate.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            sourceType = "OIL_CHANGE",
            sourceRecordId = record.id,
            notes = "Odometer recorded with oil change",
            createdAtUtc = priorOdometer?.createdAtUtc ?: normalized.createdAtUtc,
            updatedAtUtc = normalized.updatedAtUtc
        )
        if (existing == null) dao.insertOilChangeWithReading(normalized, serviceOdometer)
        else {
            require(existing.vehicleId == record.vehicleId) { "A service record cannot be moved to another vehicle." }
            dao.updateOilChangeWithReading(normalized, serviceOdometer)
        }
    }

    private fun String?.cleanOrNull(): String? = this?.trim()?.ifEmpty { null }
}
