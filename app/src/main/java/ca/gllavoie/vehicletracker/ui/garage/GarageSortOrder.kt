package ca.gllavoie.vehicletracker.ui.garage

import ca.gllavoie.vehicletracker.data.local.VehicleEntity

/** Presentation-only garage ordering; vehicle records and their saved values are unchanged. */
enum class GarageSortOrder(val label: String) {
    NAME("Name (A–Z)"),
    YEAR_NEWEST("Model year (newest)"),
    YEAR_OLDEST("Model year (oldest)"),
    RECENTLY_ADDED("Recently added");

    fun sort(vehicles: List<VehicleEntity>): List<VehicleEntity> = when (this) {
        NAME -> vehicles.sortedWith(nameComparator)
        YEAR_NEWEST -> vehicles.sortedWith(
            compareByDescending<VehicleEntity> { it.modelYear ?: Int.MIN_VALUE }
                .then(nameComparator)
        )
        YEAR_OLDEST -> vehicles.sortedWith(
            compareBy<VehicleEntity> { it.modelYear ?: Int.MAX_VALUE }
                .then(nameComparator)
        )
        RECENTLY_ADDED -> vehicles.sortedWith(
            compareByDescending<VehicleEntity> { it.createdAtUtc }
                .then(nameComparator)
        )
    }

    companion object {
        private val nameComparator = compareBy<VehicleEntity, String>(String.CASE_INSENSITIVE_ORDER) { it.make }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.model }
            .thenBy { it.id }

        fun fromStoredValue(value: String?): GarageSortOrder =
            entries.firstOrNull { it.name == value } ?: NAME
    }
}
