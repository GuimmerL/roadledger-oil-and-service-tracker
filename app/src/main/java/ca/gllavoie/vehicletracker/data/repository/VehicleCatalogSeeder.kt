package ca.gllavoie.vehicletracker.data.repository

import android.content.Context
import ca.gllavoie.vehicletracker.data.local.SpecificationSourceEntity
import ca.gllavoie.vehicletracker.data.local.VehicleSpecificationEntity
import java.io.InputStream
import java.io.InputStreamReader
import java.math.BigDecimal
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Imports the production CSV without promoting imported verification claims to user verification. */
class VehicleCatalogSeeder(
    context: Context,
    private val repository: VehicleSpecificationRepository,
    private val openCatalogAsset: () -> InputStream = { context.assets.open(ASSET_NAME) }
) {
    suspend fun seedIfMissing(): Int = withContext(Dispatchers.IO) {
        if (repository.getSeededCatalogVersion() == CATALOG_VERSION) return@withContext 0

        val now = System.currentTimeMillis()
        val specifications = ArrayList<VehicleSpecificationEntity>(EXPECTED_RECORD_COUNT)
        val sources = ArrayList<SpecificationSourceEntity>(EXPECTED_RECORD_COUNT)
        val ids = HashSet<String>(EXPECTED_RECORD_COUNT)
        var headers: List<String>? = null

        openCatalogAsset().use { input ->
            val recordCount = CsvRecordReader.read(InputStreamReader(input, StandardCharsets.UTF_8)) { values ->
                val csvHeaders = headers
                if (csvHeaders == null) {
                    val normalizedHeaders = values.map(String::trim)
                    require(normalizedHeaders.distinct().size == normalizedHeaders.size) { "Production catalog has duplicate CSV headers." }
                    val missing = REQUIRED_COLUMNS - normalizedHeaders.toSet()
                    require(missing.isEmpty()) { "Production catalog is missing required columns: ${missing.sorted().joinToString()}" }
                    headers = normalizedHeaders
                    return@read
                }
                require(values.size == csvHeaders.size) { "Production catalog row ${specifications.size + 2} has the wrong number of fields." }
                val row = csvHeaders.indices.associate { csvHeaders[it] to values[it].trim() }
                specifications += parseSpecification(row, now, ids)
                sources += parseSource(row, specifications.last(), csvHeaders)
            }
            require(recordCount == EXPECTED_RECORD_COUNT + 1) {
                "Production catalog record count is $recordCount; expected ${EXPECTED_RECORD_COUNT + 1} rows including its header."
            }
        }
        require(specifications.size == EXPECTED_RECORD_COUNT) { "Production catalog import was incomplete." }
        repository.seedCatalogIfMissing(specifications, sources)
        repository.markCatalogVersionSeeded(CATALOG_VERSION)
        specifications.size
    }

    private fun parseSpecification(
        row: Map<String, String>,
        now: Long,
        ids: MutableSet<String>
    ): VehicleSpecificationEntity {
        val csvId = row.required("vehicle_id")
        require(ids.add(csvId)) { "Production catalog contains duplicate vehicle_id '$csvId'." }
        val make = row.required("make")
        val model = row.required("model")
        val modelYear = row.required("model_year").toIntOrNull()
            ?: throw IllegalArgumentException("Production catalog has an invalid model_year for $csvId.")
        require(modelYear in 1886..9999) { "Production catalog has an out-of-range model_year for $csvId." }
        val verificationStatus = row.required("verification_status")
        require(verificationStatus == AI_GENERATED_UNVERIFIED || verificationStatus == OFFICIAL_INTERVAL_RESEARCHED) {
            "Production catalog has unsupported verification status '$verificationStatus' for $csvId."
        }
        val sourceName = row["verification_source_name"].cleanOrNull()
        val sourceUrl = row["verification_source_url"].cleanOrNull()
        if (verificationStatus == OFFICIAL_INTERVAL_RESEARCHED) {
            require(!sourceName.isNullOrBlank() && !sourceUrl.isNullOrBlank()) { "Researched interval row $csvId requires a source name and URL." }
            val uri = runCatching { URI(sourceUrl) }.getOrNull()
            require(uri != null && uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()) {
                "Researched interval row $csvId has an invalid source URL."
            }
        }

        val normalDistanceKm = row.requiredPositiveInt("normal_interval_km")
        row.requiredPositiveInt("normal_interval_mi")
        val normalMonths = row.requiredPositiveInt("normal_interval_months")
        val severeDistanceKm = row.optionalPositiveInt("severe_interval_km")
        val severeDistanceMiles = row.optionalPositiveInt("severe_interval_mi")
        val severeMonths = row.optionalPositiveInt("severe_interval_months")
        require((severeDistanceKm == null) == (severeDistanceMiles == null)) {
            "Production catalog has mismatched severe KM/MILE fields for $csvId."
        }
        row.requiredBoolean("uses_oil_life_monitor")
        row.requiredBoolean("requires_oil_change")
        require(row.required("oil_type_required") in OIL_TYPE_VALUES) { "Production catalog has an unsupported oil_type_required value for $csvId." }
        val capacityMl = row.optionalPositiveScaledInt("oil_capacity_liters", 1_000)
        val rawNormal = intervalText(row, "normal_interval_km", "normal_interval_mi", "normal_interval_months")
        val rawSevere = intervalText(row, "severe_interval_km", "severe_interval_mi", "severe_interval_months")
        val notes = buildString {
            appendLine("Catalog dataset: $DATASET_MARKER")
            appendLine("Catalog version: $CATALOG_VERSION")
            if (verificationStatus == AI_GENERATED_UNVERIFIED) {
                appendLine("AI-generated data; not manufacturer-verified. Double-check against the exact owner's manual before use.")
            } else {
                appendLine("An OEM interval source is listed; the user must review and confirm it before scheduling can use it. Other fields may remain unverified.")
            }
            appendLine("Years: $modelYear")
            appendLine("Market / Region: ${row["market_region"].orEmpty()}")
            appendLine("Engine / Trim: ${row["engine_trim"].orEmpty()}")
            appendLine("Raw oil recommendation: ${row["oil_recommendation"].orEmpty()}")
            appendLine("Raw oil filter type/form factor: ${row["filter_description"].orEmpty()}")
            appendLine("Raw normal-service interval: $rawNormal")
            appendLine("Raw severe-service interval: $rawSevere")
            row["generation_code"].cleanOrNull()?.let { appendLine("Generation code: $it") }
            row["uses_oil_life_monitor"].cleanOrNull()?.let { appendLine("Uses oil-life monitor: $it") }
            row["requires_oil_change"].cleanOrNull()?.let { appendLine("Requires oil change: $it") }
            row["drain_plug_torque_ft_lbs"].cleanOrNull()?.let { appendLine("Drain plug torque (ft-lb): $it") }
            row["drain_plug_socket_mm"].cleanOrNull()?.let { appendLine("Drain plug socket (mm): $it") }
            row["filter_wrench_size"].cleanOrNull()?.let { appendLine("Filter wrench size: $it") }
            row["oil_reset_procedure"].cleanOrNull()?.let { appendLine("Oil reset procedure: $it") }
            row["original_catalog_id"].cleanOrNull()?.let { appendLine("Original catalog ID: $it") }
            appendLine("Catalog metric status: METRIC_V2")
        }.trim()

        return VehicleSpecificationEntity(
            id = "$CATALOG_ID_PREFIX$csvId",
            make = make,
            model = model,
            modelYear = modelYear,
            engine = row["engine_trim"].cleanOrNull(),
            engineCode = row["oem_engine_code"].cleanOrNull(),
            viscosityGrade = row["viscosity_grade"].cleanOrNull(),
            manufacturerApproval = row["oem_approval_spec"].cleanOrNull(),
            capacityWithFilterMl = capacityMl,
            capacityWithoutFilterMl = null,
            distanceInterval = normalDistanceKm,
            distanceUnit = "KM",
            timeIntervalMonths = normalMonths,
            severeDistanceInterval = severeDistanceKm,
            severeTimeIntervalMonths = severeMonths,
            oilFilterPartNumber = row["filter_description"].cleanOrNull(),
            serviceNotes = notes,
            verificationStatus = verificationStatus,
            verifiedOn = null,
            createdAtUtc = now,
            updatedAtUtc = now
        )
    }

    private fun parseSource(
        row: Map<String, String>,
        specification: VehicleSpecificationEntity,
        headers: List<String>
    ): SpecificationSourceEntity {
        val status = specification.verificationStatus
        val suppliedName = row["verification_source_name"].cleanOrNull()
        val sourceName = if (status == AI_GENERATED_UNVERIFIED) {
            "vehicle_catalog_v2.0.0_production.csv (AI-generated; not an OEM source)"
        } else suppliedName.orEmpty()
        val sourceUrl = row["verification_source_url"].cleanOrNull()
        val rawRecord = JSONObject(headers.associateWith { row[it].orEmpty() }).toString()
        val notes = if (status == AI_GENERATED_UNVERIFIED) {
            "Imported production catalog row is AI-generated and remains unverified. Verify the exact vehicle, engine, market, and interval against its owner's manual before using it.\nCSV row: $rawRecord"
        } else {
            "Source research is for the oil interval only. User confirmation is required before the interval can affect scheduling; other fields may remain unverified.\nCSV row: $rawRecord"
        }
        return SpecificationSourceEntity(
            id = "production-source-${specification.id}",
            specificationId = specification.id,
            sourceName = sourceName,
            sourceUrl = sourceUrl,
            verifiedOn = null,
            verificationStatus = status,
            notes = notes
        )
    }

    private fun intervalText(row: Map<String, String>, kmField: String, mileField: String, monthField: String): String =
        buildList {
            row[kmField].cleanOrNull()?.let { add("$it km") }
            row[mileField].cleanOrNull()?.let { add("$it mi") }
            row[monthField].cleanOrNull()?.let { add("$it months") }
        }.joinToString(" / ").ifBlank { "Not specified" }

    private fun Map<String, String>.required(key: String): String =
        get(key).cleanOrNull() ?: throw IllegalArgumentException("Production catalog field '$key' is blank.")

    private fun Map<String, String>.requiredPositiveInt(key: String): Int =
        parsePositiveInt(required(key), key)

    private fun Map<String, String>.requiredBoolean(key: String): Boolean = when (required(key).lowercase()) {
        "true", "1", "yes" -> true
        "false", "0", "no" -> false
        else -> throw IllegalArgumentException("Production catalog field '$key' must be a boolean.")
    }

    private fun Map<String, String>.optionalPositiveInt(key: String): Int? =
        get(key).cleanOrNull()?.let { parsePositiveInt(it, key) }

    private fun parsePositiveInt(value: String, field: String): Int {
        val parsed = runCatching { BigDecimal(value).intValueExact() }.getOrElse {
            throw IllegalArgumentException("Production catalog field '$field' must be a whole number.")
        }
        require(parsed > 0) { "Production catalog field '$field' must be greater than zero." }
        return parsed
    }

    private fun Map<String, String>.optionalPositiveScaledInt(key: String, scale: Int): Int? =
        get(key).cleanOrNull()?.let { raw ->
            val parsed = runCatching { BigDecimal(raw).multiply(BigDecimal(scale)).intValueExact() }.getOrElse {
                throw IllegalArgumentException("Production catalog field '$key' is invalid or out of range.")
            }
            require(parsed > 0) { "Production catalog field '$key' must be greater than zero." }
            parsed
        }

    companion object {
        const val ASSET_NAME = "vehicle_catalog_v2.0.0_production.csv"
        const val CATALOG_VERSION = "2.0.0"
        const val DATASET_MARKER = "vehicle_catalog_v2.0.0_production"
        const val CATALOG_ID_PREFIX = "production-v2.0.0-"
        const val EXPECTED_RECORD_COUNT = 15_952
        const val AI_GENERATED_UNVERIFIED = "AI_GENERATED_UNVERIFIED"
        const val OFFICIAL_INTERVAL_RESEARCHED = "OFFICIAL_INTERVAL_RESEARCHED"
        private val OIL_TYPE_VALUES = setOf("FULL_SYNTHETIC", "CONVENTIONAL")
        private val REQUIRED_COLUMNS = setOf(
            "vehicle_id", "make", "model", "model_year", "market_region", "engine_trim",
            "normal_interval_km", "normal_interval_mi", "normal_interval_months",
            "severe_interval_km", "severe_interval_mi", "severe_interval_months",
            "verification_status", "verification_source_name", "verification_source_url", "original_catalog_id"
        )
    }
}

private fun String?.cleanOrNull(): String? = this?.trim()?.ifEmpty { null }
