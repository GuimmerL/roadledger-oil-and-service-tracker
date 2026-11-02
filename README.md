# RoadLedger - Oil and service tracker

Local-first Android app for a personal garage, oil-change schedules, fuel economy, service receipts, vehicle documents, and user-controlled backup/restore. Built with Kotlin, Jetpack Compose, Room/SQLite, and Android's Storage Access Framework. It does not require a server, cloud account, paid API, or network connection after installation.

## Implemented

- The garage home uses compact, tappable vehicle cards focused on oil-change status, remaining distance, interval, and estimated date. Selecting a card opens a vehicle detail page with odometer and daily-use data, service history, vehicle specifications, and oil/fuel actions. The toolbar and Android Back return to the garage list.
- Vehicles support nickname, year/make/model/trim, engine details, VIN/plate, notes, KM/MILE units, archive/restore, and confirmed permanent deletion. Edit, archive, restore, and permanent delete actions are grouped in each vehicle card’s three-dot menu. Permanent deletion removes that vehicle's oil/fuel/odometer records and receipt files while preserving shared catalog entries.
- Vehicle icons use distinct body-style silhouettes (flat-backed hatchback, extended wagon, taller SUV, and roofless convertible). Garage cards can be sorted by name, model year, or recently added; the selected order persists.
- A bundled 15,952-row catalog from `vehicle_catalog_v2.0.0_production.csv`: 15,886 AI-generated unverified rows and 66 source-researched interval suggestions. The catalog preserves market/engine/source data and both normal and severe-use intervals; structured distances are stored in km. Imported intervals remain out of schedule calculations until user verification. Selecting an AI-generated row triggers a warning to check the exact vehicle and manual. Unlisted vehicles remain addable manually.
- Each vehicle can have a starting daily-use estimate (km/day or mi/day). The current observed average recalculates as dated odometer readings are added and overrides that starting estimate once enough increasing readings exist. The **Update current mileage** button opens the dated odometer form, which defaults to today.
- Oil-change create/edit/history, actual quantity added, oil viscosity/specification as entered, filter, service provider/type, currency/cost, and per-service interval overrides.
- Manual vehicle-specific oil viscosity, approval, capacity, filter, and interval values with source notes. These are explicitly marked **unverified**; an unknown remains unknown and is never invented.
- Transparent next-oil-change calculations using both mileage and time deadlines, the earlier limit, the odometer usage estimate when enough readings exist, and visible assumptions/status. Choose Normal or Severe/heavy-use intervals per vehicle; latest-service and vehicle-specific overrides remain higher priority. An unknown interval stays unavailable; no vehicle spec is invented.
- Settings provides Metric (km) and Imperial (mi) as the default for new vehicles. Catalog recommendations display in each car's configured odometer unit (including conversion of structured intervals); historical odometer/service entries retain their stored units. Ambiguous conditional source text is not converted.
- Optional daily WorkManager reminders post **“Change oil soon for [vehicle]”** when a saved schedule is due soon, due, or overdue. Enable them in Settings and grant Android notification permission; reminders use the existing 1,000-distance-unit/30-day due-soon thresholds.
- Fuel purchase history with full/partial tank entries, litres/US gallons/imperial gallons input, odometer readings, cost and station; latest complete full-tank economy interval can be shown as L/100 km, km/L, US mpg, or imperial mpg.
- Service receipts support PDF/JPEG/PNG files linked to a service record, with preview/download/removal and checksum verification. Vehicle-level documents cover insurance, registration, warranties, and service records: take a full-resolution photo, pick common image formats, or select a PDF. Files are kept in private app storage, categorized, size/type/hash-validated, and included in user-controlled backup/restore.
- Manual versioned ZIP backup with machine-readable database JSON, receipt and vehicle-document bytes, checksums, record counts, validated replace-only restore, explicit confirmation, transactional database replacement, and rollback on failed commits. Backup/restore and Settings are in the top-left menu.

## Current limits

- The 15,886 AI-generated catalog rows are unverified. The 66 source-researched rows provide interval suggestions only; engine/trim and other fields may remain unverified. Users must confirm the selected values against the correct owner's manual before schedule use. The app cannot reproduce manufacturer oil-life monitors; conflicting OEM sources are not automatically detected.
- Automatic backup scheduling, cloud/Drive backup, encryption, merge restore, vehicle photos, receipt OCR, and configurable reminder thresholds are not implemented.
- Oil reminders are daily and require the user to enable them in Settings and grant Android notification permission. Due-soon thresholds are fixed defaults.
- Room schema v5 migrates v1-v4 databases non-destructively, including document categories; existing attachments default to service receipts and existing vehicles default to Normal interval mode. Supported legacy backups are upgraded during restore; unsupported versions are rejected.
- No emulator/device was connected. Notification permission/delivery, responsive layout, and TalkBack were not exercised; the successful build is not an on-device test.
- The APK is debug-signed, not a production release.

## Licensing

Original application code and project documentation are licensed under Apache-2.0; see [LICENSE](LICENSE). The bundled vehicle catalogs and exports are **not covered by that license** because their redistribution rights have not yet been established. See [DATA-LICENSE.md](DATA-LICENSE.md). Third-party libraries and assets retain their own applicable licenses.

## Using the vehicle catalog

On the garage page, tap **Add vehicle**; selecting an AI-generated catalog row displays a warning. Choose Year, Manufacturer, Model, then Engine/configuration and Trim from the ordered dropdowns. Each includes **Other — enter manually**. The vehicle form seeds the v2.0.0 production catalog once without overwriting user edits. On a vehicle detail page, select **Normal** or **Severe / heavy use**; per-service and vehicle-specific intervals still take priority. Use **Modify catalog data** to review/edit normal and severe intervals. Imported rows remain excluded from schedules until the user checks the cited documentation and marks the values verified. See [catalog review and sources](docs/maintenance/catalog-review.md).

## Build and test

From the project directory, configure a JDK 17 and Android SDK for your machine (set `JAVA_HOME`, `ANDROID_HOME`, and `ANDROID_SDK_ROOT` as appropriate), then run:

```bash
./gradlew testDebugUnitTest assembleDebug
```

Last verified: `./gradlew testDebugUnitTest assembleDebug` passed **146 JUnit tests** across 25 suites (0 failures/errors/skips). It produced `app/build/outputs/apk/debug/app-debug.apk` (12,769,221 bytes, debug-signed, version 0.5.3, versionCode 8). The APK package/version and launcher resources were inspected; no emulator/device was connected, so UI rendering, notification delivery, and TalkBack remain untested on-device.

## Versioned release exports

The APK and clean source-code ZIP are packaged under `releases/v<versionName>/` with a manifest and SHA-256 sums, following the versioned artifact layout used for the Distributor Playbook. After a successful build, run `python3 scripts/package_release.py`; see [release export notes](docs/release-exports.md). The APK is debug-signed, not a production release.

## Local data

- Room database: Android app-private `databases/vehicle_tracker.db`.
- Service receipts and vehicle documents: Android app-private `files/receipts/`, with UUID-based internal filenames.
- Neither database nor attachment files are exposed through a server or public directory. Android uninstall/device loss removes app-private data; create and copy a backup elsewhere.
- Manual ZIP backups are saved to the user-selected Storage Access Framework destination; see [backup and restore](docs/backup-restore/README.md).

## Project notes

- [Architecture](docs/architecture/architecture.md)
- [Database schema](docs/architecture/schema.md)
- [Calculation rules](docs/architecture/calculations.md)
- [Backup and restore](docs/backup-restore/README.md)
- [Vehicle catalog review and OEM sources](docs/maintenance/catalog-review.md)
- [Prioritized backlog](docs/maintenance/BACKLOG.md)

Vehicle specification data must never be guessed or marked verified without a trustworthy source. Keep credentials, VINs, receipts, backups, and other personal data out of Git.
