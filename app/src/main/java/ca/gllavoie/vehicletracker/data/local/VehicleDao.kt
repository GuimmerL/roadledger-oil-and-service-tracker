package ca.gllavoie.vehicletracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles WHERE archivedAtUtc IS NULL ORDER BY make, model, modelYear")
    fun observeActiveVehicles(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE archivedAtUtc IS NOT NULL ORDER BY make, model, modelYear")
    fun observeArchivedVehicles(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE id = :vehicleId")
    suspend fun getVehicle(vehicleId: String): VehicleEntity?

    @Query("SELECT * FROM receipt_attachments WHERE vehicleId = :vehicleId")
    suspend fun getVehicleAttachments(vehicleId: String): List<ReceiptAttachmentEntity>

    @Query("DELETE FROM receipt_attachments WHERE vehicleId = :vehicleId")
    suspend fun deleteVehicleAttachments(vehicleId: String): Int

    @Query("DELETE FROM oil_changes WHERE vehicleId = :vehicleId")
    suspend fun deleteVehicleOilChanges(vehicleId: String): Int

    @Query("DELETE FROM fuel_fill_ups WHERE vehicleId = :vehicleId")
    suspend fun deleteVehicleFuelFillUps(vehicleId: String): Int

    @Query("DELETE FROM odometer_readings WHERE vehicleId = :vehicleId")
    suspend fun deleteVehicleOdometerReadings(vehicleId: String): Int

    @Query("DELETE FROM vehicle_specification_overrides WHERE vehicleId = :vehicleId")
    suspend fun deleteVehicleSpecificationOverride(vehicleId: String): Int

    @Query("DELETE FROM vehicles WHERE id = :vehicleId")
    suspend fun deleteVehicleRow(vehicleId: String): Int

    @Transaction
    suspend fun deleteVehicleAndRecords(vehicleId: String): VehicleDeletionSnapshot {
        if (getVehicle(vehicleId) == null) return VehicleDeletionSnapshot(false, emptyList())
        val attachments = getVehicleAttachments(vehicleId)
        deleteVehicleAttachments(vehicleId)
        deleteVehicleOilChanges(vehicleId)
        deleteVehicleFuelFillUps(vehicleId)
        deleteVehicleOdometerReadings(vehicleId)
        deleteVehicleSpecificationOverride(vehicleId)
        val deleted = deleteVehicleRow(vehicleId) == 1
        return VehicleDeletionSnapshot(deleted, attachments)
    }

    data class VehicleDeletionSnapshot(val deleted: Boolean, val attachments: List<ReceiptAttachmentEntity>)

    @Query("SELECT * FROM vehicle_specifications ORDER BY make, model, modelYear, engine")
    fun observeSpecifications(): Flow<List<VehicleSpecificationEntity>>

    @Query("SELECT * FROM vehicle_specifications WHERE id LIKE :idPrefix || '%' ORDER BY make, model, modelYear, engine, id")
    fun observeSpecificationsForDataset(idPrefix: String): Flow<List<VehicleSpecificationEntity>>

    @Query("SELECT * FROM vehicle_specifications WHERE id = :specificationId")
    suspend fun getSpecification(specificationId: String): VehicleSpecificationEntity?

    @Query("SELECT * FROM vehicle_specifications WHERE id = :specificationId")
    fun observeSpecification(specificationId: String): Flow<VehicleSpecificationEntity?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSpecification(specification: VehicleSpecificationEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSpecificationsIfMissing(specifications: List<VehicleSpecificationEntity>)

    @androidx.room.Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun updateSpecification(specification: VehicleSpecificationEntity): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSpecificationSourcesIfMissing(sources: List<SpecificationSourceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSpecificationSource(source: SpecificationSourceEntity)

    @Transaction
    suspend fun saveCatalogSpecification(specification: VehicleSpecificationEntity, source: SpecificationSourceEntity?) {
        val existing = getSpecification(specification.id)
        if (existing == null) insertSpecification(specification)
        else check(updateSpecification(specification) == 1) { "Vehicle specification could not be updated." }
        source?.let { upsertSpecificationSource(it) }
    }

    @Query("SELECT * FROM specification_sources WHERE specificationId = :specificationId ORDER BY sourceName")
    suspend fun getSpecificationSources(specificationId: String): List<SpecificationSourceEntity>

    @Transaction
    suspend fun seedSpecificationsIfMissing(specifications: List<VehicleSpecificationEntity>, sources: List<SpecificationSourceEntity>) {
        val legacyOfficialIds = mutableListOf<String>()
        val existingById = getAllSpecifications().associateBy { it.id }
        val metricNotePrefixes = listOf(
            "Raw normal-service interval:",
            "Raw severe-service interval:",
            "Original source normal-service interval:",
            "Original source severe-service interval:",
            "Catalog metric status:"
        )
        for (seeded in specifications) {
            val existing = existingById[seeded.id] ?: continue
            val hasMetricNote = existing.serviceNotes.orEmpty().lineSequence()
                .any { it.startsWith("Catalog metric status:") }
            when {
                existing.verificationStatus == "AI_GENERATED_UNVERIFIED" && !hasMetricNote -> {
                    updateSpecification(seeded.copy(createdAtUtc = existing.createdAtUtc, updatedAtUtc = existing.updatedAtUtc))
                }
                existing.verificationStatus == "OFFICIAL_INTERVAL_VERIFIED" -> {
                    val retainedNotes = existing.serviceNotes.orEmpty().lineSequence()
                        .filterNot { line -> metricNotePrefixes.any(line::startsWith) }
                        .toList()
                    val metricIntervalNotes = seeded.serviceNotes.orEmpty().lineSequence()
                        .filter { line -> metricNotePrefixes.any(line::startsWith) }
                        .toList()
                    val updated = existing.copy(
                        distanceInterval = seeded.distanceInterval,
                        distanceUnit = seeded.distanceUnit,
                        timeIntervalMonths = seeded.timeIntervalMonths,
                        severeDistanceInterval = seeded.severeDistanceInterval,
                        severeTimeIntervalMonths = seeded.severeTimeIntervalMonths,
                        serviceNotes = (retainedNotes + metricIntervalNotes).joinToString("\n").trim().ifEmpty { null },
                        verificationStatus = "OFFICIAL_INTERVAL_RESEARCHED",
                        verifiedOn = seeded.verifiedOn
                    )
                    updateSpecification(updated)
                    legacyOfficialIds += seeded.id
                }
            }
        }
        if (specifications.isNotEmpty()) insertSpecificationsIfMissing(specifications)
        if (sources.isNotEmpty()) insertSpecificationSourcesIfMissing(sources)
        legacyOfficialIds.forEach { specificationId ->
            getSpecificationSources(specificationId)
                .filter { it.verificationStatus == "OFFICIAL_INTERVAL_VERIFIED" }
                .forEach { oldSource ->
                    upsertSpecificationSource(
                        oldSource.copy(
                            verificationStatus = "OFFICIAL_INTERVAL_RESEARCHED",
                            notes = "OEM interval source researched; user confirmation is required before scheduling can use it."
                        )
                    )
                }
        }
    }

    @Query("SELECT * FROM vehicle_specification_overrides WHERE vehicleId = :vehicleId")
    fun observeSpecificationOverride(vehicleId: String): Flow<VehicleSpecificationOverrideEntity?>

    @Query("SELECT * FROM vehicle_specification_overrides WHERE vehicleId = :vehicleId")
    suspend fun getSpecificationOverride(vehicleId: String): VehicleSpecificationOverrideEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSpecificationOverride(override: VehicleSpecificationOverrideEntity)

    @Query("SELECT `value` FROM app_settings WHERE `key` = :key LIMIT 1")
    suspend fun getSettingValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSetting(setting: AppSettingsEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertVehicle(vehicle: VehicleEntity)

    @androidx.room.Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun updateVehicle(vehicle: VehicleEntity): Int

    @Query("UPDATE vehicles SET archivedAtUtc = :archivedAtUtc, updatedAtUtc = :archivedAtUtc WHERE id = :vehicleId AND archivedAtUtc IS NULL")
    suspend fun archiveVehicle(vehicleId: String, archivedAtUtc: Long): Int

    @Query("UPDATE vehicles SET archivedAtUtc = NULL, updatedAtUtc = :updatedAtUtc WHERE id = :vehicleId AND archivedAtUtc IS NOT NULL")
    suspend fun restoreVehicle(vehicleId: String, updatedAtUtc: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOdometerReading(reading: OdometerReadingEntity)

    @Query("SELECT * FROM odometer_readings WHERE vehicleId = :vehicleId ORDER BY recordedAtUtc DESC")
    fun observeOdometerReadings(vehicleId: String): Flow<List<OdometerReadingEntity>>

    @Query("SELECT * FROM odometer_readings WHERE sourceRecordId = :recordId LIMIT 1")
    suspend fun getOdometerReadingForSource(recordId: String): OdometerReadingEntity?

    @androidx.room.Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun updateOdometerReading(reading: OdometerReadingEntity): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOilChange(record: OilChangeEntity)

    @androidx.room.Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun updateOilChange(record: OilChangeEntity): Int

    @Transaction
    suspend fun insertOilChangeWithReading(record: OilChangeEntity, reading: OdometerReadingEntity) {
        insertOilChange(record)
        insertOdometerReading(reading)
    }

    @Transaction
    suspend fun updateOilChangeWithReading(record: OilChangeEntity, reading: OdometerReadingEntity) {
        check(updateOilChange(record) == 1) { "Service record could not be updated." }
        val prior = getOdometerReadingForSource(record.id)
        if (prior == null) insertOdometerReading(reading)
        else check(updateOdometerReading(reading.copy(id = prior.id, createdAtUtc = prior.createdAtUtc)) == 1) {
            "Service odometer reading could not be updated."
        }
    }

    @Query("SELECT * FROM oil_changes WHERE vehicleId = :vehicleId ORDER BY serviceDate DESC")
    fun observeOilChanges(vehicleId: String): Flow<List<OilChangeEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFuelFillUp(record: FuelFillUpEntity)

    @Query("SELECT * FROM fuel_fill_ups WHERE vehicleId = :vehicleId ORDER BY filledAtUtc DESC")
    fun observeFuelFillUps(vehicleId: String): Flow<List<FuelFillUpEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAttachment(attachment: ReceiptAttachmentEntity)

    @Query("SELECT * FROM receipt_attachments WHERE id = :attachmentId")
    suspend fun getAttachment(attachmentId: String): ReceiptAttachmentEntity?

    @Query("SELECT * FROM receipt_attachments ORDER BY addedAtUtc DESC")
    suspend fun getAllAttachments(): List<ReceiptAttachmentEntity>

    @Query("DELETE FROM receipt_attachments WHERE id = :attachmentId")
    suspend fun deleteAttachment(attachmentId: String): Int

    @Query("SELECT * FROM oil_changes WHERE id = :recordId")
    suspend fun getOilChange(recordId: String): OilChangeEntity?

    @Query("SELECT * FROM receipt_attachments WHERE oilChangeId = :recordId OR fuelFillUpId = :recordId ORDER BY addedAtUtc DESC")
    fun observeAttachments(recordId: String): Flow<List<ReceiptAttachmentEntity>>

    @Query("SELECT * FROM receipt_attachments WHERE vehicleId = :vehicleId ORDER BY addedAtUtc DESC")
    fun observeVehicleAttachments(vehicleId: String): Flow<List<ReceiptAttachmentEntity>>

    @Query("SELECT * FROM vehicles")
    suspend fun getAllVehicles(): List<VehicleEntity>

    @Query("SELECT * FROM vehicle_specifications")
    suspend fun getAllSpecifications(): List<VehicleSpecificationEntity>

    @Query("SELECT * FROM specification_sources")
    suspend fun getAllSpecificationSources(): List<SpecificationSourceEntity>

    @Query("SELECT * FROM vehicle_specification_overrides")
    suspend fun getAllSpecificationOverrides(): List<VehicleSpecificationOverrideEntity>

    @Query("SELECT * FROM odometer_readings")
    suspend fun getAllOdometerReadings(): List<OdometerReadingEntity>

    @Query("SELECT * FROM oil_changes")
    suspend fun getAllOilChanges(): List<OilChangeEntity>

    @Query("SELECT * FROM fuel_fill_ups")
    suspend fun getAllFuelFillUps(): List<FuelFillUpEntity>

    @Query("SELECT * FROM app_settings")
    suspend fun getAllSettings(): List<AppSettingsEntity>

    @Transaction
    suspend fun addFuelRecordAndOdometer(record: FuelFillUpEntity, reading: OdometerReadingEntity) {
        insertFuelFillUp(record)
        insertOdometerReading(reading)
    }
}
