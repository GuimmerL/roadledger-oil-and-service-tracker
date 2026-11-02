package ca.gllavoie.vehicletracker.domain.vehicle

import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleTypeTest {
    @Test fun storedTypesAndLabelsResolveAndUnknownTypesStayUnspecified() {
        assertEquals(VehicleType.PICKUP, VehicleType.fromStoredValue("PICKUP"))
        assertEquals(VehicleType.HATCHBACK, VehicleType.fromLabel("Hatchback"))
        assertEquals(VehicleType.CONVERTIBLE, VehicleType.fromStoredValue("CONVERTIBLE"))
        assertEquals(VehicleType.OTHER, VehicleType.fromStoredValue("UNKNOWN"))
    }
}
