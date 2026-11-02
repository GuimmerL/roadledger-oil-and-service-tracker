package ca.gllavoie.vehicletracker.data.repository

import ca.gllavoie.vehicletracker.data.local.VehicleSpecificationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleCatalogOptionsTest {
    private val entries = listOf(
        entry("a", "Honda", "Civic", "2.0L I4", "2019-2022 (MK7)"),
        entry("b", "Honda", "Civic", "1.5L Turbo I4", "2019-2022 (MK7)"),
        entry("c", "Honda", "Accord", "1.5L Turbo I4", "2020-2024"),
        entry("d", "OtherMake", "Model", "V6", "2010")
    )

    @Test fun yearDropdownExpandsRangesAndReturnsUniqueDescendingYears() {
        assertEquals(listOf(2024, 2023, 2022, 2021, 2020, 2019, 2010), VehicleCatalogOptions.modelYears(entries))
    }

    @Test fun cascadingOptionsFilterByExactYearManufacturerAndModel() {
        assertEquals(listOf("Honda"), VehicleCatalogOptions.makesForYear(entries, 2020))
        assertEquals(listOf("OtherMake"), VehicleCatalogOptions.makesForYear(entries, 2010))
        assertEquals(listOf("Accord", "Civic"), VehicleCatalogOptions.modelsForYearAndMake(entries, 2020, "honda"))
        assertEquals(
            listOf("1.5L Turbo I4", "2.0L I4"),
            VehicleCatalogOptions.configurationsForVehicle(entries, 2020, "Honda", "Civic").map { it.engine }
        )
        assertTrue(VehicleCatalogOptions.entriesForYear(entries, 2009).isEmpty())
    }

    @Test fun clearlyDistinctEngineOptionsSplitButAmbiguousEngineSpecificIntervalsStayGrouped() {
        val splitEntry = entry("split", "Acme", "Model", "2.5L I4 / 3.5L V6", "2015-2020")
        val split = VehicleCatalogOptions.configurationsForVehicle(listOf(splitEntry), 2018, "Acme", "Model")
        assertEquals(listOf("2.5L I4", "3.5L V6"), split.map { it.engine })
        assertTrue(split.all { it.id == "split" })

        val conditional = splitEntry.copy(
            id = "conditional",
            serviceNotes = "Years: 2015-2020\nRaw normal-service interval: 10,000 mi (NA) / 5,000 mi (Turbo)"
        )
        val grouped = VehicleCatalogOptions.configurationsForVehicle(listOf(conditional), 2018, "Acme", "Model")
        assertEquals(listOf("2.5L I4 / 3.5L V6"), grouped.map { it.engine })
    }

    @Test fun engineTrimSourceTextStaysOneConfigurationAndDoesNotRequireSeparateTrimSelection() {
        val entry = entry("trim", "Honda", "Civic", "2.0L Turbo I4 (Type R)", "2023")
        val options = VehicleCatalogOptions.configurationsForVehicle(listOf(entry), 2023, "Honda", "Civic")
        val option = options.single()

        assertEquals("2.0L Turbo I4 (Type R)", option.engine)
        assertNull(option.trim)
        assertTrue(VehicleCatalogOptions.matchesSelectedConfiguration(entry, options, option.engine!!))
    }

    @Test fun officialIntervalSupplementsAreNotCollapsedIntoUnverifiedRanges() {
        val ai = entry("ai", "Toyota", "Prius", "1.8L I4 (2ZR-FXE)", "2016-2022")
        val official = entry("official", "Toyota", "Prius", "1.8L I4 (2ZR-FXE)", "2016").copy(
            modelYear = 2016,
            verificationStatus = "OFFICIAL_INTERVAL_RESEARCHED"
        )
        val options = VehicleCatalogOptions.configurationsForVehicle(listOf(ai, official), 2016, "Toyota", "Prius")
        assertEquals(2, options.size)
        assertEquals("OFFICIAL_INTERVAL_RESEARCHED", options.first().verificationStatus)
    }

    @Test fun selectionWarningIsRequiredOnlyForAiGeneratedUnverifiedRows() {
        assertTrue(VehicleCatalogOptions.requiresSelectionWarning("AI_GENERATED_UNVERIFIED"))
        assertTrue(!VehicleCatalogOptions.requiresSelectionWarning("OFFICIAL_INTERVAL_RESEARCHED"))
        assertTrue(!VehicleCatalogOptions.requiresSelectionWarning("USER_VERIFIED"))
        assertTrue(!VehicleCatalogOptions.requiresSelectionWarning(null))
    }

    private fun entry(id: String, make: String, model: String, engine: String, years: String) =
        VehicleSpecificationEntity(
            id = id,
            make = make,
            model = model,
            engine = engine,
            serviceNotes = "Years: $years",
            createdAtUtc = 1,
            updatedAtUtc = 1
        )
}
