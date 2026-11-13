#!/usr/bin/env python3
"""Audit the bundled RoadLedger vehicle catalog for structural and consistency problems.

Read-only. Prints a prioritized summary; exit code 0 always (it is a report, not a gate).
Usage:  python3 scripts/audit_vehicle_catalog.py
"""
from __future__ import annotations

import csv
import math
import re
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "app/src/main/assets/vehicle_catalog_v2.0.0_production.csv"
FIELD_SEP = "\x1f"

MONTHS_RE = re.compile(r"(\d+(?:\.\d+)?)\s*(?:months|mo\b|mos)", re.I)
FORCED_RE = re.compile(r"\bturbo\b|\btfsi\b|\btdi\b|\bcdti\b|\btd\b|\bdti\b|\bsupercharg\w*\b|\bs/c\b|\bkompressor\b|\bbiturbo\b", re.I)
DIESEL_RE = re.compile(r"\btdi\b|\bcdti\b|\btd\b|\bdti\b|\bcdi\b|\bdiesel\b|\bsdi\b|\bduramax\b|\becodiesel\b", re.I)


def source_fields(original_id: str) -> tuple[str, str]:
    parts = [p.strip() for p in original_id.split(FIELD_SEP) if p.strip()]
    return (parts[-3], parts[-2]) if len(parts) >= 3 else ("", "")


def main() -> int:
    if not CATALOG.exists():
        print(f"missing {CATALOG}")
        return 1
    with CATALOG.open(newline="", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        rows = list(reader)
        header = reader.fieldnames or []

    issues: list[tuple[str, str, int]] = []

    def add(severity: str, title: str, count: int) -> None:
        issues.append((severity, title, count))

    # --- structure ---
    add("info", "rows", len(rows))
    add("info", "columns", len(header))
    ids = [r["vehicle_id"] for r in rows]
    add("high" if len(set(ids)) != len(ids) else "ok", "duplicate vehicle_id", len(ids) - len(set(ids)))

    content_key = [c for c in header if c != "vehicle_id"]
    exact = Counter(tuple(r[c] for c in content_key) for r in rows)
    add("medium" if any(v > 1 for v in exact.values()) else "ok", "exact duplicate rows (ignoring id)", sum(v - 1 for v in exact.values() if v > 1))

    logical = Counter((r["make"], r["model"], r["model_year"], r["market_region"], r["engine_trim"]) for r in rows)
    add("info", "logical duplicate groups (same name/year/engine)", sum(v - 1 for v in logical.values() if v > 1))

    # --- missing fields ---
    for column in header:
        blank = sum(1 for r in rows if not r[column].strip())
        if blank:
            add("info", f"blank {column}", blank)

    # --- month source consistency (source decimals are rounded half-up to whole months) ---
    def rounded(text: str) -> float | None:
        match = MONTHS_RE.search(text)
        return float(match.group(1)) if match else None

    normal_bad = severe_bad = 0
    for r in rows:
        normal_src, severe_src = source_fields(r["original_catalog_id"])
        src = rounded(normal_src)
        if src is not None and float(math.floor(src + 0.5)) != float(r["normal_interval_months"]):
            normal_bad += 1
        src = rounded(severe_src)
        if src is not None and r["severe_interval_months"].strip() and float(math.floor(src + 0.5)) != float(r["severe_interval_months"]):
            severe_bad += 1
    add("high" if normal_bad else "ok", "normal months disagree with source text", normal_bad)
    add("high" if severe_bad else "ok", "severe months disagree with source text", severe_bad)

    # --- severe vs normal ---
    sev_gt = sev_eq = 0
    for r in rows:
        skm = r["severe_interval_km"].strip()
        if not skm:
            continue
        skm_i, nkm_i = float(skm), float(r["normal_interval_km"])
        if skm_i > nkm_i:
            sev_gt += 1
        elif skm_i == nkm_i:
            sev_eq += 1
    add("high" if sev_gt else "ok", "severe distance greater than normal", sev_gt)
    add("info", "severe distance equals normal", sev_eq)

    # --- engine trim shape ---
    unbalanced = [r for r in rows if r["engine_trim"].count("(") != r["engine_trim"].count(")")]
    add("high" if unbalanced else "ok", "engine_trim with unbalanced parentheses", len(unbalanced))
    if unbalanced:
        add("info", "example unbalanced engine_trim", 0)
        for value, count in Counter(r["engine_trim"] for r in unbalanced).most_common(5):
            print(f"    {count:4d}  {value!r}")

    # --- induction / fuel consistency ---
    na_forced = [r for r in rows if r["induction_type"] == "NATURALLY_ASPIRATED" and FORCED_RE.search(r["engine_trim"])]
    add("high" if na_forced else "ok", "naturally-aspirated label on forced-induction engine", len(na_forced))
    gas_diesel = [r for r in rows if r["fuel_type"] == "GASOLINE" and DIESEL_RE.search(r["engine_trim"])]
    add("high" if gas_diesel else "ok", "gasoline label on diesel engine", len(gas_diesel))

    # --- provenance-only gaps (no in-row source) ---
    for column in ("oil_capacity_liters", "drain_plug_torque_ft_lbs", "drain_plug_socket_mm", "filter_wrench_size"):
        blank = sum(1 for r in rows if not r[column].strip())
        if blank == len(rows):
            add("low", f"entirely missing {column} (needs OEM source)", blank)

    print(f"catalog: {CATALOG}")
    for severity, title, count in issues:
        if severity in {"high", "medium"} or count:
            print(f"  [{severity:6s}] {title}: {count}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
