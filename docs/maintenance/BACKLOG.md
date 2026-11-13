# Prioritized handoff backlog

## Implemented and verified in code
1. Android foundation: Room v5 database with tested non-destructive v1→v2→v3→v4→v5 migrations, vehicle create/edit/archive/restore, confirmed permanent deletion with associated record/receipt cleanup, and core calculation modules.
2. Garage interaction fixes: duplicate-save guard, dialog-local save/archival errors, cancellation handling, responsive editor, unsaved-change confirmation, accessible per-vehicle actions.
3. Dated odometer history: entry/list UI, KM/MILE choice, an Update current mileage card action, optional per-car starting daily-use baseline with an observed average that recalculates from dated readings, usage-based due-date projection, duplicate/future/negative validation, review confirmation for anomalies, and tests.
4. Oil service history and scheduling: add/edit, actual quantity, service details/cost, per-service interval overrides, atomic service+odometer write, visible dual-deadline schedule and assumptions.
5. Catalog: import the 15,149-row v2.0.0 production CSV (revision 2.1.0; 15,083 AI-generated unverified + 66 source-researched), preserve source fields, show a warning on AI-unverified selections, support Normal/Severe interval modes, and keep imported intervals excluded from schedules until user verification.
6. Persistent Metric/Imperial setting. It defaults new vehicles to the selected system; each vehicle's own KM/MILE unit controls catalog interval display and schedule calculations. Existing readings retain stored units.
7. Compact garage vehicle cards with a prominent next-oil distance and a detail subpage for full vehicle data, normal/severe interval mode, service history, and oil/fuel actions.
8. Receipts: private PDF/image storage (PDF, JPEG, PNG, WebP, GIF, HEIC, HEIF, AVIF), generated filenames, content-type/size/signature checks, metadata and hashes, preview/download/remove, service association.
9. Local manual ZIP backup/restore: consistent transactional SQLite JSON snapshot, manifest/counts/checksums, attachments, preflight validation, explicit replace confirmation, staged receipt restore, transactional rollback, duplicate replace idempotence, and tests.
10. Fuel fill-ups and economy: volume-unit conversion, full/partial tank records, odometer/source records, review confirmation, and latest full-tank interval calculations with four display units.
11. Optional daily WorkManager oil-change notifications for due-soon/due/overdue schedules, user opt-in setting, notification channel, and Android 13+ permission request.

## Remaining work / limitations
- The 15,083 AI-generated catalog rows are not OEM-verified. The 66 source-researched suggestions require user confirmation and may not verify engine/trim or other fields. Manufacturer oil-life-monitor behavior is not modeled. User-entered verification claims are not independently checked, and conflicting manufacturer sources are not automatically detected.
- Configurable reminder thresholds, expenses reporting, fuel edit/delete and charts, vehicle photos, and receipt notes/OCR remain incomplete. Oil notifications require user opt-in and Android permission; the due-soon thresholds are fixed.
- Backups are manual, local ZIP files, unencrypted, and replace-only. There is no merge, scheduled backup or Google Drive integration. Users should keep a copy on a different physical device.
- Current Room schema is v5; v1→v2 adds the optional per-vehicle daily-use baseline, v2→v3 adds the Normal/Severe selection, v4 adds vehicle type, and v5 adds document categories. Legacy backups are upgraded during restore; unsupported versions are rejected. Future schema changes still require a non-destructive migration and tests.
- No emulator was available, so responsive device rendering and TalkBack have not been tested.
- Debug APK is not a production-signed release.

## Verification gates
- Latest full run: offline `:app:testDebugUnitTest assembleDebug` succeeded; 145 JUnit tests passed (0 failures/errors/skips) across 24 suites. Version 0.5.3 debug APK (12,637,020 bytes) contains the 15,149-row production catalog. No emulator/device UI or notification test was available.
- Test totals are parsed from `app/build/test-results/testDebugUnitTest/TEST-*.xml` after the build; rerun after source changes.
- For any schema-version change, add and test a non-destructive Room migration and update backup compatibility handling.
- The suite includes database persistence across reopen and full backup → clean restore → receipt checksum verification.
