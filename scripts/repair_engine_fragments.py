#!/usr/bin/env python3
"""Repair malformed engine_trim fragments in the bundled RoadLedger vehicle catalog.

The original export split engine lists on every " / " without respecting parentheses,
producing rows such as ``5.6L V8 (VK56DE`` and ``VK56VD)`` for the single variant
``5.6L V8 (VK56DE / VK56VD)``. This script:

  1. groups rows by (make, model, year, market);
  2. recovers the original engine list from the row's ``original_catalog_id`` provenance;
  3. splits it at top-level separators (parenthesis-aware);
  4. maps every fragment row back to its full variant and keeps one row per variant;
  5. drops exact duplicate rows;
  6. drops the 14 Tesla electric drive-unit rows (gearbox fluid, not engine oil).

The row count changes (15,952 -> 15,156). Run with ``--dry-run`` to preview.
"""
from __future__ import annotations

import csv
import re
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSET = ROOT / "app/src/main/assets/vehicle_catalog_v2.0.0_production.csv"
EXPORT = ROOT / "exports/vehicle_catalog_v2.0.0_production.csv"
FIELD_SEP = "\x1f"


def split_top_level(value: str) -> list[str]:
    parts: list[str] = []
    depth = 0
    start = 0
    for index, char in enumerate(value):
        if char == "(":
            depth += 1
        elif char == ")":
            depth = max(0, depth - 1)
        elif char == "/" and depth == 0 and 0 < index < len(value) - 1 and value[index - 1].isspace() and value[index + 1].isspace():
            parts.append(value[start:index].strip())
            start = index + 1
    parts.append(value[start:].strip())
    return [part for part in parts if part]


def norm(value: str) -> str:
    return re.sub(r"\s*/\s*", "/", re.sub(r"\s+", " ", value.strip().lower()))


def fields_of(original_id: str) -> list[str]:
    return [part.strip() for part in original_id.split(FIELD_SEP) if part.strip()]


def find_engine_list(rows: list[dict[str, str]], fields: list[str]) -> str | None:
    normalized_fragments = [norm(row["engine_trim"]) for row in rows]
    for candidate in fields:
        normalized = norm(candidate)
        if all(fragment and fragment in normalized for fragment in normalized_fragments):
            return candidate
    return None


def repair(rows: list[dict[str, str]]) -> tuple[list[dict[str, str]], Counter[str]]:
    stats: Counter[str] = Counter()
    groups: dict[tuple[str, str, str, str], list[dict[str, str]]] = {}
    order: list[tuple[str, str, str, str]] = []
    for row in rows:
        key = (row["make"], row["model"], row["model_year"], row["market_region"])
        if key not in groups:
            groups[key] = []
            order.append(key)
        groups[key].append(row)

    kept: list[dict[str, str]] = []
    for key in order:
        group = groups[key]
        ai_rows = [row for row in group if row["verification_status"] == "AI_GENERATED_UNVERIFIED"]
        researched_rows = [row for row in group if row["verification_status"] != "AI_GENERATED_UNVERIFIED"]
        kept.extend(researched_rows)
        stats["researched_rows"] += len(researched_rows)
        if not ai_rows:
            continue

        engine_list = find_engine_list(ai_rows, fields_of(ai_rows[0]["original_catalog_id"]))
        if engine_list is None:
            for row in ai_rows:
                engine_list = find_engine_list(ai_rows, fields_of(row["original_catalog_id"]))
                if engine_list:
                    break
        if engine_list is None:
            stats["unresolved_groups"] += 1
            kept.extend(ai_rows)
            continue

        variants = split_top_level(engine_list)
        variant_index: dict[str, dict[str, str]] = {}
        cursor = 0
        accumulator = ""
        resolved = True
        for row in ai_rows:
            fragment = row["engine_trim"]
            piece = fragment if not accumulator else accumulator + " / " + fragment
            if cursor >= len(variants):
                resolved = False
                break
            target = variants[cursor]
            if norm(piece) == norm(target):
                variant_index.setdefault(target, row)
                cursor += 1
                accumulator = ""
            elif norm(target).startswith(norm(piece)):
                accumulator = piece
            else:
                resolved = False
                break
        if not resolved or cursor != len(variants):
            stats["unresolved_groups"] += 1
            kept.extend(ai_rows)
            continue

        for variant in variants:
            row = variant_index.get(variant)
            if row is None:
                stats["missing_variant_rows"] += 1
                continue
            row["engine_trim"] = variant
            kept.append(row)
            stats["merged_rows"] += sum(1 for candidate in ai_rows if candidate is not row and norm(candidate["engine_trim"]) in norm(variant)) 

    # Drop exact duplicates (every column except the stable vehicle_id).
    content_columns = [column for column in rows[0].keys() if column != "vehicle_id"]
    seen: set[tuple[str, ...]] = set()
    unique: list[dict[str, str]] = []
    for row in kept:
        signature = tuple(row[column] for column in content_columns)
        if signature in seen:
            stats["exact_duplicates_dropped"] += 1
            continue
        seen.add(signature)
        unique.append(row)

    # Drop electric drive-unit rows: they describe gearbox fluid, not engine oil.
    before = len(unique)
    unique = [row for row in unique if not (row["fuel_type"] == "ELECTRIC" and row["requires_oil_change"].lower() == "false")]
    stats["electric_drive_unit_rows_dropped"] = before - len(unique)
    return unique, stats


def main() -> int:
    if not ASSET.exists():
        raise SystemExit(f"missing {ASSET}")
    with ASSET.open(newline="", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        fieldnames = reader.fieldnames or []
        rows = list(reader)

    repaired, stats = repair(rows)
    print(f"rows: {len(rows)} -> {len(repaired)} ({len(repaired) - len(rows):+d})")
    print(dict(stats))
    if "--dry-run" in sys.argv:
        return 0

    for target in (ASSET, EXPORT):
        with target.open("w", newline="", encoding="utf-8") as handle:
            writer = csv.DictWriter(handle, fieldnames=fieldnames, lineterminator="\n")
            writer.writeheader()
            writer.writerows(repaired)
        print(f"wrote {target}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
