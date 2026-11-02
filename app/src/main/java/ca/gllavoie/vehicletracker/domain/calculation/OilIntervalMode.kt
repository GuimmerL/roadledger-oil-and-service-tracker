package ca.gllavoie.vehicletracker.domain.calculation

enum class OilIntervalMode(val storedValue: String, val displayName: String) {
    NORMAL("NORMAL", "Normal use"),
    SEVERE("SEVERE", "Severe / heavy use");

    companion object {
        fun fromStoredValue(value: String?): OilIntervalMode =
            entries.firstOrNull { it.storedValue == value } ?: NORMAL
    }
}
