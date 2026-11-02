package ca.gllavoie.vehicletracker.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object VehicleDatabaseMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `vehicles` ADD COLUMN `startingDailyUsageKmPerDay` REAL")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `vehicles` ADD COLUMN `oilIntervalMode` TEXT NOT NULL DEFAULT 'NORMAL'")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `vehicles` ADD COLUMN `vehicleType` TEXT NOT NULL DEFAULT 'OTHER'")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `receipt_attachments` ADD COLUMN `documentType` TEXT NOT NULL DEFAULT 'RECEIPT'")
        }
    }
}
