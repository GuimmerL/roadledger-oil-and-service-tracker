#!/usr/bin/env python3
"""Package a versioned debug APK and clean source archive under releases/."""
from __future__ import annotations

import hashlib
import json
import os
import re
import shutil
import tempfile
import zipfile
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GRADLE_FILE = ROOT / "app" / "build.gradle.kts"
APK_FILE = ROOT / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
RELEASE_ROOT = ROOT / "releases"

EXCLUDED_DIRS = {
    ".git", ".gradle", ".idea", ".kotlin", "build", "releases",
    "__pycache__", ".venv", "venv", "node_modules", "secrets", "local-data", "runtime",
}
EXCLUDED_ROOT_DIRS = {"data"}
EXCLUDED_FILES = {
    "local.properties", "keystore.properties", ".DS_Store",
}
EXCLUDED_SUFFIXES = {
    ".apk", ".aab", ".db", ".db-shm", ".db-wal", ".zip", ".log", ".pyc",
    ".jks", ".keystore",
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def app_version() -> tuple[str, int]:
    text = GRADLE_FILE.read_text(encoding="utf-8")
    name_match = re.search(r'versionName\s*=\s*"([A-Za-z0-9._+-]+)"', text)
    code_match = re.search(r"versionCode\s*=\s*(\d+)", text)
    if not name_match or not code_match:
        raise RuntimeError(f"Could not read app version from {GRADLE_FILE}")
    return name_match.group(1), int(code_match.group(1))


def is_source_file(path: Path) -> bool:
    relative = path.relative_to(ROOT)
    if any(part in EXCLUDED_DIRS for part in relative.parts):
        return False
    if path.name in EXCLUDED_FILES or path.suffix.lower() in EXCLUDED_SUFFIXES:
        return False
    if path.name.startswith(".env"):
        return False
    if path.is_symlink() or not path.is_file():
        return False
    return True


def write_source_zip(destination: Path, version: str) -> None:
    root_name = f"RoadLedger_v{version}_Source_Code"
    destination.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(destination, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for current, directories, filenames in os.walk(ROOT):
            at_project_root = Path(current) == ROOT
            directories[:] = sorted(
                name for name in directories
                if name not in EXCLUDED_DIRS and not (at_project_root and name in EXCLUDED_ROOT_DIRS)
            )
            for filename in sorted(filenames):
                path = Path(current) / filename
                if is_source_file(path):
                    archive.write(path, (Path(root_name) / path.relative_to(ROOT)).as_posix())


def main() -> None:
    if not APK_FILE.is_file():
        raise SystemExit(f"Debug APK not found: {APK_FILE}. Build :app:assembleDebug first.")
    if not zipfile.is_zipfile(APK_FILE):
        raise SystemExit(f"APK is not a valid ZIP-format APK: {APK_FILE}")

    version, version_code = app_version()
    safe_version = re.sub(r"[^A-Za-z0-9._+-]", "_", version)
    release_dir = RELEASE_ROOT / f"v{safe_version}"
    release_dir.mkdir(parents=True, exist_ok=True)
    apk_name = f"RoadLedger_v{safe_version}_debug.apk"
    source_name = f"RoadLedger_v{safe_version}_Source_Code.zip"
    apk_path = release_dir / apk_name
    source_path = release_dir / source_name

    with tempfile.TemporaryDirectory(prefix=".package-tmp-", dir=release_dir) as temp_name:
        temp_dir = Path(temp_name)
        temp_apk = temp_dir / apk_name
        temp_source = temp_dir / source_name
        shutil.copy2(APK_FILE, temp_apk)
        write_source_zip(temp_source, safe_version)
        os.replace(temp_apk, apk_path)
        os.replace(temp_source, source_path)

    manifest = {
        "applicationId": "ca.gllavoie.vehicletracker",
        "versionName": version,
        "versionCode": version_code,
        "buildType": "debug",
        "signing": "debug key; not production-signed",
        "createdUtc": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "artifacts": {
            apk_name: {"bytes": apk_path.stat().st_size, "sha256": sha256(apk_path)},
            source_name: {"bytes": source_path.stat().st_size, "sha256": sha256(source_path)},
        },
    }
    (release_dir / "release-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    (release_dir / "SHA256SUMS.txt").write_text(
        f"{manifest['artifacts'][apk_name]['sha256']}  {apk_name}\n"
        f"{manifest['artifacts'][source_name]['sha256']}  {source_name}\n",
        encoding="utf-8",
    )
    print(f"Release: {release_dir}")
    print(f"APK: {apk_path} ({apk_path.stat().st_size} bytes)")
    print(f"Source: {source_path} ({source_path.stat().st_size} bytes)")
    print(f"Manifest: {release_dir / 'release-manifest.json'}")
    print(f"Checksums: {release_dir / 'SHA256SUMS.txt'}")


if __name__ == "__main__":
    main()
