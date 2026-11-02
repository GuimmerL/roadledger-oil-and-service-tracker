package ca.gllavoie.vehicletracker

import android.content.Context
import ca.gllavoie.vehicletracker.data.local.VehicleDatabaseFactory
import ca.gllavoie.vehicletracker.data.repository.RoomVehicleRepository
import ca.gllavoie.vehicletracker.data.repository.RoomOdometerRepository
import ca.gllavoie.vehicletracker.data.repository.OdometerRepository
import ca.gllavoie.vehicletracker.data.repository.RoomOilChangeRepository
import ca.gllavoie.vehicletracker.data.repository.OilChangeRepository
import ca.gllavoie.vehicletracker.data.repository.ReceiptRepository
import ca.gllavoie.vehicletracker.data.repository.BackupRepository
import ca.gllavoie.vehicletracker.data.repository.RoomFuelRepository
import ca.gllavoie.vehicletracker.data.repository.FuelRepository
import ca.gllavoie.vehicletracker.data.repository.RoomVehicleSpecificationRepository
import ca.gllavoie.vehicletracker.data.repository.VehicleSpecificationRepository
class AppContainer(context: Context) {
    private val database = VehicleDatabaseFactory.create(context)
    val vehicleRepository = RoomVehicleRepository(database.vehicleDao())
    val odometerRepository: OdometerRepository = RoomOdometerRepository(database.vehicleDao(), vehicleRepository)
    val oilChangeRepository: OilChangeRepository = RoomOilChangeRepository(database.vehicleDao(), vehicleRepository)
    val receiptRepository = ReceiptRepository(context.applicationContext, database)
    val backupRepository = BackupRepository(context.applicationContext, database)
    val fuelRepository: FuelRepository = RoomFuelRepository(database.vehicleDao(), vehicleRepository)
    val specificationRepository: VehicleSpecificationRepository = RoomVehicleSpecificationRepository(database.vehicleDao(), vehicleRepository)
}
