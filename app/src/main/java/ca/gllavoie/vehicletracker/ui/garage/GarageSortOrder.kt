package ca.gllavoie.vehicletracker.ui.garage

import androidx.annotation.StringRes
import ca.gllavoie.vehicletracker.R
import ca.gllavoie.vehicletracker.data.local.VehicleEntity

/** Presentation-only garage ordering; vehicle records and their saved values are unchanged. */
enum class GarageSortOrder(@StringRes val labelRes: Int) {
    NAME(R.string.sort_name_az),
    YEAR_NEWEST(R.string.sort_year_newest),
    YEAR_OLDEST(R.string.sort_year_oldest),
    RECENTLY_ADDED(R.string.sort_recently_added);

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
