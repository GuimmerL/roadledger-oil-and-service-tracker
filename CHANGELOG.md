# Changelog

All notable changes to this project are documented here.

## [Unreleased]
- Catalog data revision 2.1.0: correct 1,872 cells across 1,440 rows using only in-row evidence (month decimals 7.5 → 5 and 3.75 → 75, approval formatting, `API C-4`/`API CK-4`, induction/fuel labels). Merge 940 malformed engine-list fragments back into their full variants, remove 7 exact duplicate rows, and remove 14 Tesla electric drive-unit rows (gearbox fluid, not engine oil). Catalog is now 15,149 rows.
- Catalog seeder: refresh untouched `AI_GENERATED_UNVERIFIED` rows on a seed-version change and remove rows that no longer exist in the bundled file (unless a vehicle references them). User-edited, verified, researched, and referenced legacy rows are preserved. Add a regression test for the re-seed path.
- Platform: edge-to-edge with light/dark system-bar styling, `Theme.Material3.DayNight` window, predictive-back opt-in, `adjustResize` keyboard mode, and single-top notification deep links that open the due vehicle.
- Navigation: replace the toolbar dropdown with a Material `NavigationBar` on compact screens and a `NavigationRail` on medium/expanded widths; Material icons replace text glyphs.
- Accessibility: merge garage-card semantics, remove duplicate chip labels, add a monochrome notification icon, and give the oil reminder a tap action plus reminder category.
- Performance: hoist Compose flows into `remember` so collection does not restart on recomposition, cache catalog option lists, and pre-normalize catalog search text.
- Backup/restore: stream the archive to/from a temp file with `ZipFile` instead of materializing all attachment bytes in memory; cap entry count as well as total bytes.
- UI polish: full-screen-width cap on expanded layouts, loading-vs-missing states in dialogs, saveable restore selection, and date fields replaced with Material date pickers.
- Localization: move user-visible strings into `strings.xml` with placeholders.
- Remove unreachable Dashboard screen/ViewModel/test and the unused `navigation-compose` dependency.
- Add `scripts/audit_vehicle_catalog.py`, `scripts/fix_vehicle_catalog.py`, and `scripts/repair_engine_fragments.py` (all support `--dry-run`).
- Verified offline `:app:testDebugUnitTest assembleDebug`: 145 tests passed (0 failures/errors/skips) across 24 suites. No emulator/device was available for UI testing.

## [0.5.3] - Release
- Redraw vehicle-type icons with distinct silhouettes, including a flat-tail hatchback, longer wagon, taller SUV, and open-top convertible.
- Add persistent garage sorting by name, newest/oldest model year, or recently added.
- Verified offline `:app:testDebugUnitTest assembleDebug`: 146 tests passed (0 failures/errors/skips). APK is debug-signed; no emulator/device was available for UI testing.

## [0.5.2] - Debug build
- Simplify the garage list to compact vehicle summaries; selecting a vehicle opens a detail page with extended specifications, odometer/use data, service history, and maintenance actions. Toolbar and system back return to the list.
- Import the 15,952-row production vehicle catalog with normal/severe intervals and preserved source fields; warn when an AI-generated unverified record is selected.
- Add a per-vehicle Normal / Severe (heavy use) interval selector. Existing service and manual vehicle overrides retain precedence; emphasize the remaining oil-change distance.
- Upgrade Room to schema v3 with a non-destructive default-Normal migration, and upgrade v1/v2 backups during restore.

## [0.5.0] - Debug build
- Expand the catalog from 481 to 518 entries by adding 37 exact-year OEM interval suggestions for Infiniti, Mazda, Lexus, and Subaru, in addition to the five existing Toyota/Lexus suggestions. All 476 imported AI rows remain explicitly unverified.
- Preserve source interval wording while storing every structured catalog distance in KM; keep researched intervals excluded from scheduling until explicit user verification. Engine/trim and other specifications may remain unverified.
- Record that Mazda monitor-based schedules cannot be exactly reproduced by a static reminder, and retain the manual's maximum interval only as a confirmation-required suggestion.
- Add research audit notes documenting the incomplete OEM coverage and unresolved fields rather than marking unsupported rows verified.
- Verified offline `:app:testDebugUnitTest assembleDebug`: 124 tests across 19 suites passed (0 failures/errors/skips). APK: 11,941,031 bytes, debug-signed; 518 catalog entries included. No device/emulator UI test was available.

## [0.4.0] - Debug build
- Normalize every structured catalog distance interval to KM while retaining the original source-unit wording; use deterministic IDs to preserve existing catalog links.
- Migrate untouched AI catalog rows in existing databases to metric intervals without overwriting user-edited or verified rows; downgrade legacy agent-verified suggestions to source-researched pending user confirmation.
- Verify year-range dropdown selection and conservative engine/trim splitting, with ambiguous engine-specific schedules kept grouped.
- Verified offline `:app:testDebugUnitTest assembleDebug`: 124 tests across 19 suites passed (0 failures/errors/skips). APK: 11,938,107 bytes, debug-signed; 481 catalog entries included. No device/emulator UI test was available.

## [0.3.0] - Debug build
- Add five exact-year Toyota/Lexus source-researched oil-interval suggestions while retaining the supplied 476 AI-generated rows as unverified; every imported interval still requires explicit user verification before it drives schedules.
- Split clear engine variants and recognized trims into dropdown choices when the source interval is unambiguous; preserve grouped configurations with conditional engine-specific interval text.
- Re-run the idempotent catalog seed on vehicle-form entry so new supplements reach existing databases without overwriting edits.
- Replace the misleading “Not verified” schedule label with “Schedule unavailable” and add card guidance for missing service records, intervals, or daily-use rates.
- Add catalog research notes and source links; bump version to 0.3.0.
- Verified offline `:app:testDebugUnitTest assembleDebug`: 123 tests across 19 suites passed (0 failures/errors/skips). APK: 11,896,043 bytes, debug-signed; 481 catalog entries included. No device/emulator UI test was available.

## [0.2.0] - Debug build
- Catalog oil intervals display in the selected vehicle's KM/MILE unit; verified catalog distances are converted to the service odometer unit for scheduling.
- Add an optional per-vehicle starting daily-use estimate. The app uses it until dated odometer readings establish an observed average, then recalculates that average as mileage is updated. The starting value is stored canonically as km/day.
- Add a visible Update current mileage card action and move vehicle Edit/Archive/Restore/Delete actions into the three-dot menu.
- Upgrade Room schema to v2 with a non-destructive v1→v2 migration. Room-v1 backup archives are upgraded during restore with an empty starting-use value.
- Verified offline `:app:testDebugUnitTest assembleDebug`: 117 tests across 19 suites passed (0 failures/errors/skips). APK: 11,894,583 bytes, debug-signed; no device/emulator UI test was available.

## [0.1.0] - Previous debug baseline
- Simplify navigation to one vehicle home that combines due-status, garage, oil-change, and fuel workflows; move Backup/Restore and Settings into the hamburger menu.
- Add vehicle-card expansion with last odometer/service, recommended interval and estimated due date; expose oil/fuel actions on each card.
- Add confirmed permanent vehicle deletion that transactionally removes linked maintenance/fuel/odometer/override/receipt records and then cleans receipt files, preserving shared catalog rows; retain archive/restore as the reversible alternative.
- Implement Room v1 persistence, multi-vehicle CRUD, KM/MILE selection, soft archive/restore, and database persistence tests.
- Add dated odometer history with duplicate/future/negative checks and explicit review for lower/out-of-order readings.
- Implement oil-change create/edit/history, actual oil amount, service/provider/cost fields, service snapshots, per-service interval overrides, and dual mileage/time scheduling.
- Add manual vehicle-specific recommendation overrides for oil viscosity, manufacturer approval, capacity, filter, intervals, and source notes. Values remain explicitly unverified.
- Add a 476-row catalog from the supplied `global_road_vehicle_oil_and_filter_database_45years.csv`; normalize explicit normal/severe oil intervals, take the shorter endpoint for simple ranges, and keep conditional/conflicting values raw. All imported data remains AI-generated/unverified; shared intervals affect schedules only after user verification.
- Change vehicle selection to ordered Year → Manufacturer → Model → Engine/configuration dropdowns, each with an Other/manual path; catalog editing remains secondary. Users can record a source name/URL/date to mark values user-verified.
- Implement the unified vehicle-home status cards and per-vehicle service estimates.
- Add optional daily WorkManager oil reminders for due-soon/due/overdue vehicles, notification channel and Android 13+ permission flow; reminders are enabled from Settings and deduplicated until the schedule changes.
- Add fuel fill-up recording for full and partial tanks, volume/currency conversion, transactional odometer records, and latest complete full-tank economy output in four units.
- Add app-private PDF/JPEG/PNG receipt storage, validation, checksum metadata, preview/download/remove, and service linkage.
- Implement versioned ZIP backup/restore with JSON database export, manifest, checksums, explicit replace confirmation, staged receipts, and transactional rollback.
- Document install/build, database, calculations, backup/restore, and remaining limits.
- Added persistent Metric/Imperial preference for the default unit of new vehicles; v0.2.0 makes each car's unit authoritative for catalog interval display.
- Verified full offline Gradle test/build: 110 tests across 18 suites passed (0 failures/errors/skips); debug APK generated (11,878,199 bytes) with the catalog asset and notification permission. No device/emulator notification or UI test was available.
- Production release signing, OEM data sourcing, configurable reminder thresholds, scheduled/cloud backups, merge restore, and on-device notification/TalkBack testing remain outstanding.
