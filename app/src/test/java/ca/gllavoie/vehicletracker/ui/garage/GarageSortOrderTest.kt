package ca.gllavoie.vehicletracker.ui.garage

import ca.gllavoie.vehicletracker.data.local.VehicleEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class GarageSortOrderTest {
    private val vehicles = listOf(
        vehicle("civic", "Honda", "Civic", 2020, 10L),
        vehicle("prius", "Toyota", "Prius", 2018, 30L),
        vehicle("corolla", "Toyota", "Corolla", 2022, 20L),
        vehicle("unknown-year", "Ford", "Focus", null, 40L)
    )

    @Test fun sortsByNameWithStableTieBreakers() {
        assertEquals(listOf("Focus", "Civic", "Corolla", "Prius"), GarageSortOrder.NAME.sort(vehicles).map { it.model })
    }

    @Test fun sortsByYearNewestAndKeepsUnknownYearsLast() {
        assertEquals(listOf("Corolla", "Civic", "Prius", "Focus"), GarageSortOrder.YEAR_NEWEST.sort(vehicles).map { it.model })
    }

    @Test fun sortsByYearOldestAndKeepsUnknownYearsLast() {
        assertEquals(listOf("Prius", "Civic", "Corolla", "Focus"), GarageSortOrder.YEAR_OLDEST.sort(vehicles).map { it.model })
    }

    @Test fun sortsByRecentlyAdded() {
        assertEquals(listOf("Focus", "Prius", "Corolla", "Civic"), GarageSortOrder.RECENTLY_ADDED.sort(vehicles).map { it.model })
    }

    private fun vehicle(id: String, make: String, model: String, year: Int?, createdAt: Long) = VehicleEntity(
        id = id,
        make = make,
        model = model,
        modelYear = year,
        createdAtUtc = createdAt,
        updatedAtUtc = createdAt
    )
}
