package ca.gllavoie.vehicletracker.domain.document

enum class VehicleDocumentType(val storedValue: String, val label: String) {
    RECEIPT("RECEIPT", "Service receipt"),
    INSURANCE("INSURANCE", "Insurance"),
    SERVICE_RECORD("SERVICE_RECORD", "Service record"),
    REGISTRATION("REGISTRATION", "Registration"),
    WARRANTY("WARRANTY", "Warranty"),
    OTHER("OTHER", "Other document");

    companion object {
        val vehicleDocumentTypes: List<VehicleDocumentType>
            get() = entries.filterNot { it == RECEIPT }

        fun fromStoredValue(value: String?): VehicleDocumentType =
            entries.firstOrNull { it.storedValue == value } ?: OTHER
    }
}
