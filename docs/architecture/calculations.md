# Calculation rules

The pure Kotlin calculation code is in `domain/calculation/OilSchedule.kt` and `FuelEconomy.kt`. Calculations run locally and do not require a live vehicle API. No manufacturer interval is assumed when the user has not entered one.

## Oil-change prediction

For the latest oil-change entry, the app considers independent mileage and time limits. The first reached limit makes maintenance due.

1. `next due odometer = service odometer + distance interval in the service odometer unit`. A verified catalog interval in the other unit is converted with 1 mile = 1.609344 km and rounded down to a whole unit.
2. `time deadline = service date + recorded time interval in calendar months` (month-end/leap-year behavior uses `LocalDate.plusMonths`).
3. If there are two or more dated odometer readings, the app may estimate average use from the historical span only when the readings are monotonic and use the same unit. It will not extrapolate across a reset/decrease.
4. `mileage deadline date = current odometer date + ceil(max(distance remaining, 0) / average distance per day)` if the average is positive and finite. Otherwise the mileage date is unavailable and the time limit is used where available.
5. Estimated due date is the earlier available mileage-projection date and time deadline. It is explicitly shown as an estimate.
6. Default due-soon thresholds are 1,000 odometer units and 30 days. These are currently fixed defaults, not user-configurable.
7. At exact equality to a limit, status is `DUE`; only after passing it is status `OVERDUE`. If no usable interval exists, status is `UNAVAILABLE`, not zero-day due.
8. Missing/zero usage, missing/stale odometer, negative values, deadline overflow, and odometer below the last service are called out as assumptions/inconsistencies. A zero average never causes division by zero.
9. Recording/editing a service does not rewrite other historical service data. An oil-change entry and its odometer event are stored together. New odometer readings alone do not reset the oil schedule.

Schedule distance interval precedence is the latest service's explicit override, then a matching vehicle-specific interval, then the catalog interval selected by the vehicle's saved mode (`NORMAL` or `SEVERE`) with status `USER_VERIFIED`. The interval mode applies only to the catalog choice; it does not override a service-record or vehicle-specific interval. Catalog distances are stored in KM and converted to the service odometer unit. AI-generated and source-researched records remain excluded from calculations until the user verifies the selected interval. A projection also requires a recorded last oil service: the app does not assume that adding a vehicle means its oil was just changed. For a mileage-based due-date projection, the app uses the average across increasing dated readings when available; otherwise it uses that vehicle's optional starting daily-use estimate, stored canonically in km/day and converted to the service unit. Each new reading recalculates the observed average and supersedes the starting estimate when enough readings are available. Without a rate, the next due odometer can still be shown, but its mileage-based date is unavailable. No usable distance/time interval means no inferred next service date/mileage.

## Fuel economy

For full-tank endpoints and all fuel purchased after the starting full tank through the ending refill:

- `L/100 km = litres consumed / kilometres travelled × 100`
- `km/L = kilometres travelled / litres consumed`
- `US MPG = miles travelled / (litres consumed / 3.785411784)`
- `Imperial MPG = miles travelled / (litres consumed / 4.54609)`
- `1 mile = 1.609344 km`

The first full-tank fill amount is excluded from consumption. Intermediate partial fills and the final refuel volume are included. Distance must increase; volume must be finite and positive. UI shows the latest interval bounded by two full tanks; without two valid full-tank records, it reports insufficient data rather than inventing a value.

Fuel entered in litres, US gallons, or Imperial gallons is converted to millilitres for storage. A fill-up and its odometer event are committed atomically. Lower/out-of-order readings require explicit user confirmation and are retained as confirmed events.

## Tests

`CalculationTest.kt` covers fixed-date examples, independent deadline ordering, exact due/overdue boundaries, invalid/overflow intervals, zero/unknown usage, month-end/leap dates, unit conversion, Imperial-vs-US MPG, partial fills, and invalid/incomplete fuel series. Repository tests cover Room persistence, record validation, receipt integrity, backup/restore and review-required odometer anomalies. Test counts/results are recorded in the README after a full build run.
