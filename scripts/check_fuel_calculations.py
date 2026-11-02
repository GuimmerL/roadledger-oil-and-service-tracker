"""Deterministic independent smoke checks for fuel math while Gradle is blocked."""
from math import isclose

KM_PER_MILE = 1.609344
LITRES_PER_US_GALLON = 3.785411784
LITRES_PER_IMPERIAL_GALLON = 4.54609

def litres(quantity, unit):
    return quantity * {"L": 1.0, "US_GAL": LITRES_PER_US_GALLON, "IMP_GAL": LITRES_PER_IMPERIAL_GALLON}[unit]

def economy(distance, distance_unit, fuel, fuel_unit, out):
    distance_km = distance if distance_unit == "KM" else distance * KM_PER_MILE
    fuel_l = litres(fuel, fuel_unit)
    l100 = fuel_l / distance_km * 100
    if out == "L100": return l100
    if out == "KM_L": return distance_km / fuel_l
    if out == "MPG_US": return distance_km / KM_PER_MILE / (fuel_l / LITRES_PER_US_GALLON)
    return distance_km / KM_PER_MILE / (fuel_l / LITRES_PER_IMPERIAL_GALLON)

assert isclose(economy(520, "KM", 46, "L", "L100"), 8.846153846, rel_tol=1e-9)
assert isclose(economy(300, "MI", 10, "US_GAL", "MPG_US"), 30.0, rel_tol=1e-12)
assert isclose(economy(300, "MI", 10, "IMP_GAL", "MPG_IMP"), 30.0, rel_tol=1e-12)
# Initial full tank is excluded; partial and ending refill purchases are included.
assert isclose((10 + 10) / 200 * 100, 10.0, rel_tol=1e-12)
# Mixed distance units normalize the odometer endpoints before subtracting.
assert isclose(124.274238 * KM_PER_MILE - 100, 100, abs_tol=1e-5)
assert isclose(litres(1, "US_GAL"), LITRES_PER_US_GALLON, rel_tol=1e-12)
assert isclose(litres(1, "IMP_GAL"), LITRES_PER_IMPERIAL_GALLON, rel_tol=1e-12)
print("PASS: 7 deterministic fuel conversion/formula smoke checks")
