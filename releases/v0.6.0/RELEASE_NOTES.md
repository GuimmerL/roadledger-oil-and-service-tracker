## RoadLedger v0.6.0

- Catalog data revision 2.1.0: 1,872 corrected cells, 940 malformed engine fragments merged, and 7 duplicates plus 14 electric drive-unit rows removed (15,952 → 15,149 rows). The seeder refreshes untouched AI rows on a catalog version change without overwriting user edits.
- Localization-ready: user-visible strings moved into `strings.xml` with placeholders.
- Edge-to-edge system bars, predictive back, `adjustResize`, bottom navigation on compact screens and a navigation rail on expanded widths, and capped content width.
- Material date pickers, merged garage-card semantics, a notification icon with a tap action, and hoisted Compose flows.
- Backup/restore streams the archive to and from a temp file instead of loading attachments into memory; receipt-to-vehicle links are validated.
- Receipt picker and validator now agree on PDF/image formats (JPEG, PNG, WebP, GIF, HEIC/HEIF, AVIF).
- Removed the dead Dashboard screen/ViewModel/test and the unused `navigation-compose` dependency.

### Verification and build

- 145 JUnit tests passed across 24 suites (0 failures, 0 errors, 0 skipped).
- The Android APK is debug-signed and is not production-signed.
- Source tag `v0.6.0` is on branch `feat/vehicle-icon-lookup`.
