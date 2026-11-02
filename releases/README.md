# Versioned release exports

Each application version is stored in its own `v<versionName>/` directory. `versionName` and `versionCode` come from `app/build.gradle.kts`.

A release directory contains:

- `RoadLedger_v<version>_debug.apk` — installable debug-signed build; not production-signed.
- `RoadLedger_v<version>_Source_Code.zip` — clean Gradle source project, including tests and the wrapper, excluding generated build caches and local/private data.
- `release-manifest.json` — package/version metadata, artifact sizes and SHA-256 hashes.
- `SHA256SUMS.txt` — checksums for verifying both exported artifacts.

After building and testing, regenerate the package from the project root:

```bash
python3 scripts/package_release.py
```

Increment the Android `versionName` and `versionCode` before making a distinct release. Re-running the script for the same version replaces that version's package files.
