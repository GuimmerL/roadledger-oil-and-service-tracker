# Versioned release exports

Each application version is stored in `releases/v<versionName>/`. The version name and code are read from `app/build.gradle.kts`.

A release folder contains:

- `RoadLedger_v<version>_debug.apk` — installable debug-signed build; not production-signed.
- `RoadLedger_v<version>_Source_Code.zip` — clean Gradle source project, including tests and the wrapper, excluding generated build caches and local/private data.
- `release-manifest.json` — package/version metadata, artifact sizes, and SHA-256 hashes.
- `SHA256SUMS.txt` — checksums for both exported artifacts.

After a successful build and test run, regenerate the package from the project root:

```bash
python3 scripts/package_release.py
```

Increment Android `versionName` and `versionCode` before creating a distinct release. Re-running the script for the same version replaces that version's package files.
