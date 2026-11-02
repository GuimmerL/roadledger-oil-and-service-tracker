package ca.gllavoie.vehicletracker.data.repository

/** Normalizes only unambiguous interval values; all original text remains in catalog notes. */
data class ParsedCatalogOilIntervals(
    val normalDistance: Int?,
    val distanceUnit: String?,
    val normalMonths: Int?,
    val severeDistance: Int?,
    val severeMonths: Int?,
    val normalizationNotes: List<String>
)

object CatalogOilIntervalParser {
    private const val UNIT = "mi|miles?|km|kilomet(?:er|re)s?"
    private val distanceRange = Regex("(?i)(?<![\\d.])(\\d[\\d,]*)\\s*[-–]\\s*(\\d[\\d,]*)\\s*($UNIT)\\b")
    private val distanceToken = Regex("(?i)(?<![\\d.])(\\d[\\d,]*)\\s*($UNIT)\\b")
    private val monthRange = Regex("(?i)(?<![\\d.])(\\d{1,3})\\s*[-–]\\s*(\\d{1,3})\\s*(?:mo|mos|months?)\\b")
    private val monthToken = Regex("(?i)(?<![\\d.])(\\d{1,3})\\s*(?:mo|mos|months?)\\b")

    fun parse(normalRaw: String?, severeRaw: String?): ParsedCatalogOilIntervals {
        val notes = mutableListOf<String>()
        val normal = parseOne(normalRaw.orEmpty(), "normal", notes)
        val severe = parseOne(severeRaw.orEmpty(), "severe", notes)
        val unit = normal.unit ?: severe.unit
        val severeDistance = when {
            severe.distance == null -> null
            unit == severe.unit -> severe.distance
            else -> {
                notes += "Severe-service distance uses a different unit and was left as raw text."
                null
            }
        }
        return ParsedCatalogOilIntervals(
            normalDistance = normal.distance,
            distanceUnit = unit,
            normalMonths = normal.months,
            severeDistance = severeDistance,
            severeMonths = severe.months,
            normalizationNotes = notes.distinct()
        )
    }

    private data class OneInterval(val distance: Int?, val unit: String?, val months: Int?)

    private fun parseOne(raw: String, label: String, notes: MutableList<String>): OneInterval {
        val text = raw.trim()
        if (text.isEmpty()) return OneInterval(null, null, null)
        val lower = text.lowercase()
        val blockedDistance = "lifetime" in lower || Regex("\\binspect\\s+at\\b").containsMatchIn(lower)
        val range = if (blockedDistance) null else distanceRange.find(text)
        val tokens = if (blockedDistance) emptyList() else distanceToken.findAll(text).toList()
        val distanceAndUnit = when {
            range != null && tokens.none { it.range.first !in range.range } -> {
                val amount = range.groupValues[1].toIntOrNullGrouped()
                val unit = normalizeUnit(range.groupValues[3])
                if (amount != null) {
                    notes += "${label.replaceFirstChar(Char::uppercase)}-service interval imported at the lower endpoint of a stated range."
                    amount to unit
                } else null
            }
            range == null && tokens.size == 1 -> {
                val amount = tokens.single().groupValues[1].toIntOrNullGrouped()
                val unit = normalizeUnit(tokens.single().groupValues[2])
                if (amount != null) amount to unit else null
            }
            else -> {
                if (!blockedDistance && tokens.size > 1) notes += "${label.replaceFirstChar(Char::uppercase)}-service distance has conditional or conflicting values; only the raw text was retained."
                null
            }
        }

        val monthRangeMatch = monthRange.find(text)
        val monthTokens = monthToken.findAll(text).toList()
        val months = when {
            monthRangeMatch != null && monthTokens.none { it.range.first !in monthRangeMatch.range } -> {
                val minimum = monthRangeMatch.groupValues[1].toIntOrNull()
                if (minimum != null) {
                    notes += "${label.replaceFirstChar(Char::uppercase)}-service time interval imported at the lower endpoint of a stated range."
                }
                minimum
            }
            monthRangeMatch == null && monthTokens.size == 1 -> monthTokens.single().groupValues[1].toIntOrNull()
            else -> null
        }
        if (monthTokens.size > 1 && monthRangeMatch == null) {
            notes += "${label.replaceFirstChar(Char::uppercase)}-service time has conditional or conflicting values; only the raw text was retained."
        }
        return OneInterval(distanceAndUnit?.first, distanceAndUnit?.second, months)
    }

    private fun normalizeUnit(unit: String): String =
        if (unit.startsWith("mi", ignoreCase = true)) "MILE" else "KM"

    private fun String.toIntOrNullGrouped(): Int? = replace(",", "").toIntOrNull()
}
