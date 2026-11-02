package ca.gllavoie.vehicletracker.data.local

import android.content.Context
import androidx.room.Room

/** Creates the durable app database; destructive migration fallback is intentionally disabled. */
object VehicleDatabaseFactory {
    const val DATABASE_NAME = "vehicle_tracker.db"

    fun create(context: Context): VehicleDatabase = Room.databaseBuilder(
        context.applicationContext,
        VehicleDatabase::class.java,
        DATABASE_NAME
    ).addMigrations(
        VehicleDatabaseMigrations.MIGRATION_1_2,
        VehicleDatabaseMigrations.MIGRATION_2_3,
        VehicleDatabaseMigrations.MIGRATION_3_4,
        VehicleDatabaseMigrations.MIGRATION_4_5
    ).build()
}
