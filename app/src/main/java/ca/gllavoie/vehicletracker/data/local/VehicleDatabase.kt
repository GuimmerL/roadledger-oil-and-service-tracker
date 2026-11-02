package ca.gllavoie.vehicletracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        VehicleEntity::class,
        VehicleSpecificationEntity::class,
        SpecificationSourceEntity::class,
        VehicleSpecificationOverrideEntity::class,
        OdometerReadingEntity::class,
        OilChangeEntity::class,
        FuelFillUpEntity::class,
        ReceiptAttachmentEntity::class,
        AppSettingsEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class VehicleDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
}
