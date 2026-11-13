#!/usr/bin/env python3
"""Apply v2.0.1 corrections to the bundled RoadLedger vehicle catalog.

Every correction is derived from the row's own text (``engine_trim``,
``oil_recommendation`` or ``original_catalog_id``); nothing is invented.

Corrections:
  1. Normal time interval: 102 rows stored 5 months where the source says "7.5 mo".
  2. Severe time interval: 52 rows stored 75 months where the source says "3.75 mo".
     Both are decimal-point parsing errors in the original export. The catalog schema
     stores whole months, so the source value is rounded half-up to the nearest month.
  3. Manufacturer approval strings: trim surrounding whitespace and normalise the
     GM dexos marks to the official lowercase "dexos1" / "dexos2".
  4. API service category: "API C" -> "API C-4" and "API CK" -> "API CK-4" where the
     row's own oil recommendation spells out the full category.
  5. Induction: rows labelled NATURALLY_ASPIRATED whose engine trim names a forced-
     induction engine (Turbo/TFSI/TDI/CDTI/TD) become TURBOCHARGED.
  6. Fuel type: rows labelled GASOLINE whose engine trim names a diesel engine
     (TDI/CDTI/CDI/TD/Diesel) become DIESEL.

The script is idempotent, refuses unexpected values, and updates both the bundled asset
and the exports copy. Run:  python3 scripts/fix_vehicle_catalog.py [--dry-run]
"""
from __future__ import annotations

import csv
import math
import re
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSET = ROOT / "app/src/main/assets/vehicle_catalog_v2.0.0_production.csv"
EXPORT = ROOT / "exports/vehicle_catalog_v2.0.0_production.csv"
BACKUP = ROOT / "scripts/vehicle_catalog_v2.0.0_pre_fix.csv"

EXPECTED_ROWS = 15_952
FIELD_SEP = "\x1f"
MONTHS_RE = re.compile(r"(\d+(?:\.\d+)?)\s*(?:months|mo\b|mos)", re.I)

NORMAL_MONTH_FIXES = {7.5: 8}   # source 7.5 mo -> nearest whole month
SEVERE_MONTH_FIXES = {3.75: 4}  # source 3.75 mo -> nearest whole month
APPROVAL_FIXES = {
    "dexos1": "dexos1",
    "dexos2": "dexos2",
    "Dexos2": "dexos2",
    "BMW LL-01 ": "BMW LL-01",
    "BMW LL-04 ": "BMW LL-04",
}
API_FIXES = {"API C": "API C-4", "API CK": "API CK-4"}

FORCED_INDUCTION_RE = re.compile(r"\bturbo\b|\btfsi\b|\btsi\b|\bfsi\b|\btdi\b|\bcdti\b|\btd\b|\bdti\b|\bsupercharg\w*\b|\bs/c\b|\bkompressor\b|\bbiturbo\b", re.I)
TWIN_TURBO_RE = re.compile(r"\btwin[\s-]?turbo\b|\bbiturbo\b|\bdual[\s-]?turbo\b", re.I)
SUPERCHARGE_RE = re.compile(r"\bsupercharg\w*\b|\bs/c\b|\bkompressor\b", re.I)
DIESEL_TRIM_RE = re.compile(r"\btdi\b|\bcdti\b|\btd\b|\bdti\b|\bcdi\b|\bdiesel\b|\bsdi\b|\bduramax\b|\becodiesel\b|\b300tdi\b", re.I)
GASOLINE_TRIM_RE = re.compile(r"\btsi\b|\btfsi\b|\bgti\b|\bvr6\b|\bna\b|\bfsi\b|\bgli\b|\bcupra\b|\bvrs\b", re.I)


def round_half_up(value: float) -> int:
    return int(math.floor(value + 0.5))


def source_fields(original_id: str) -> tuple[str, str]:
    parts = [p.strip() for p in original_id.split(FIELD_SEP) if p.strip()]
    if len(parts) < 3:
        return "", ""
    return parts[-3], parts[-2]


def parse_months(text: str) -> float | None:
    match = MONTHS_RE.search(text or "")
    return float(match.group(1)) if match else None


def fix(rows: list[list[str]], header: list[str]) -> dict[str, int]:
    idx = {name: i for i, name in enumerate(header)}
    changes = {
        "normal_months": 0, "severe_months": 0, "approval": 0,
        "api_category": 0, "induction": 0, "fuel_type": 0, "fuel_type_gasoline": 0,
    }

    def set_cell(row: list[str], column: str, value: str, counter: str) -> None:
        if row[idx[column]] != value:
            row[idx[column]] = value
            changes[counter] += 1

    for row in rows:
        normal_src, severe_src = source_fields(row[idx["original_catalog_id"]])

        raw_normal = row[idx["normal_interval_months"]].strip()
        src_normal = parse_months(normal_src)
        if src_normal is not None and src_normal != float(raw_normal):
            expected = NORMAL_MONTH_FIXES.get(src_normal)
            if expected is None:
                raise SystemExit(f"Unexpected normal-month source {src_normal!r} for {row[idx['vehicle_id']]}")
            set_cell(row, "normal_interval_months", str(expected), "normal_months")

        raw_severe = row[idx["severe_interval_months"]].strip()
        src_severe = parse_months(severe_src)
        if src_severe is not None and raw_severe and src_severe != float(raw_severe):
            expected = SEVERE_MONTH_FIXES.get(src_severe)
            if expected is None:
                raise SystemExit(f"Unexpected severe-month source {src_severe!r} for {row[idx['vehicle_id']]}")
            set_cell(row, "severe_interval_months", str(expected), "severe_months")

        approval = row[idx["oem_approval_spec"]]
        set_cell(row, "oem_approval_spec", APPROVAL_FIXES.get(approval, approval.strip()), "approval")

        category = row[idx["api_service_category"]].strip()
        if category in API_FIXES and category in row[idx["oil_recommendation"]]:
            set_cell(row, "api_service_category", API_FIXES[category], "api_category")

        trim = row[idx["engine_trim"]]
        if row[idx["induction_type"]] == "NATURALLY_ASPIRATED" and FORCED_INDUCTION_RE.search(trim):
            target = "TWIN_TURBO" if TWIN_TURBO_RE.search(trim) else ("SUPERCHARGED" if SUPERCHARGE_RE.search(trim) else "TURBOCHARGED")
            set_cell(row, "induction_type", target, "induction")

        if row[idx["fuel_type"]] == "GASOLINE" and DIESEL_TRIM_RE.search(trim):
            set_cell(row, "fuel_type", "DIESEL", "fuel_type")
        if row[idx["fuel_type"]] == "DIESEL" and GASOLINE_TRIM_RE.search(trim) and not DIESEL_TRIM_RE.search(trim):
            set_cell(row, "fuel_type", "GASOLINE", "fuel_type_gasoline")

    return changes


def validate(header: list[str], rows: list[list[str]]) -> None:
    idx = {name: i for i, name in enumerate(header)}
    assert len(rows) == EXPECTED_ROWS, f"row count {len(rows)} != {EXPECTED_ROWS}"
    ids = [r[idx["vehicle_id"]] for r in rows]
    assert len(set(ids)) == len(ids), "duplicate vehicle_id"
    for r in rows:
        for column in ("normal_interval_km", "normal_interval_mi", "normal_interval_months"):
            assert int(r[idx[column]]) > 0, f"{column} must be positive"
        for column in ("severe_interval_km", "severe_interval_mi", "severe_interval_months"):
            value = r[idx[column]].strip()
            if value:
                number = float(value)
                assert number > 0 and number.is_integer(), f"{column} must be a positive whole number or blank"
        assert (r[idx["severe_interval_km"]].strip() == "") == (r[idx["severe_interval_mi"]].strip() == "")
        assert r[idx["induction_type"]] in {"NATURALLY_ASPIRATED", "TURBOCHARGED", "SUPERCHARGED", "TWIN_TURBO", "ELECTRIC_DRIVE"}
        assert r[idx["fuel_type"]] in {"GASOLINE", "DIESEL", "HYBRID", "ELECTRIC"}


def main() -> int:
    if not ASSET.exists():
        raise SystemExit(f"missing {ASSET}")
    with ASSET.open(newline="", encoding="utf-8") as handle:
        reader = csv.reader(handle)
        header = next(reader)
        rows = [row for row in reader]

    original = [row[:] for row in rows]
    changes = fix(rows, header)
    validate(header, rows)

    diff_cells = sum(1 for before, after in zip(original, rows) for a, b in zip(before, after) if a != b)
    print(f"rows={len(rows)} changed_cells={diff_cells} {changes}")
    if diff_cells == 0:
        print("nothing to do")
        return 0
    if "--dry-run" in sys.argv:
        return 0

    if not BACKUP.exists():
        shutil.copy2(ASSET, BACKUP)
    for target in (ASSET, EXPORT):
        with target.open("w", newline="", encoding="utf-8") as handle:
            writer = csv.writer(handle, lineterminator="\n")
            writer.writerow(header)
            writer.writerows(rows)
        print(f"wrote {target}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
