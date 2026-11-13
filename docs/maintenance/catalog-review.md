# Vehicle catalog review

## State of the catalog

The active bundled database is imported from `exports/vehicle_catalog_v2.0.0_production.csv` and has 15,952 year/engine entries: 15,886 `AI_GENERATED_UNVERIFIED` rows and 66 `OFFICIAL_INTERVAL_RESEARCHED` interval suggestions. The source file is retained unchanged in `app/src/main/assets/vehicle_catalog_v2.0.0_production.csv`; each imported row preserves its original fields in source metadata. The researched status only identifies a cited interval suggestion; it does **not** verify every year, engine, trim, oil grade/approval, capacity, or filter. Neither imported status drives schedules until the user reviews the exact vehicle source and explicitly verifies the selected schedule data.

Normal and severe distance intervals are imported from the production file's metric columns and stored in KM; mile values and all other source columns remain in the original-row provenance. Oil capacities and torque fields are blank in this production export and remain unknown. Year rows are selectable individually. The app only splits engine lists when labels and interval data are unambiguous.

## Catalog data revision 2.1.0 (2026-11-12)

The bundled CSV was re-audited against its own `original_catalog_id` provenance text and its engine/oil fields. **1,872 cells across 1,440 rows** were corrected, **940 malformed engine fragments were merged**, **7 exact duplicates removed**, and **14 Tesla electric drive-unit rows removed** (gearbox fluid, not engine oil). The catalog now has **15,149 rows** (15,083 `AI_GENERATED_UNVERIFIED` + 66 `OFFICIAL_INTERVAL_RESEARCHED`). Every correction is derived from the row itself; no value was invented.

| Correction | Cells / rows | Detail |
|---|---:|---|
| Normal time interval | 102 | Source text says `7.5 mo`; the export had truncated it to `5`. Stored as `8` months (nearest whole month; the schema stores integers). |
| Severe time interval | 52 | Source text says `3.75 mo`; the export had read it as `75`. Stored as `4` months. |
| Manufacturer approval | 392 | Trimmed surrounding whitespace; normalised `Dexos2` to the official `dexos2`. |
| API service category | 110 | `API C` → `API C-4` (38) and `API CK` → `API CK-4` (72), matching the row's own oil recommendation. |
| Induction type | 854 | `NATURALLY_ASPIRATED` → `TURBOCHARGED` where the engine trim names a turbo/TFSI/TSI/FSI/TDI/CDTI/TD engine. |
| Fuel type | 362 | `GASOLINE` → `DIESEL` (112) where the trim names a CDI/CDTI/TDI/TD diesel; `DIESEL` → `GASOLINE` (250) where the trim names a TSI/FSI/VR6 gasoline engine. |
| Engine fragments merged | 782 rows | The source engine list was split inside parentheses (`5.6L V8 (VK56DE` / `VK56VD)`). Fragments were rejoined into their full variants. |
| Exact duplicates removed | 7 | Mercedes-Benz E-Class W212, 2010–2016, 3.5L V6. |
| Electric drive-unit rows removed | 14 | Tesla rows described the drive-unit gearbox fluid (150,000 mi inspection), not engine oil. |

`VehicleCatalogSeeder.CATALOG_VERSION` was bumped to `2.1.0`. The seeder refreshes untouched `AI_GENERATED_UNVERIFIED` rows when the version changes, and removes rows that no longer exist in the bundled file (merged/deduplicated/removed) as long as no vehicle references them. User-edited rows (`USER_ENTERED_UNVERIFIED`), user-verified rows, source-researched rows, and referenced legacy rows are preserved. Before this change, existing installs kept stale catalog data.

Maintenance scripts: `scripts/audit_vehicle_catalog.py` (read-only report), `scripts/fix_vehicle_catalog.py` (idempotent field corrections; `--dry-run`), and `scripts/repair_engine_fragments.py` (engine-list repair; `--dry-run`).

### Still unresolved

- **163 rows** have severe distance equal to normal distance and no severe time limit; the source gives a distance-only severe range (`3,000-5,000 mi / Off-road`).
- **Missing fields that need OEM sources:** oil capacity, drain-plug torque/socket, and filter wrench size are empty for all rows; cylinder config, OEM engine code, approval, and API category are blank for 30–65% of rows. These were not invented.
- **Naming consistency:** `Scion` and `Scion / Toyota` are separate makes; `Ford Australia` is separate from `Ford`; `market_region` mixes 11 granularity levels.
- **136 rows labelled `DIESEL` have no diesel word in the trim** and need per-model review (e.g., Audi Q7 3.0T, Ford PowerStroke, Land Rover 300Tdi are legitimate).

## OEM interval suggestions

The source CSV contains 66 entries marked `OFFICIAL_INTERVAL_RESEARCHED`. The tables below retain the previously documented source checks; use the CSV's `verification_source_name` and `verification_source_url` columns to review each additional suggestion. None is schedule-active until a user verifies the exact entry.

### Toyota and Lexus

| Vehicle | Normal oil interval | Severe interval | Interval source |
|---|---:|---:|---|
| Toyota Prius 2007 | 16,094 km / 12 mo | Not imported | [Toyota 2007 Prius Scheduled Maintenance Guide](https://assets.sia.toyota.com/publications/en/omms-s/07ToyMS_MS0003/pdf/07ToyMS_MS0003.pdf) |
| Toyota Prius 2016 | 16,094 km / 12 mo when using specified 0W-20 | Not imported | [Toyota 2016 Prius Warranty and Maintenance Guide](https://assets.sia.toyota.com/publications/en/omms-s/T-MMS-Prius/pdf/2016PriusWSG.pdf) |
| Toyota Prius 2017 | 16,094 km / 12 mo | 8,047 km / 6 mo under listed conditions | [Toyota 2017 Prius Warranty and Maintenance Guide](https://assets.sia.toyota.com/publications/en/omms-s/T-MMS-17Prius/pdf/T-MMS-17Prius.pdf) |
| Toyota Prius 2026 | 16,094 km / 12 mo | 8,047 km / 6 mo for specified dirt/dust-road use | [Toyota 2026 Prius Warranty and Maintenance Guide](https://assets.sia.toyota.com/publications/en/omms-s/T-MMS-26Prius/pdf/T-MMS-26Prius.pdf) |
| Lexus CT 200h 2013 | 16,094 km / 12 mo | 8,047 km / 6 mo under listed conditions | [2013 Lexus CT 200h Warranty and Services Guide](https://assets.sia.toyota.com/publications/en/omms-s/L-MMS-13CT200H/pdf/MY13_Lexus%20CT%20200h_WSG_lr.pdf); [Lexus L-TT-0142-13 interval bulletin](https://static.nhtsa.gov/odi/tsbs/2013/MC-10132323-9999.pdf) |
| Lexus LX 600 2023 | 16,094 km / 12 mo or when maintenance reminder appears | Not imported | [2023 Lexus LX 600 Warranty and Services Guide](https://assets.sia.toyota.com/publications/en/omms-s/L-MMS-23X600/pdf/L-MMS-23X600.pdf) |
| Lexus RX 500h 2024 | 16,094 km / 12 mo or when maintenance reminder appears | Not imported | [2024 Lexus RX 500h Warranty and Services Guide](https://assets.sia.toyota.com/publications/en/omms-s/L-MMS-24RX500H/pdf/L-MMS-24RX500H.pdf) |

### Infiniti

The Infiniti maintenance pages are exact model-year/model interval sources. Engine/trim fitment is not established by those interval pages and still needs VIN/owner-manual confirmation.

| Vehicle | Normal oil interval | Severe interval | Official source |
|---|---:|---:|---|
| FX35 2003 | 12,071 km / 6 mo | 6,036 km / 3 mo | [Infiniti FX35 2003](https://www.infinitiusa.com/maintenance-schedules/2003/fx35/components/engine-oil-and-filter/) |
| G35 2003 | 12,071 km / 6 mo | 6,036 km / 3 mo | [Infiniti G35 2003](https://www.infinitiusa.com/maintenance-schedules/2003/g35/components/engine-oil-and-filter/) |
| QX56 2004 | 12,071 km / 6 mo | 6,036 km / 3 mo | [Infiniti QX56 2004](https://www.infinitiusa.com/maintenance-schedules/2004/qx56/components/engine-oil-and-filter/) |
| QX70 2017 | 8,047 km / 6 mo | 8,047 km / 6 mo | [Infiniti QX70 2017](https://www.infinitiusa.com/maintenance-schedules/2017/qx70/components/engine-oil-and-filter/) |
| QX60 2020 | 12,071 km / 6 mo | 8,047 km / 6 mo | [Infiniti QX60 2020](https://www.infinitiusa.com/maintenance-schedules/2020/qx60/components/engine-oil-and-filter/) |
| Q50 2023 | 16,094 km / 12 mo | 16,094 km / 12 mo | [Infiniti Q50 2023](https://www.infinitiusa.com/maintenance-schedules/2023/q50/components/engine-oil-and-filter/) |
| QX60 2022 | 16,094 km / 12 mo | 16,094 km / 12 mo | [Infiniti QX60 2022](https://www.infinitiusa.com/maintenance-schedules/2022/qx60/components/engine-oil-and-filter/) |
| QX60 2025 | 12,071 km / 12 mo | 12,071 km / 12 mo | [Infiniti QX60 2025](https://www.infinitiusa.com/maintenance-schedules/2025/qx60/components/engine-oil-and-filter/) |
| QX80 2025 | 16,094 km / 12 mo | 16,094 km / 12 mo | [Infiniti QX80 2025](https://www.infinitiusa.com/maintenance-schedules/2025/qx80/components/engine-oil-and-filter/) |

### Mazda (2025 North American owner's manuals)

Mazda's manuals require following the vehicle status/wrench monitor, which may call for service before the listed maximum. The app cannot reproduce that monitor; the catalog entries are confirmation-required reminders, not a substitute for the vehicle's indicator.

| Vehicle | Normal oil interval | Severe interval | Official source |
|---|---:|---:|---|
| Mazda3 2025 | Monitor-triggered; max 16,000 km / 12 mo | 8,000 km / 6 mo | [2025 Mazda3 Scheduled Maintenance](https://www.mazdausa.com/static/manuals/2025/mazda3/contents/69031000.html) |
| CX-5 2025 | Monitor-triggered; max 12,000 km / 12 mo | Not imported | [2025 CX-5 Scheduled Maintenance](https://www.mazdausa.com/static/manuals/2025/cx-5/contents/07020201.html) |
| CX-30 2025 | Monitor-triggered; max 16,000 km / 12 mo | 8,000 km / 6 mo | [2025 CX-30 Scheduled Maintenance](https://www.mazdausa.com/static/manuals/2025/cx-30/contents/69031000.html) |
| CX-50 2025 | Monitor-triggered; max 16,000 km / 12 mo | 8,000 km / 6 mo | [2025 CX-50 Scheduled Maintenance](https://www.mazdausa.com/static/manuals/2025/cx-50/contents/69031000.html) |
| CX-90 2025 | Monitor-triggered; max 16,000 km / 12 mo | 8,000 km / 6 mo | [2025 CX-90 Scheduled Maintenance](https://www.mazdausa.com/static/manuals/2025/cx-90/contents/69031000.html) |

### Subaru

Subaru's model-year interval pages explicitly list engine oil and filter replacement. They do not provide a distinct severe oil interval in the cited schedule pages. Engine/trim coverage remains to be checked against the vehicle's own booklet.

| Model years | Exact catalog model choices | Oil interval | Official source |
|---|---|---:|---|
| 2014 | Crosstrek, Forester, Impreza / WRX / STI, Outback / Legacy, Tribeca | 12,000 km / 6 mo | [2014 Subaru Maintenance Recommendations](https://contactus.subaru.com/knowledgebase/article/1002371/en-us) |
| 2017 | Crosstrek, Forester, Outback / Legacy, WRX / STI | 9,657 km / 6 mo (converted from 6,000 mi) | [2017 Subaru Maintenance Recommendations](https://contactus.subaru.com/knowledgebase/article/1001834/en-us) |
| 2018 | Crosstrek, Forester, Outback / Legacy, WRX / STI | 9,500 km / 6 mo (OEM metric value) | [2018 Subaru Maintenance Recommendations](https://contactus.subaru.com/knowledgebase/article/1001833/en-us) |
| 2019 | Crosstrek, Forester, Outback / Legacy, WRX / STI | 9,500 km / 6 mo (OEM metric value) | [2019 Subaru Maintenance Recommendations](https://contactus.subaru.com/knowledgebase/article/1001832/en-us) |
| 2020 | Crosstrek, Forester, Outback / Legacy, WRX / STI | 9,500 km / 6 mo (OEM metric value) | [2020 Subaru Maintenance Recommendations](https://contactus.subaru.com/knowledgebase/article/1002183/en-us) |

## What remains unresolved

A multi-agent OEM-source sweep covered the supplied catalog by make groups, but several groups hit web-search limits or were interrupted, and many manufacturer guides are unavailable or only describe variable oil-life systems. No original CSV row was established as fully verified across exact year/model/engine, interval, oil grade/approval, capacity, and filter. Most of the 476 rows therefore remain unverified. The 42 supplements verify an interval suggestion only; several retain AI-sourced engine/trim labels, and the app does not parse every engine-specific condition or variable monitor.

The app deliberately does not promote a source-researched row to schedule-active. The user must review the cited model-year guide and confirm the exact vehicle/engine before use. A source citation is not evidence that unrelated fields in the row are correct.

## Why an estimated due date may be absent

A date projection needs a due-odometer target and a usable daily-distance estimate. The app derives daily use from at least two increasing odometer readings on different dates; otherwise the vehicle needs a starting daily-use value. Older vehicles upgraded to the new field have no baseline until one is entered in Edit vehicle. New dated readings supersede that baseline and update the estimate.

A schedule also needs an oil-change record and a usable interval. A per-service or vehicle-level manual interval works directly. Imported catalog intervals are used only after the user marks them verified. The card explains whether an interval, oil-change record, or daily-use estimate is missing, and labels this state “Schedule unavailable” rather than “Not verified.”

## Reproducibility

The production CSV is the source of truth for the catalog and is bundled unchanged. `VehicleCatalogSeeder` imports it idempotently with stable per-row IDs and records the seed version (`2.0.1`) in `app_settings`. On a version change it refreshes untouched `AI_GENERATED_UNVERIFIED` rows so data corrections reach existing installs, while preserving user-edited, user-verified, and source-researched rows. It retains legacy catalog rows so vehicle foreign keys remain valid. `scripts/audit_vehicle_catalog.py` reports structural and consistency problems; `scripts/fix_vehicle_catalog.py` applies the 2.0.1 corrections idempotently. The prior enrichment script describes the retired 518-row catalog and does not regenerate the production file.
