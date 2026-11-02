package ca.gllavoie.vehicletracker.domain.calculation

import kotlin.math.roundToLong

enum class DistanceUnit { KILOMETRE, MILE }
enum class VolumeUnit { LITRE, US_GALLON, IMPERIAL_GALLON }
enum class EconomyUnit { L_PER_100_KM, KM_PER_LITRE, MPG_US, MPG_IMPERIAL }

data class FillUp(
    val odometer: Double,
    val distanceUnit: DistanceUnit,
    val volume: Double,
    val volumeUnit: VolumeUnit,
    val isFullTank: Boolean
)

/**
 * Calculate a full-tank interval. `start` and `end` are full-tank readings;
 * `end.volume` is the amount needed to refill at the ending reading.
 */
fun calculateEconomy(start: FillUp, end: FillUp, output: EconomyUnit): Double? {
    if (!start.isFullTank || !end.isFullTank || !start.odometer.isFinite() || !end.odometer.isFinite()) return null
    val distanceKm = distanceToKilometres(end.odometer, end.distanceUnit) - distanceToKilometres(start.odometer, start.distanceUnit)
    if (distanceKm <= 0.0 || start.volume <= 0.0 || end.volume <= 0.0) return null
    val litres = volumeToLitres(end.volume, end.volumeUnit)
    if (litres <= 0.0) return null
    val litresPer100Km = litres / distanceKm * 100.0
    return when (output) {
        EconomyUnit.L_PER_100_KM -> litresPer100Km
        EconomyUnit.KM_PER_LITRE -> distanceKm / litres
        EconomyUnit.MPG_US -> (distanceKm / KM_PER_MILE) / (litres / LITRES_PER_US_GALLON)
        EconomyUnit.MPG_IMPERIAL -> (distanceKm / KM_PER_MILE) / (litres / LITRES_PER_IMPERIAL_GALLON)
    }
}

/** Aggregate all recorded purchases after a starting full tank through the next full tank. */
fun calculateEconomyFromSeries(fillUps: List<FillUp>, output: EconomyUnit): Double? {
    val startIndex = fillUps.indexOfFirst { it.isFullTank }
    if (startIndex < 0) return null
    val nextIndex = (startIndex + 1 until fillUps.size).firstOrNull { fillUps[it].isFullTank } ?: return null
    val interval = fillUps.subList(startIndex, nextIndex + 1)
    if (interval.any { !it.odometer.isFinite() || it.odometer < 0.0 || !it.volume.isFinite() || it.volume <= 0.0 }) return null
    val normalizedOdometersKm = interval.map { distanceToKilometres(it.odometer, it.distanceUnit) }
    if (normalizedOdometersKm.zipWithNext().any { (previous, current) -> current <= previous }) return null
    val distanceKm = normalizedOdometersKm.last() - normalizedOdometersKm.first()
    val litres = interval.drop(1).sumOf { volumeToLitres(it.volume, it.volumeUnit) }
    if (distanceKm <= 0.0 || litres <= 0.0) return null
    val litresPer100Km = litres / distanceKm * 100.0
    return when (output) {
        EconomyUnit.L_PER_100_KM -> litresPer100Km
        EconomyUnit.KM_PER_LITRE -> distanceKm / litres
        EconomyUnit.MPG_US -> (distanceKm / KM_PER_MILE) / (litres / LITRES_PER_US_GALLON)
        EconomyUnit.MPG_IMPERIAL -> (distanceKm / KM_PER_MILE) / (litres / LITRES_PER_IMPERIAL_GALLON)
    }
}

fun volumeToLitres(volume: Double, unit: VolumeUnit): Double = when (unit) {
    VolumeUnit.LITRE -> volume
    VolumeUnit.US_GALLON -> volume * LITRES_PER_US_GALLON
    VolumeUnit.IMPERIAL_GALLON -> volume * LITRES_PER_IMPERIAL_GALLON
}

fun distanceToKilometres(distance: Double, unit: DistanceUnit): Double = when (unit) {
    DistanceUnit.KILOMETRE -> distance
    DistanceUnit.MILE -> distance * KM_PER_MILE
}


private const val KM_PER_MILE = 1.609344
private const val LITRES_PER_US_GALLON = 3.785411784
private const val LITRES_PER_IMPERIAL_GALLON = 4.54609
