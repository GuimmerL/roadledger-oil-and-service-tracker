package ca.gllavoie.vehicletracker.data.repository

import ca.gllavoie.vehicletracker.data.local.VehicleDao
import ca.gllavoie.vehicletracker.data.local.VehicleEntity
import ca.gllavoie.vehicletracker.domain.calculation.OilIntervalMode
import ca.gllavoie.vehicletracker.domain.vehicle.VehicleType
import kotlinx.coroutines.flow.Flow

interface VehicleRepository {
    fun observeActiveVehicles(): Flow<List<VehicleEntity>>
    fun observeArchivedVehicles(): Flow<List<VehicleEntity>>
    suspend fun getVehicle(vehicleId: String): VehicleEntity?
    suspend fun save(vehicle: VehicleEntity)
    suspend fun archive(vehicleId: String, nowUtc: Long): Boolean
    suspend fun restore(vehicleId: String, nowUtc: Long): Boolean
    suspend fun deletePermanently(vehicleId: String): VehicleDao.VehicleDeletionSnapshot
}

class RoomVehicleRepository(private val dao: VehicleDao) : VehicleRepository {
    override fun observeActiveVehicles(): Flow<List<VehicleEntity>> = dao.observeActiveVehicles()

    override fun observeArchivedVehicles(): Flow<List<VehicleEntity>> = dao.observeArchivedVehicles()

    override suspend fun getVehicle(vehicleId: String): VehicleEntity? = dao.getVehicle(vehicleId)

    override suspend fun save(vehicle: VehicleEntity) {
        val make = vehicle.make.trim()
        val model = vehicle.model.trim()
        require(make.isNotEmpty()) { "Vehicle make is required." }
        require(model.isNotEmpty()) { "Vehicle model is required." }
        require(vehicle.modelYear == null || vehicle.modelYear in 1886..9999) { "Enter a valid model year." }
        require(vehicle.odometerUnit == "KM" || vehicle.odometerUnit == "MILE") { "Odometer unit must be KM or MILE." }
        require(VehicleType.entries.any { it.storedValue == vehicle.vehicleType }) { "Vehicle type is invalid." }
        require(OilIntervalMode.entries.any { it.storedValue == vehicle.oilIntervalMode }) { "Oil interval mode must be NORMAL or SEVERE." }
        require(vehicle.startingDailyUsageKmPerDay == null ||
            (vehicle.startingDailyUsageKmPerDay.isFinite() && vehicle.startingDailyUsageKmPerDay > 0.0 && vehicle.startingDailyUsageKmPerDay <= 3_218.688)
        ) { "Starting daily use must be a finite positive value up to 3,218.688 km/day." }
        require(vehicle.createdAtUtc >= 0L && vehicle.updatedAtUtc >= vehicle.createdAtUtc) {
            "Vehicle timestamps are invalid."
        }
        val existing = dao.getVehicle(vehicle.id)
        if (existing == null) {
            dao.insertVehicle(vehicle.copy(make = make, model = model))
        } else {
            val normalized = vehicle.copy(make = make, model = model, createdAtUtc = existing.createdAtUtc)
            check(dao.updateVehicle(normalized) == 1) { "Vehicle could not be updated." }
        }
    }

    override suspend fun archive(vehicleId: String, nowUtc: Long): Boolean {
        require(nowUtc >= 0L) { "Archive timestamp cannot be negative." }
        return dao.archiveVehicle(vehicleId, nowUtc) == 1
    }

    override suspend fun restore(vehicleId: String, nowUtc: Long): Boolean {
        require(nowUtc >= 0L) { "Restore timestamp cannot be negative." }
        return dao.restoreVehicle(vehicleId, nowUtc) == 1
    }

    override suspend fun deletePermanently(vehicleId: String): VehicleDao.VehicleDeletionSnapshot =
        dao.deleteVehicleAndRecords(vehicleId)
}
