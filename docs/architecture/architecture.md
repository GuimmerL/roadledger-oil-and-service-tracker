# Architecture and decisions

## Context
Single-user, offline-first native Android application. Room/SQLite is the source of truth. A backup is a user-controlled copy, not sync. The active catalog is imported from `vehicle_catalog_v2.0.0_production.csv` and contains 15,952 year/engine rows: 15,886 `AI_GENERATED_UNVERIFIED` and 66 `OFFICIAL_INTERVAL_RESEARCHED`. Neither status means user verification; rows stay excluded from schedules until the user confirms the interval. Do not treat AI source ratings as evidence or imply unrelated fields are manufacturer-verified.

## Stack and packages
- Kotlin, Jetpack Compose, AndroidX lifecycle, Room 2.7, KSP and SQLite.
- `domain/calculation`: Android-free pure Kotlin maintenance scheduling and fuel economy.
- `data/local`: Room schema v3, v1→v2→v3 migrations, indices, foreign keys, DAO transaction boundaries and database factory.
- `data/repository`: vehicle, odometer, oil, fuel, catalog interval normalization, reminder schedule calculation and receipt operations.
- `data/backup`: versioned ZIP export/validation/restore over the live Room database; archive data is machine-readable JSON plus original files.
- `ui/garage`, `ui/fuel`, `ui/backup`: Compose workflows and ViewModels.

The app is a single Android module. Repositories expose Flows for observations and suspend writes. Persist stable IDs and explicit distance/volume/currency units. Use database transactions for records that need an associated odometer entry. Keep computation code independent of Android UI.

## Implemented screens and flows
- Unified vehicle home: each vehicle card foregrounds oil-change status, last odometer/service, recommended interval and estimated due date; expanding a card shows fuel/oil specifications and exposes fuel/oil actions. A visible Update current mileage action opens the dated odometer form. Edit, archive, restore, and permanent deletion are grouped in a per-card three-dot menu.
- Permanent vehicle deletion: explicit confirmation, atomic removal of vehicle-linked oil/fuel/odometer/override/receipt metadata, then cleanup of receipt files. The reusable vehicle catalog is preserved; archive/restore remains the reversible option.
- Vehicle catalog: idempotently inserts the supplied 476 AI rows plus 42 exact-year OEM interval suggestions whenever the vehicle form opens, without overwriting user edits. Year → Manufacturer → Model → Engine/configuration → Trim dropdowns include an Other/manual route. Year ranges expand into selectable years; safe engine lists and known trim badges split into choices, while ambiguous intervals stay grouped. Structured catalog distances are stored as KM (mile values round upward to a whole km) and US-quart capacities as L; original wording is retained and distances convert for the selected vehicle's unit. Source-researched suggestions remain excluded from schedules until the user confirms the interval; other AI rows remain unverified. The caution is shown only while adding a vehicle.
- Measurement system: Settings selects Metric (km) or Imperial (mi) as the default for new vehicles. Structured catalog intervals display in each car's configured odometer unit and convert for schedule calculations; existing readings keep their stored units and unstructured conditional interval text stays unchanged.
- Daily-use estimate: each vehicle can store an optional starting distance-per-day estimate. The card and schedule use it until at least two increasing dated readings allow an observed average; each new odometer reading recalculates that average. The baseline is stored as km/day and shown in the vehicle's selected unit.
- Odometer history: dated entries; the card's Update current mileage action opens today's prefilled reading form. Two or more increasing readings on different dates are used to estimate usage for projected due dates; negative/future/duplicate values are rejected and lower/out-of-order readings require confirmation.
- Oil changes: add/edit historical records and actual oil amounts; each service's odometer snapshot is atomically represented in odometer history. Service interval overrides stay attached to the record. The schedule uses mileage and time deadlines independently, selects the earlier one, and shows assumptions/status.
- Fuel: card-level tracking dialog, full/partial-tank distinction, unit/currency conversion and latest full-tank interval display using the pure calculator.
- Oil reminders: opt-in daily WorkManager check posts “Change oil soon for [vehicle]” for due-soon/due/overdue schedules. Android 13+ notification permission is requested in Settings; notifications use a channel and schedule fingerprint to avoid daily duplicates.
- Receipts: app-private original files, safe internal UUID names, metadata, checksums, PDF/JPEG/PNG validation, preview/download and independent removal.
- Backup/restore and Settings are reached from the hamburger menu. Backups use user-selected ZIP export, transactionally consistent SQLite snapshots, manifest and per-payload SHA-256, complete receipt payloads, preflight validation, explicit replace-only restore and transaction rollback.

## Data and units
- Odometer distance: KM or MILE per vehicle. Service/fuel entries carry an explicit unit snapshot.
- Fuel volume is stored in millilitres. Cost is integer minor units with ISO currency code. Oil capacity (specification) is distinct from the actual oil amount used (service record).
- Service dates and user-entered odometer dates are local calendar dates persisted as ISO/UTC instants as appropriate.
- Historical service details are separate from the current vehicle specification state; changing an interval must not rewrite earlier service details.
- Interval overrides entered on a service are user-entered and are not labeled as verified manufacturer recommendations.

## Persistence and integrity
Archive remains reversible; permanent deletion is an explicit confirmed action that transactionally removes the vehicle's linked records and receipt metadata, then removes private receipt files. Foreign-key deletes are restricted elsewhere. Attachments point to one oil/fuel record and can also be removed independently. Original files live under app-private `files/receipts/`; a non-exported FileProvider exposes read-only URIs for preview. Room is configured without destructive migration fallback.

## Backup/restore
See [backup and restore](../backup-restore/README.md). Archive v1 carries the Room schema version and is rejected by incompatible schemas. Replace is the only restore mode. The archive is parsed and all rows, IDs, relationships, entries, counts, file sizes and SHA-256 checks are validated before touching the live database. Receipt files are staged under new generated names and database replacement runs in a single SQLite transaction; failed writes roll back. A same-device copy is not off-device disaster protection. Exports are not encrypted.

## Explicit limits
- The catalog contains AI-generated rows and source-researched interval suggestions. Imported intervals stay out of schedules until user verification; the app does not audit the user's verification claims or automatically detect conflicting sources.
- Reminder thresholds are fixed at 1,000 miles/kilometres or 30 days. Notifications are opt-in and require Android permission. Scheduled/cloud backups, merge import, encryption, photos and OCR are not implemented.
- Room v1→v2→v3 migrations are implemented and tested. Future schema changes still require a non-destructive migration and tests.
- Production signing remains unfinished. The available environment had no emulator/device, so on-device layout/accessibility and notification delivery remain unverified.
- Docker is unavailable and is not needed to run the Android app; no server is required.
