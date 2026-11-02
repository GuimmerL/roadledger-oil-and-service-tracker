package ca.gllavoie.vehicletracker.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogOilIntervalParserTest {
    @Test fun parsesExplicitNormalAndSevereIntervals() {
        val parsed = CatalogOilIntervalParser.parse("10,000 mi / 12 mo", "5,000 mi / 6 mo")

        assertEquals(10_000, parsed.normalDistance)
        assertEquals("MILE", parsed.distanceUnit)
        assertEquals(12, parsed.normalMonths)
        assertEquals(5_000, parsed.severeDistance)
        assertEquals(6, parsed.severeMonths)
    }

    @Test fun rangeUsesShorterEndpointAndRecordsThatDecision() {
        val parsed = CatalogOilIntervalParser.parse("5,000-7,500 mi / 6 mo", "3,000-5,000 mi")

        assertEquals(5_000, parsed.normalDistance)
        assertEquals("MILE", parsed.distanceUnit)
        assertEquals(6, parsed.normalMonths)
        assertEquals(3_000, parsed.severeDistance)
        assertTrue(parsed.normalizationNotes.any { it.contains("lower endpoint") })
    }

    @Test fun preservesConditionalOrConflictingValuesWithoutChoosingOne() {
        val parsed = CatalogOilIntervalParser.parse("7,500 mi (NA) / 5,000 mi (Turbo)", "3,750 mi / 3,000 mi")

        assertNull(parsed.normalDistance)
        assertNull(parsed.severeDistance)
        assertTrue(parsed.normalizationNotes.any { it.contains("conditional or conflicting") })
    }

    @Test fun doesNotTreatLifetimeInspectionAsAnOilInterval() {
        val parsed = CatalogOilIntervalParser.parse("Lifetime / Inspect at 150,000 mi", "High")

        assertNull(parsed.normalDistance)
        assertNull(parsed.distanceUnit)
        assertNull(parsed.severeDistance)
    }

    @Test fun preservesUnitsAndDoesNotMixNormalAndSevereDistanceUnits() {
        val parsed = CatalogOilIntervalParser.parse("10,000 km / 12 mo", "3,000 mi / Track")

        assertEquals(10_000, parsed.normalDistance)
        assertEquals("KM", parsed.distanceUnit)
        assertEquals(12, parsed.normalMonths)
        assertNull(parsed.severeDistance)
        assertTrue(parsed.normalizationNotes.any { it.contains("different unit") })
    }
}
