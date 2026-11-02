package ca.gllavoie.vehicletracker.domain.vehicle

enum class VehicleType(val storedValue: String, val label: String) {
    SEDAN("SEDAN", "Sedan"),
    HATCHBACK("HATCHBACK", "Hatchback"),
    SUV("SUV", "SUV / crossover"),
    PICKUP("PICKUP", "Pickup truck"),
    WAGON("WAGON", "Wagon"),
    VAN("VAN", "Van / minivan"),
    COUPE("COUPE", "Coupe"),
    CONVERTIBLE("CONVERTIBLE", "Convertible"),
    MOTORCYCLE("MOTORCYCLE", "Motorcycle"),
    OTHER("OTHER", "Other / not set");

    companion object {
        fun fromStoredValue(value: String?): VehicleType =
            entries.firstOrNull { it.storedValue == value } ?: OTHER

        fun fromLabel(label: String): VehicleType =
            entries.firstOrNull { it.label == label } ?: OTHER
    }
}
