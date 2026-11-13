# Room data schema

The Kotlin entities in `app/src/main/java/ca/gllavoie/vehicletracker/data/local/Entities.kt` are the schema source of truth. `VehicleDatabase` is at schema version 5; the generated Room schema is `app/schemas/ca.gllavoie.vehicletracker.data.local.VehicleDatabase/5.json`. `VehicleDatabaseFactory` registers non-destructive v1→v2, v2→v3, v3→v4 and v4→v5 migrations and uses `vehicle_tracker.db` in app-private storage; there is no destructive fallback.

| Entity/table | Purpose | Integrity / relationships |
|---|---|---|
| `vehicles` | Vehicle identification, engine/fuel, VIN/plate, odometer unit, optional starting daily-use estimate (stored canonically as km/day), per-vehicle normal/severe interval mode, notes and archive timestamp | VIN and archive indices; a vehicle's specification FK is restricted. Existing vehicles migrate to `NORMAL`. Archive is reversible; confirmed permanent removal deletes dependent records/receipt files and preserves shared catalog rows. |
| `vehicle_specifications` | Reusable year/make/model/engine specification fields, oil grades/approvals/capacities/normal and severe intervals, and verification state | The active v2.0.0 production catalog (data revision 2.1.0) has 15,149 exact-year/engine rows: 15,083 `AI_GENERATED_UNVERIFIED` and 66 `OFFICIAL_INTERVAL_RESEARCHED`. Legacy catalog rows remain so existing vehicle foreign-key links are preserved. Neither imported status drives schedules until the user marks the selected interval `USER_VERIFIED`. Other fields may remain unverified. Structured catalog distances are stored in KM; the original CSV row and source-unit fields are retained in notes/source metadata. |
| `specification_sources` | Source name/URL/date/status/notes for a reusable specification | Restricted FK to specification. |
| `vehicle_specification_overrides` | One manual override row per vehicle for viscosity, approval, capacity, interval, filter and source note | Unique vehicle FK; independent of reusable specifications. The UI labels all such values unverified and does not validate source authenticity. |
| `odometer_readings` | Dated odometer value/unit, origin/source-record ID and notes | Vehicle FK and vehicle/date index. Manual, oil-change and fuel entries retain their source. |
| `oil_changes` | Service date/mileage, oil brand/product/grade/approval/actual quantity, filter, provider/type, cost/currency, interval overrides | Vehicle FK and vehicle/date index. Oil amount uses ml; money uses minor units. Each service stores its own values and links to a dated odometer event. |
| `fuel_fill_ups` | Fill date/mileage/unit, purchased volume in ml, cost/currency, fuel type, explicit full-tank flag, station and notes | Vehicle FK and vehicle/date index. Its odometer event is inserted in the same Room transaction. |
| `receipt_attachments` | Vehicle/service relationship, document category (`RECEIPT` or a vehicle document type), internal relative path, original filename, MIME, size, SHA-256, uploaded time and notes | Restricted vehicle/oil/fuel FKs. Repository validation enforces exactly one service relationship for receipts and no service link for vehicle documents; originals live in private filesystem storage. Removing a receipt does not remove its service. |
| `app_settings` | Local key/value settings, including last backup/restore/error status and the catalog seed version | No secrets or OAuth tokens. Included in the backup export. |

Records use stable IDs and timestamps as applicable. Service dates are ISO local calendar dates; timestamps for instants are UTC milliseconds. Do not cascade-delete maintenance history. Do not add an expenses table for amounts already recorded on fuel/oil rows; sum those transaction values for reports to avoid double counting.

## Migration policy
- Increment schema version for every table/column/index/constraint change and add a non-destructive Room `Migration` (v1→v2→v3→v4→v5).
- Never use `fallbackToDestructiveMigration` for owner data.
- Preserve IDs, source-record links and receipt metadata.
- Test every migration from the previous schema with representative active/archived vehicles and linked services/attachments.
- Backup archive version and Room schema version are distinct. Restore upgrades older backups with the missing-column defaults (null starting-use, `NORMAL` interval mode, `OTHER` vehicle type, `RECEIPT` document category); unsupported schemas are rejected before modifying live data.

## Persistence tests
Robolectric/unit tests cover persistence across database reopen, v1→v2→v3→v4→v5 migrations, daily-usage validation and recalculation, per-vehicle interval-mode persistence, vehicle CRUD/archive/restore/permanent deletion with linked records and receipt cleanup, service snapshots, odometer anomaly handling, atomic service/fill-up plus odometer inserts, the 15,149-row production catalog import and status/source preservation, catalog re-seed on version change without overwriting user edits, catalog selection warnings, normal/severe calculations, reminder eligibility, receipt storage, and legacy backup upgrades. Current Room schema: v5.
