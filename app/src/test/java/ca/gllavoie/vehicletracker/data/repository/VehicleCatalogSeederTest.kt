package ca.gllavoie.vehicletracker.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ca.gllavoie.vehicletracker.data.local.AppSettingsEntity
import ca.gllavoie.vehicletracker.data.local.VehicleDatabase
import ca.gllavoie.vehicletracker.data.local.VehicleEntity
import ca.gllavoie.vehicletracker.data.local.VehicleSpecificationEntity
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VehicleCatalogSeederTest {
    private lateinit var context: Context
    private lateinit var dbFile: File
    private lateinit var database: VehicleDatabase
    private lateinit var vehicleRepository: RoomVehicleRepository
    private lateinit var catalogRepository: RoomVehicleSpecificationRepository
    private lateinit var seeder: VehicleCatalogSeeder

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbFile = File(context.cacheDir, "catalog-v2-seed-${System.nanoTime()}.db")
        database = Room.databaseBuilder(context, VehicleDatabase::class.java, dbFile.absolutePath).build()
        vehicleRepository = RoomVehicleRepository(database.vehicleDao())
        catalogRepository = RoomVehicleSpecificationRepository(database.vehicleDao(), vehicleRepository)
        seeder = VehicleCatalogSeeder(context, catalogRepository) { productionAssetFile().inputStream() }
    }

    @After fun tearDown() {
        database.close()
        dbFile.delete()
        File(dbFile.path + "-shm").delete()
        File(dbFile.path + "-wal").delete()
    }

    @Test fun importsProductionCatalogSevereIntervalsAndProvenanceWithoutBreakingLegacyLinks() = runBlocking {
        val legacy = VehicleSpecificationEntity(
            id = "legacy-user-linked-spec", make = "Honda", model = "Civic", modelYear = 2020,
            distanceInterval = 8_000, distanceUnit = "KM", verificationStatus = "USER_VERIFIED",
            createdAtUtc = 1L, updatedAtUtc = 1L
        )
        database.vehicleDao().insertSpecification(legacy)
        vehicleRepository.save(
            VehicleEntity(
                id = "legacy-linked-car", make = "Honda", model = "Civic", modelYear = 2020,
                defaultSpecificationId = legacy.id, createdAtUtc = 1L, updatedAtUtc = 1L
            )
        )

        assertEquals(VehicleCatalogSeeder.EXPECTED_RECORD_COUNT, seeder.seedIfMissing())
        val active = catalogRepository.observeSpecificationsForDataset(VehicleCatalogSeeder.CATALOG_ID_PREFIX).first()
        assertEquals(15_149, active.size)
        assertEquals(15_083, active.count { it.verificationStatus == "AI_GENERATED_UNVERIFIED" })
        assertEquals(66, active.count { it.verificationStatus == "OFFICIAL_INTERVAL_RESEARCHED" })
        assertTrue(active.map(VehicleSpecificationEntity::id).distinct().size == active.size)

        val first = catalogRepository.getCatalogEntry("${VehicleCatalogSeeder.CATALOG_ID_PREFIX}7752bc54")!!
        assertEquals("Acura", first.make)
        assertEquals("Integra", first.model)
        assertEquals(2023, first.modelYear)
        assertEquals(16_000, first.distanceInterval)
        assertEquals(12, first.timeIntervalMonths)
        assertEquals(8_000, first.severeDistanceInterval)
        assertEquals(6, first.severeTimeIntervalMonths)
        assertEquals("KM", first.distanceUnit)
        assertEquals("AI_GENERATED_UNVERIFIED", first.verificationStatus)
        assertTrue(first.serviceNotes.orEmpty().contains("Catalog dataset: ${VehicleCatalogSeeder.DATASET_MARKER}"))
        assertTrue(first.serviceNotes.orEmpty().contains("Uses oil-life monitor: True"))
        assertTrue(first.serviceNotes.orEmpty().contains("Raw severe-service interval: 8000.0 km / 5000.0 mi / 6.0 months"))

        val official = active.first { it.verificationStatus == "OFFICIAL_INTERVAL_RESEARCHED" }
        val officialSource = database.vehicleDao().getSpecificationSources(official.id).single()
        assertTrue(officialSource.sourceName.isNotBlank())
        assertTrue(officialSource.sourceUrl.orEmpty().startsWith("https://"))
        assertEquals("OFFICIAL_INTERVAL_RESEARCHED", officialSource.verificationStatus)
        assertEquals(15_150, database.vehicleDao().getAllSpecifications().size)
        assertEquals(15_149, database.vehicleDao().getAllSpecificationSources().size)

        assertEquals(legacy.id, vehicleRepository.getVehicle("legacy-linked-car")?.defaultSpecificationId)
        assertNotNull(catalogRepository.getCatalogEntry(legacy.id))

        catalogRepository.saveCatalogEntry(
            first.copy(viscosityGrade = "Corrected by user", verificationStatus = "USER_ENTERED_UNVERIFIED", updatedAtUtc = first.updatedAtUtc + 1)
        )
        assertEquals(0, seeder.seedIfMissing())
        assertEquals("Corrected by user", catalogRepository.getCatalogEntry(first.id)?.viscosityGrade)
        assertEquals("2.1.0", catalogRepository.getSeededCatalogVersion())
    }

    @Test fun refreshUntouchedAiRowsOnCatalogVersionChangeWithoutOverwritingUserEdits() = runBlocking {
        assertEquals(VehicleCatalogSeeder.EXPECTED_RECORD_COUNT, seeder.seedIfMissing())
        val untouchedId = "${VehicleCatalogSeeder.CATALOG_ID_PREFIX}7752bc54"
        val editedId = "${VehicleCatalogSeeder.CATALOG_ID_PREFIX}1248777c"

        // Simulate stale data left by an older catalog revision.
        val stale = catalogRepository.getCatalogEntry(untouchedId)!!.copy(timeIntervalMonths = 3, updatedAtUtc = 2L)
        database.vehicleDao().updateSpecification(stale)
        // Simulate a user edit, which carries a distinct status and must survive a re-seed.
        val edited = catalogRepository.getCatalogEntry(editedId)!!.copy(
            timeIntervalMonths = 9,
            verificationStatus = "USER_ENTERED_UNVERIFIED",
            updatedAtUtc = 3L
        )
        database.vehicleDao().updateSpecification(edited)
        database.vehicleDao().upsertSetting(AppSettingsEntity("catalog.seed.version", "2.0.0", 1L))

        assertEquals(VehicleCatalogSeeder.EXPECTED_RECORD_COUNT, seeder.seedIfMissing())

        assertEquals(12, catalogRepository.getCatalogEntry(untouchedId)?.timeIntervalMonths)
        val preserved = catalogRepository.getCatalogEntry(editedId)!!
        assertEquals(9, preserved.timeIntervalMonths)
        assertEquals("USER_ENTERED_UNVERIFIED", preserved.verificationStatus)
        assertEquals("2.1.0", catalogRepository.getSeededCatalogVersion())
    }

    private fun productionAssetFile(): File {
        var directory = File(System.getProperty("user.dir").orEmpty()).absoluteFile
        while (true) {
            listOf(
                File(directory, "app/src/main/assets/${VehicleCatalogSeeder.ASSET_NAME}"),
                File(directory, "src/main/assets/${VehicleCatalogSeeder.ASSET_NAME}")
            ).firstOrNull(File::isFile)?.let { return it }
            directory = directory.parentFile ?: break
        }
        error("Production catalog CSV was not found from ${System.getProperty("user.dir")}")
    }
}
