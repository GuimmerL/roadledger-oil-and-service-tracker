# Backup and restore

Backup and restore are implemented in the Android app under **Backups**. The feature uses Android's Storage Access Framework, so the user selects where to create the ZIP and which ZIP to restore. The app does not silently upload or synchronize records.

## Archive format (v1)

The ZIP contains:

- `manifest.json`: archive version, Room schema version, app version, UTC creation time, per-table row counts, receipt count, and SHA-256/size entries for every payload file.
- `database.json`: machine-readable SQLite table/column/row export for all app tables, captured inside a SQLite transaction.
- `attachments/receipts/<internal-name>`: original receipt bytes. Internal filenames are UUID-based; original names remain in the database metadata.

Room schema v3 is current. Backups from Room schema v1 are upgraded during validation by adding a null starting-daily-usage value and a `NORMAL` interval mode to each vehicle. Schema-v2 backups are upgraded by adding a `NORMAL` interval mode. Stable IDs, readings, and receipts are preserved. Other unsupported versions, missing/extra entries, unsafe paths, duplicate ZIP entries, malformed rows, duplicate primary keys, broken references, mismatched counts, missing receipt files, and size/checksum mismatches are rejected before the live database is touched. The implementation caps a ZIP at 200 MiB, a JSON database export at 50 MiB, and each receipt at 10 MiB.

## Create a backup

1. Open **Backups** and tap **Create backup**.
2. Choose a destination in Android's file picker. Prefer a different physical device, USB drive, or trusted external storage.
3. Keep the generated `.zip` file. The app records the last successful backup time and most recent error in local settings.

The database snapshot is read in a SQLite transaction. The app checks every receipt's saved size and SHA-256 before including it. A missing or changed receipt causes backup creation to fail rather than producing a knowingly incomplete successful archive.

## Restore a backup

1. Open **Backups** and tap **Choose backup to restore**.
2. The app fully validates the archive and displays the creation time and record counts.
3. Review the replace warning. Tapping **Replace data** is explicit confirmation that the selected archive will replace all vehicles, settings, odometer entries, service records, and receipts on this installation.

Receipt files are staged under new private UUID names. The live database is replaced in one SQLite transaction; a failed transaction rolls back and the staged files are removed. Existing receipt files are removed only after the database commit succeeds. Re-importing the same archive uses stable record IDs and replace semantics, so it does not append duplicate records. **Merge is not implemented.**

Room schema-v1 archives are upgraded to the current v2 schema by setting each vehicle's new starting-usage field to null. Other unknown older/newer schemas remain rejected; no data conversion is guessed. Export a current backup before app updates.

## Receipt storage and privacy

Receipt originals are stored in app-private persistent storage at `files/receipts/`; metadata (vehicle/service relationship, original filename, MIME type, byte size, SHA-256, and upload time) is in Room. Supported content is PDF, JPEG, and PNG. Files are validated by content signature, limited to 10 MiB, and accessed through a non-exported Android `FileProvider` for preview. Removing an attachment removes only that receipt, not its service record.

Exports are **not encrypted**. Treat ZIPs as sensitive: they may contain VINs, license plates, invoices, and personal information. Keep an additional copy somewhere other than the phone's physical storage. An export on the same device or disk does not protect against its loss or failure.

## Limits

- Backups are user-initiated; automatic scheduling and cloud/Drive integration are not implemented.
- Restore replaces the current dataset; merge is not available.
- A debug build is not a signed production release.
- When the schema changes, add a Room migration and backup compatibility handling before changing the schema version.
