package ca.gllavoie.vehicletracker.preferences

import android.content.Context
import ca.gllavoie.vehicletracker.ui.garage.GarageSortOrder

object GarageSortPreferences {
    private const val PREFERENCES = "garage_preferences"
    private const val VEHICLE_SORT_ORDER = "vehicle_sort_order"

    fun get(context: Context): GarageSortOrder = GarageSortOrder.fromStoredValue(
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(VEHICLE_SORT_ORDER, null)
    )

    fun set(context: Context, order: GarageSortOrder) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(VEHICLE_SORT_ORDER, order.name)
            .apply()
    }
}
