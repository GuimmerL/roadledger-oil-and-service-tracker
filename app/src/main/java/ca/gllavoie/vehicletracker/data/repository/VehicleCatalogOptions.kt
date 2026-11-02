package ca.gllavoie.vehicletracker.data.repository

import ca.gllavoie.vehicletracker.data.local.VehicleSpecificationEntity

/** Dropdown options derived from the catalog's preserved year-range text. */
object VehicleCatalogOptions {
    fun isOilIntervalVerified(status: String?): Boolean =
        status == "USER_VERIFIED"

    fun requiresSelectionWarning(status: String?): Boolean =
        status == VehicleCatalogSeeder.AI_GENERATED_UNVERIFIED

    private val supportedRange = Regex("^\\s*(\\d{4})(?:\\s*[-–]\\s*(\\d{4}))?(?:\\s*\\([^)]*\\))?\\s*$")
    private val engineDisplacement = Regex("\\b\\d+(?:\\.\\d+)?\\s*L\\b", RegexOption.IGNORE_CASE)
    private val engineArchitecture = Regex("\\b(?:I[3-8]|V[4-8]|H[46]|W(?:12|16)|Inline[- ]?[3-6]|Flat[- ]?[46]|Boxer|Rotary|Diesel|TDI|TFSI|Hybrid|PHEV|HEV|Electric|EV)\\b", RegexOption.IGNORE_CASE)
    private val distanceToken = Regex("\\b\\d[\\d,]*(?:\\.\\d+)?\\s*(?:mi|miles|km|kilometers)\\b", RegexOption.IGNORE_CASE)

    private fun splitTopLevelEngineList(value: String): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        var start = 0
        value.forEachIndexed { index, char ->
            when (char) {
                '(' -> depth += 1
                ')' -> depth = (depth - 1).coerceAtLeast(0)
                '/' -> if (depth == 0 && index > 0 && index < value.lastIndex && value[index - 1].isWhitespace() && value[index + 1].isWhitespace()) {
                    parts += value.substring(start, index).trim()
                    start = index + 1
                }
            }
        }
        parts += value.substring(start).trim()
        return parts.filter(String::isNotBlank)
    }

    private fun expandEngineConfigurations(entry: VehicleSpecificationEntity): List<VehicleSpecificationEntity> {
        val rawEngine = entry.engine?.trim().orEmpty()
        if (rawEngine.isBlank()) return listOf(entry)
        val variants = splitTopLevelEngineList(rawEngine)
        val rawNormal = rawNormalInterval(entry).orEmpty()
        val rawSevere = entry.serviceNotes.orEmpty().lineSequence()
            .firstOrNull { it.startsWith("Raw severe-service interval:") }
            ?.substringAfter(":")?.trim().orEmpty()
        val parsedIntervals = CatalogOilIntervalParser.parse(rawNormal, rawSevere)
        val intervalAmbiguous = listOf(rawNormal to parsedIntervals.normalDistance, rawSevere to parsedIntervals.severeDistance)
            .any { (raw, parsedDistance) -> distanceToken.findAll(raw).count() > 1 && parsedDistance == null }
        val canSplit = variants.size > 1 && !intervalAmbiguous && variants.all {
            engineDisplacement.containsMatchIn(it) && engineArchitecture.containsMatchIn(it)
        }
        val configurations = if (canSplit) variants else listOf(rawEngine)
        return configurations.map { variant -> entry.copy(engine = variant) }
    }

    private fun verificationRank(status: String): Int = when (status) {
        "OFFICIAL_INTERVAL_RESEARCHED" -> 0
        "USER_VERIFIED" -> 1
        else -> 2
    }

    fun modelYears(entries: List<VehicleSpecificationEntity>): List<Int> = entries
        .flatMap { yearRange(it)?.toList().orEmpty() }
        .distinct()
        .sortedDescending()

    fun entriesForYear(entries: List<VehicleSpecificationEntity>, year: Int): List<VehicleSpecificationEntity> =
        entries.filter { yearRange(it)?.contains(year) == true }

    fun makesForYear(entries: List<VehicleSpecificationEntity>, year: Int): List<String> =
        entriesForYear(entries, year).map(VehicleSpecificationEntity::make).distinct().sorted()

    fun modelsForYearAndMake(entries: List<VehicleSpecificationEntity>, year: Int, make: String): List<String> =
        entriesForYear(entries, year)
            .filter { it.make.equals(make, ignoreCase = true) }
            .map(VehicleSpecificationEntity::model)
            .distinct()
            .sorted()

    fun configurationsForVehicle(
        entries: List<VehicleSpecificationEntity>,
        year: Int,
        make: String,
        model: String
    ): List<VehicleSpecificationEntity> = entriesForYear(entries, year)
        .filter { it.make.equals(make, ignoreCase = true) && it.model.equals(model, ignoreCase = true) }
        .flatMap(::expandEngineConfigurations)
        .distinctBy { listOf(it.id, it.engine.orEmpty(), it.verificationStatus).joinToString("|") }
        .sortedWith(compareBy<VehicleSpecificationEntity>({ verificationRank(it.verificationStatus) }, { it.engine.orEmpty() }, { it.id }))

    fun matchesSelectedConfiguration(
        selectedSpecification: VehicleSpecificationEntity?,
        configurations: List<VehicleSpecificationEntity>,
        selectedEngine: String
    ): Boolean = selectedSpecification != null && selectedEngine.isNotBlank() && (
        configurations.any {
            it.id == selectedSpecification.id && it.engine.equals(selectedEngine, ignoreCase = true)
        } || selectedSpecification.engine.equals(selectedEngine, ignoreCase = true)
    )

    fun yearRange(entry: VehicleSpecificationEntity): IntRange? {
        val years = entry.serviceNotes.orEmpty().lineSequence()
            .firstOrNull { it.startsWith("Years:") }
            ?.substringAfter(":")
            ?.trim()
            ?: entry.modelYear?.toString()
            ?: return null
        val match = supportedRange.matchEntire(years) ?: return null
        val first = match.groupValues[1].toIntOrNull() ?: return null
        val last = match.groupValues[2].takeIf(String::isNotBlank)?.toIntOrNull() ?: first
        return minOf(first, last)..maxOf(first, last)
    }

    fun marketRegion(entry: VehicleSpecificationEntity): String? = entry.serviceNotes.orEmpty().lineSequence()
        .firstOrNull { it.startsWith("Market / Region:") }
        ?.substringAfter(":")
        ?.trim()
        ?.takeIf(String::isNotBlank)

    fun rawNormalInterval(entry: VehicleSpecificationEntity): String? = entry.serviceNotes.orEmpty().lineSequence()
        .firstOrNull { it.startsWith("Raw normal-service interval:") }
        ?.substringAfter(":")
        ?.trim()
        ?.takeIf(String::isNotBlank)
}
