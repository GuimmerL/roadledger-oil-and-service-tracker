package ca.gllavoie.vehicletracker.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VehicleDatabaseMigrationTest {
    @Test fun migrationPreservesExistingVehicleAndAddsNullableUsageBaseline() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseFile = File(context.cacheDir, "vehicle-migration-${UUID.randomUUID()}.db")
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE `vehicles` (`id` TEXT NOT NULL PRIMARY KEY, `make` TEXT NOT NULL, `model` TEXT NOT NULL)")
                        db.execSQL("INSERT INTO `vehicles` (`id`,`make`,`model`) VALUES ('legacy-car','Toyota','Prius')")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        try {
            val database = helper.writableDatabase
            VehicleDatabaseMigrations.MIGRATION_1_2.migrate(database)
            VehicleDatabaseMigrations.MIGRATION_2_3.migrate(database)
            VehicleDatabaseMigrations.MIGRATION_3_4.migrate(database)
            val columns = mutableSetOf<String>()
            database.query("PRAGMA table_info(`vehicles`)").use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) columns += cursor.getString(nameIndex)
            }
            assertTrue("startingDailyUsageKmPerDay" in columns)
            assertTrue("oilIntervalMode" in columns)
            assertTrue("vehicleType" in columns)
            database.query("SELECT `make`, `startingDailyUsageKmPerDay`, `oilIntervalMode`, `vehicleType` FROM `vehicles` WHERE `id`='legacy-car'").use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals("Toyota", cursor.getString(0))
                assertTrue(cursor.isNull(1))
                assertEquals("NORMAL", cursor.getString(2))
                assertEquals("OTHER", cursor.getString(3))
            }
        } finally {
            helper.close()
            databaseFile.delete()
        }
    }

    @Test fun migrationFromVersionTwoPreservesUsageAndDefaultsIntervalModeToNormal() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseFile = File(context.cacheDir, "vehicle-migration-v2-${UUID.randomUUID()}.db")
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE `vehicles` (`id` TEXT NOT NULL PRIMARY KEY, `make` TEXT NOT NULL, `model` TEXT NOT NULL, `startingDailyUsageKmPerDay` REAL)")
                        db.execSQL("INSERT INTO `vehicles` (`id`,`make`,`model`,`startingDailyUsageKmPerDay`) VALUES ('v2-car','Honda','Civic',42.5)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        try {
            val database = helper.writableDatabase
            VehicleDatabaseMigrations.MIGRATION_2_3.migrate(database)
            VehicleDatabaseMigrations.MIGRATION_3_4.migrate(database)
            database.query("SELECT `startingDailyUsageKmPerDay`, `oilIntervalMode`, `vehicleType` FROM `vehicles` WHERE `id`='v2-car'").use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals(42.5, cursor.getDouble(0), 0.0)
                assertEquals("NORMAL", cursor.getString(1))
                assertEquals("OTHER", cursor.getString(2))
            }
        } finally {
            helper.close()
            databaseFile.delete()
        }
    }

    @Test fun migrationFromVersionFourAddsDocumentTypeDefaultToExistingAttachments() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseFile = File(context.cacheDir, "vehicle-migration-v4-${UUID.randomUUID()}.db")
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE `receipt_attachments` (`id` TEXT NOT NULL PRIMARY KEY)")
                        db.execSQL("INSERT INTO `receipt_attachments` (`id`) VALUES ('legacy-receipt')")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        try {
            val database = helper.writableDatabase
            VehicleDatabaseMigrations.MIGRATION_4_5.migrate(database)
            database.query("SELECT `documentType` FROM `receipt_attachments` WHERE `id`='legacy-receipt'").use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals("RECEIPT", cursor.getString(0))
            }
        } finally {
            helper.close()
            databaseFile.delete()
        }
    }
}
