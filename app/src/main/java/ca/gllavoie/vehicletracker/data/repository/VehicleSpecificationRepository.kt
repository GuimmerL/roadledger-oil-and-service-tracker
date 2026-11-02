package ca.gllavoie.vehicletracker.data.repository

import ca.gllavoie.vehicletracker.data.local.SpecificationSourceEntity
import ca.gllavoie.vehicletracker.data.local.AppSettingsEntity
import ca.gllavoie.vehicletracker.data.local.VehicleDao
import ca.gllavoie.vehicletracker.data.local.VehicleSpecificationEntity
import ca.gllavoie.vehicletracker.data.local.VehicleSpecificationOverrideEntity
import java.net.URI
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface VehicleSpecificationRepository {
    fun observeCatalog(): Flow<List<VehicleSpecificationEntity>>
    fun observeSpecificationsForDataset(idPrefix: String): Flow<List<VehicleSpecificationEntity>> =
        observeCatalog().map { entries -> entries.filter { it.id.startsWith(idPrefix) } }
    fun observeCatalogEntry(specificationId: String): Flow<VehicleSpecificationEntity?>
    suspend fun getCatalogEntry(specificationId: String): VehicleSpecificationEntity?
    suspend fun getSources(specificationId: String): List<SpecificationSourceEntity>
    suspend fun saveCatalogEntry(specification: VehicleSpecificationEntity, verifiedSource: SpecificationSourceEntity? = null)
    suspend fun seedCatalogIfMissing(specifications: List<VehicleSpecificationEntity>, sources: List<SpecificationSourceEntity>)
    fun observeOverride(vehicleId: String): Flow<VehicleSpecificationOverrideEntity?>
    suspend fun saveOverride(override: VehicleSpecificationOverrideEntity)
    suspend fun getSeededCatalogVersion(): String? = null
    suspend fun markCatalogVersionSeeded(version: String) = Unit
}

class RoomVehicleSpecificationRepository(
    private val dao: VehicleDao,
    private val vehicleRepository: VehicleRepository
) : VehicleSpecificationRepository {
    override fun observeCatalog(): Flow<List<VehicleSpecificationEntity>> = dao.observeSpecifications()

    override fun observeSpecificationsForDataset(idPrefix: String): Flow<List<VehicleSpecificationEntity>> =
        dao.observeSpecificationsForDataset(idPrefix)

    override fun observeCatalogEntry(specificationId: String): Flow<VehicleSpecificationEntity?> = dao.observeSpecification(specificationId)

    override suspend fun getCatalogEntry(specificationId: String): VehicleSpecificationEntity? = dao.getSpecification(specificationId)

    override suspend fun getSources(specificationId: String): List<SpecificationSourceEntity> = dao.getSpecificationSources(specificationId)

    override suspend fun seedCatalogIfMissing(specifications: List<VehicleSpecificationEntity>, sources: List<SpecificationSourceEntity>) {
        dao.seedSpecificationsIfMissing(specifications, sources)
    }

    override suspend fun saveCatalogEntry(specification: VehicleSpecificationEntity, verifiedSource: SpecificationSourceEntity?) {
        require(specification.make.isNotBlank()) { "Specification make is required." }
        require(specification.model.isNotBlank()) { "Specification model is required." }
        require(specification.modelYear == null || specification.modelYear in 1886..9999) { "Specification year is invalid." }
        require(specification.capacityWithFilterMl == null || specification.capacityWithFilterMl > 0) { "Oil capacity must be greater than zero." }
        require(specification.capacityWithoutFilterMl == null || specification.capacityWithoutFilterMl > 0) { "Oil capacity must be greater than zero." }
        require(specification.distanceInterval == null || specification.distanceInterval > 0) { "Distance interval must be greater than zero." }
        require(specification.timeIntervalMonths == null || specification.timeIntervalMonths > 0) { "Time interval must be greater than zero." }
        require(specification.severeDistanceInterval == null || specification.severeDistanceInterval > 0) { "Severe-service distance interval must be greater than zero." }
        require(specification.severeTimeIntervalMonths == null || specification.severeTimeIntervalMonths > 0) { "Severe-service time interval must be greater than zero." }
        require(specification.distanceInterval == null || specification.distanceUnit == "KM" || specification.distanceUnit == "MILE") { "Distance unit must be KM or MILE." }
        require(specification.verificationStatus in setOf("AI_GENERATED_UNVERIFIED", "USER_ENTERED_UNVERIFIED", "USER_VERIFIED", "OFFICIAL_INTERVAL_RESEARCHED")) { "Specification verification status is invalid." }
        verifiedSource?.let { source ->
            require(source.specificationId == specification.id) { "Specification source does not belong to this entry." }
            require(source.sourceName.isNotBlank()) { "Source name is required." }
            require(source.verificationStatus == specification.verificationStatus) { "Source and specification verification states must match." }
            require(source.verifiedOn.isNullOrBlank() || runCatching { LocalDate.parse(source.verifiedOn) }.isSuccess) { "Enter the source verification date as YYYY-MM-DD." }
        }
        if (specification.verificationStatus == "USER_VERIFIED") {
            requireNotNull(verifiedSource) { "A source name, URL, and verification date are required to mark data verified." }
            require(!verifiedSource.sourceUrl.isNullOrBlank()) { "A source URL is required to mark data verified." }
            val sourceUri = runCatching { URI(verifiedSource.sourceUrl.trim()) }.getOrNull()
            require(sourceUri != null && sourceUri.scheme in setOf("http", "https") && !sourceUri.host.isNullOrBlank()) { "Enter a valid HTTP or HTTPS source URL." }
            require(!verifiedSource.verifiedOn.isNullOrBlank()) { "A verification date is required to mark data verified." }
        }
        require(specification.createdAtUtc >= 0L && specification.updatedAtUtc >= specification.createdAtUtc) { "Specification timestamps are invalid." }
        val existing = dao.getSpecification(specification.id)
        val normalized = specification.copy(
            make = specification.make.trim(),
            model = specification.model.trim(),
            trim = specification.trim.cleanOrNull(),
            engine = specification.engine.cleanOrNull(),
            engineCode = specification.engineCode.cleanOrNull(),
            viscosityGrade = specification.viscosityGrade.cleanOrNull(),
            manufacturerApproval = specification.manufacturerApproval.cleanOrNull(),
            oilFilterPartNumber = specification.oilFilterPartNumber.cleanOrNull(),
            serviceNotes = specification.serviceNotes.cleanOrNull(),
            verifiedOn = verifiedSource?.verifiedOn?.takeIf { specification.verificationStatus in setOf("USER_VERIFIED", "OFFICIAL_INTERVAL_RESEARCHED") },
            createdAtUtc = existing?.createdAtUtc ?: specification.createdAtUtc
        )
        val normalizedSource = verifiedSource?.copy(
            sourceName = verifiedSource.sourceName.trim(),
            sourceUrl = verifiedSource.sourceUrl?.trim()?.ifEmpty { null }
        )
        dao.saveCatalogSpecification(normalized, normalizedSource)
    }

    override fun observeOverride(vehicleId: String): Flow<VehicleSpecificationOverrideEntity?> =
        dao.observeSpecificationOverride(vehicleId)

    override suspend fun getSeededCatalogVersion(): String? = dao.getSettingValue(CATALOG_SEED_VERSION_KEY)

    override suspend fun markCatalogVersionSeeded(version: String) {
        require(version.isNotBlank()) { "Catalog version is required." }
        dao.upsertSetting(AppSettingsEntity(CATALOG_SEED_VERSION_KEY, version, System.currentTimeMillis()))
    }

    private fun String?.cleanOrNull(): String? = this?.trim()?.ifEmpty { null }

    private companion object {
        const val CATALOG_SEED_VERSION_KEY = "catalog.seed.version"
    }

    override suspend fun saveOverride(override: VehicleSpecificationOverrideEntity) {
        val vehicle = requireNotNull(vehicleRepository.getVehicle(override.vehicleId)) { "Vehicle was not found." }
        require(vehicle.archivedAtUtc == null) { "Restore this vehicle before editing its recommendations." }
        require(override.viscosityGrade.isNullOrBlank() || override.viscosityGrade.trim().length <= 32) { "Oil viscosity is too long." }
        require(override.manufacturerApproval.isNullOrBlank() || override.manufacturerApproval.trim().length <= 100) { "Manufacturer approval is too long." }
        require(override.capacityWithFilterMl == null || override.capacityWithFilterMl > 0) { "Oil capacity must be greater than zero." }
        require(override.distanceInterval == null || override.distanceInterval > 0) { "Distance interval must be greater than zero." }
        require(override.timeIntervalMonths == null || override.timeIntervalMonths > 0) { "Time interval must be greater than zero." }
        require(override.distanceInterval == null || override.distanceUnit == vehicle.odometerUnit) { "Distance interval unit must match the vehicle." }
        require(override.distanceUnit == null || override.distanceUnit == vehicle.odometerUnit) { "Distance interval unit must match the vehicle." }
        require(override.oilFilterPartNumber.isNullOrBlank() || override.oilFilterPartNumber.trim().length <= 80) { "Oil filter part number is too long." }
        require(override.sourceDescription.isNullOrBlank() || override.sourceDescription.trim().length <= 500) { "Specification source note is too long." }
        require(override.updatedAtUtc >= 0L) { "Specification timestamp is invalid." }
        require(listOf(override.viscosityGrade, override.manufacturerApproval, override.capacityWithFilterMl, override.distanceInterval, override.timeIntervalMonths, override.oilFilterPartNumber).any { value ->
            when (value) {
                null -> false
                is String -> value.isNotBlank()
                else -> true
            }
        }) { "Enter at least one vehicle-specific specification." }
        val existing = dao.getSpecificationOverride(override.vehicleId)
        dao.upsertSpecificationOverride(
            override.copy(
                id = existing?.id ?: override.id,
                viscosityGrade = override.viscosityGrade.cleanOrNull()?.uppercase(),
                manufacturerApproval = override.manufacturerApproval.cleanOrNull(),
                oilFilterPartNumber = override.oilFilterPartNumber.cleanOrNull(),
                sourceDescription = override.sourceDescription.cleanOrNull(),
                updatedAtUtc = maxOf(override.updatedAtUtc, System.currentTimeMillis())
            )
        )
    }
}
