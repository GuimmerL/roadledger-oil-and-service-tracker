package ca.gllavoie.vehicletracker.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MeasurementSystemTest {
    @Test fun imperialDistanceIntervalsConvertToMetricConservatively() {
        assertEquals(16_093, convertDistanceInterval(10_000, "MILE", MeasurementSystem.METRIC))
        assertEquals(8_046, convertDistanceInterval(5_000, "MILE", MeasurementSystem.METRIC))
    }

    @Test fun metricDistanceIntervalsConvertToImperialConservatively() {
        assertEquals(6_213, convertDistanceInterval(10_000, "KM", MeasurementSystem.IMPERIAL))
    }

    @Test fun identicalAndUnknownUnitsAreHandledWithoutGuessing() {
        assertEquals(10_000, convertDistanceInterval(10_000, "KM", MeasurementSystem.METRIC))
        assertNull(convertDistanceInterval(10_000, null, MeasurementSystem.METRIC))
        assertNull(convertDistanceInterval(10_000, "YARD", MeasurementSystem.METRIC))
    }

    @Test fun dailyUsageConvertsWithoutRoundingAwayPrecision() {
        val kilometres = convertDailyDistance(50.0, "MILE", "KM")
        assertEquals(80.4672, kilometres!!, 0.0001)
        assertEquals(50.0, convertDailyDistance(kilometres, "KM", "MILE")!!, 0.0001)
        assertEquals("80.5", formatDailyDistance(kilometres))
        assertNull(convertDailyDistance(Double.NaN, "KM", "MILE"))
    }
}
