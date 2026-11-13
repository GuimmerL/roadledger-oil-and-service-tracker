# Environment inspection (2026-10-12 EDT)

## Verified findings
- Shell/container OS: Debian GNU/Linux 13 (trixie); kernel reports Linux 6.18.38-Unraid. `/opt/data` is Unraid shfs persistent storage (954 GiB visible, 781 GiB free at inspection). This identifies the shared filesystem, not the full physical server mount layout.
- CPU: 4 logical CPUs visible. RAM: 19 GiB visible; approximately 8.9 GiB available at inspection; swap disabled. No GPU utility/device is exposed. Local LLM runtime/model weights are not present.
- Docker CLI 26.1.5 exists, but connection to `/var/run/docker.sock` fails; Compose CLI plugin is missing. Cannot inspect host containers, Docker volumes, host bind mounts, Unraid appdata, or Docker permissions from this shell. Do not assume `/opt/data` project files belong in an Unraid appdata runtime path.
- `/opt/data/android-sdk` exists and is writable in this container; platforms 30, 34, 36, extension 19; build-tools 30.0.3, 34.0.0, 35.0.0, 36.0.0; platform-tools package exists but `adb` is not on PATH and no emulator is installed.
- JDK 17.0.13 is at `/opt/data/jdk/jdk-17.0.13+11`, not PATH. Gradle 8.14.2 distribution/cache exists under `/opt/data/.gradle`; no standalone system Gradle and no official project wrapper jar.
- Existing Android source projects are `/opt/data/DocVault_Project/source` and Cordova projects `/opt/data/PdfViewerApp`, `/opt/data/PilzPlaybookWork/PdfViewerApp`; they are unrelated and have not been altered. A separate git repository has been created at `/opt/data/vehicle-tracker`.
- Git executable exists, but no global Git author identity was found. No Git commit has been created yet (configure an identity before committing).
- Current project Gradle configuration tested with `--offline` failed during plugin resolution because required artifacts/marker/KSP/Compose/Room packages are not cached. Kotlin code was not compiled and tests were not executed.

## LLM budget / provider findings
- `hermes` CLI is not on PATH in the inspected shell, so available account/model/usage commands cannot be run. Config in `/home/hermes/.hermes/config.yaml` selects Nous `openai/gpt-6-luna`; `auth.json` lists Nous and other provider credential records. Secrets were not read or printed.
- Config contains free-looking model identifiers, but no account entitlements, inference pricing, limits or credit balance are exposed. No provider switch or billing action was taken. Treat all alternative inference as unverified; do not send private car data to it.
- Owner-reported expected renewal: Oct. 21, 2026. Current date is Oct. 12, 2026. Renewal and ~$1 reported credit remain unverified through tools.

## Proposed path
Use native single-module Kotlin/Compose/Room project at `/opt/data/vehicle-tracker`, pure Kotlin calculation package, offline-first data layer. Source project and local app data remain outside containers. Optional Docker image under `docker/` is for deterministic Android builds only; it is not a server-hosted application. Persistent Gradle cache and APK output mounts are explicit in Compose. Android app-private database/attachments are on-device and require an independent tested backup before storing critical records.

## Blockers and next actions
1. Owner/admin provides authorized dependency/network access and SDK license acceptance; then generate official Gradle wrapper, resolve dependencies, compile, and run tests once.
2. If Docker build is desired, enable appropriate Docker socket/Compose in the intended host or run from an authorized build machine; verify bind-mount paths before using them.
3. Verify subscription usage/renewal and explicitly authorize any free provider configuration before using alternatives; otherwise continue deterministic local work.
4. Implement Room/repository, vehicle form, schema export/migrations, and database tests before adding records/attachments/backup.
