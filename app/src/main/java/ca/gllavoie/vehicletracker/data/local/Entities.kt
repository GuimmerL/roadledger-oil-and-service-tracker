package ca.gllavoie.vehicletracker.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

private fun newId(): String = UUID.randomUUID().toString()

@Entity(
    tableName = "vehicles",
    foreignKeys = [ForeignKey(entity = VehicleSpecificationEntity::class, parentColumns = ["id"], childColumns = ["defaultSpecificationId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("vin"), Index("archivedAtUtc"), Index("defaultSpecificationId")]
)
data class VehicleEntity(
    @PrimaryKey val id: String = newId(),
    val nickname: String? = null,
    val modelYear: Int? = null,
    val make: String,
    val model: String,
    val trim: String? = null,
    val engine: String? = null,
    val engineCode: String? = null,
    val fuelType: String? = null,
    val transmission: String? = null,
    val vin: String? = null,
    val licensePlate: String? = null,
    val odometerUnit: String = "KM",
    val acquiredOn: String? = null,
    val photoUri: String? = null,
    val notes: String? = null,
    val defaultSpecificationId: String? = null,
    val archivedAtUtc: Long? = null,
    val createdAtUtc: Long,
    val updatedAtUtc: Long,
    val startingDailyUsageKmPerDay: Double? = null,
    @androidx.room.ColumnInfo(defaultValue = "'NORMAL'") val oilIntervalMode: String = "NORMAL",
    @androidx.room.ColumnInfo(defaultValue = "'OTHER'") val vehicleType: String = "OTHER"
)

@Entity(tableName = "vehicle_specifications", indices = [Index(value = ["make", "model", "modelYear", "engineCode"])])
data class VehicleSpecificationEntity(
    @PrimaryKey val id: String = newId(),
    val make: String,
    val model: String,
    val modelYear: Int? = null,
    val trim: String? = null,
    val engine: String? = null,
    val engineCode: String? = null,
    val viscosityGrade: String? = null,
    val manufacturerApproval: String? = null,
    val capacityWithFilterMl: Int? = null,
    val capacityWithoutFilterMl: Int? = null,
    val distanceInterval: Int? = null,
    val distanceUnit: String? = null,
    val timeIntervalMonths: Int? = null,
    val severeDistanceInterval: Int? = null,
    val severeTimeIntervalMonths: Int? = null,
    val oilFilterPartNumber: String? = null,
    val serviceNotes: String? = null,
    val verificationStatus: String = "UNVERIFIED",
    val verifiedOn: String? = null,
    val createdAtUtc: Long,
    val updatedAtUtc: Long
)

@Entity(
    tableName = "specification_sources",
    foreignKeys = [ForeignKey(entity = VehicleSpecificationEntity::class, parentColumns = ["id"], childColumns = ["specificationId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("specificationId")]
)
data class SpecificationSourceEntity(
    @PrimaryKey val id: String = newId(),
    val specificationId: String,
    val sourceName: String,
    val sourceUrl: String? = null,
    val verifiedOn: String? = null,
    val verificationStatus: String,
    val notes: String? = null
)

@Entity(
    tableName = "vehicle_specification_overrides",
    foreignKeys = [ForeignKey(entity = VehicleEntity::class, parentColumns = ["id"], childColumns = ["vehicleId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["vehicleId"], unique = true)]
)
data class VehicleSpecificationOverrideEntity(
    @PrimaryKey val id: String = newId(),
    val vehicleId: String,
    val viscosityGrade: String? = null,
    val manufacturerApproval: String? = null,
    val capacityWithFilterMl: Int? = null,
    val distanceInterval: Int? = null,
    val distanceUnit: String? = null,
    val timeIntervalMonths: Int? = null,
    val oilFilterPartNumber: String? = null,
    val sourceDescription: String? = null,
    val updatedAtUtc: Long
)

@Entity(
    tableName = "odometer_readings",
    foreignKeys = [ForeignKey(entity = VehicleEntity::class, parentColumns = ["id"], childColumns = ["vehicleId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["vehicleId", "recordedAtUtc"]), Index("sourceRecordId")]
)
data class OdometerReadingEntity(
    @PrimaryKey val id: String = newId(),
    val vehicleId: String,
    val value: Long,
    val unit: String,
    val recordedAtUtc: Long,
    val sourceType: String,
    val sourceRecordId: String? = null,
    val notes: String? = null,
    val createdAtUtc: Long,
    val updatedAtUtc: Long
)

@Entity(
    tableName = "oil_changes",
    foreignKeys = [ForeignKey(entity = VehicleEntity::class, parentColumns = ["id"], childColumns = ["vehicleId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["vehicleId", "serviceDate"]), Index("odometerReadingId")]
)
data class OilChangeEntity(
    @PrimaryKey val id: String = newId(),
    val vehicleId: String,
    val serviceDate: String,
    val odometerValue: Long,
    val odometerUnit: String,
    val odometerReadingId: String? = null,
    val oilBrand: String? = null,
    val oilProduct: String? = null,
    val viscosityGrade: String? = null,
    val manufacturerApproval: String? = null,
    val quantityUsedMl: Int? = null,
    val filterBrand: String? = null,
    val filterPartNumber: String? = null,
    val filterReplaced: Boolean = true,
    val serviceProvider: String? = null,
    val serviceType: String = "OTHER",
    val totalCostMinor: Long? = null,
    val partsCostMinor: Long? = null,
    val laborCostMinor: Long? = null,
    val currencyCode: String = "CAD",
    val distanceIntervalOverride: Int? = null,
    val timeIntervalMonthsOverride: Int? = null,
    val notes: String? = null,
    val createdAtUtc: Long,
    val updatedAtUtc: Long
)

@Entity(
    tableName = "fuel_fill_ups",
    foreignKeys = [ForeignKey(entity = VehicleEntity::class, parentColumns = ["id"], childColumns = ["vehicleId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["vehicleId", "filledAtUtc"]), Index("odometerReadingId")]
)
data class FuelFillUpEntity(
    @PrimaryKey val id: String = newId(),
    val vehicleId: String,
    val filledAtUtc: Long,
    val odometerValue: Long,
    val odometerUnit: String,
    val odometerReadingId: String? = null,
    val volumeMillilitres: Long,
    val totalCostMinor: Long? = null,
    val currencyCode: String = "CAD",
    val fuelType: String? = null,
    val isFullTank: Boolean,
    val stationName: String? = null,
    val notes: String? = null,
    val createdAtUtc: Long,
    val updatedAtUtc: Long
)

@Entity(
    tableName = "receipt_attachments",
    foreignKeys = [
        ForeignKey(entity = VehicleEntity::class, parentColumns = ["id"], childColumns = ["vehicleId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = OilChangeEntity::class, parentColumns = ["id"], childColumns = ["oilChangeId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = FuelFillUpEntity::class, parentColumns = ["id"], childColumns = ["fuelFillUpId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("vehicleId"), Index("oilChangeId"), Index("fuelFillUpId")]
)
data class ReceiptAttachmentEntity(
    @PrimaryKey val id: String = newId(),
    val vehicleId: String,
    val oilChangeId: String? = null,
    val fuelFillUpId: String? = null,
    val internalRelativePath: String,
    val originalFileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
    val addedAtUtc: Long,
    val notes: String? = null,
    @androidx.room.ColumnInfo(defaultValue = "'RECEIPT'") val documentType: String = "RECEIPT"
)

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAtUtc: Long
)
