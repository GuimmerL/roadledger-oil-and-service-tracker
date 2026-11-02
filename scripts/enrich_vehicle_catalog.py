#!/usr/bin/env python3
"""Add reproducible, exact-year OEM interval supplements to the seeded catalog."""
from __future__ import annotations

import json
import math
import re
from pathlib import Path

CATALOG = Path(__file__).resolve().parents[1] / "app/src/main/assets/vehicle_catalog_ai_generated.json"
RESEARCH_DATE = "2026-10-27"
OFFICIAL_STATUS = "OFFICIAL_INTERVAL_RESEARCHED"
DATA_FIELDS = [
    "make", "model", "years", "marketRegion", "engineTrim", "oilRecommendation",
    "filterDescription", "normalIntervalRaw", "severeIntervalRaw", "sourceReliabilityRating",
]
MILE_RANGE = re.compile(r"(?i)(?<![A-Za-z0-9.])(?P<low>\d[\d,]*(?:\.\d+)?)\s*[-–]\s*(?P<high>\d[\d,]*(?:\.\d+)?)\s*(?:miles?|mi)\b")
MILE_TOKEN = re.compile(r"(?i)(?<![A-Za-z0-9.])(?P<value>\d[\d,]*(?:\.\d+)?)\s*(?:-\s*)?(?:miles?|mi)\b")
US_QUART_RANGE = re.compile(r"(?i)(?<![A-Za-z0-9.])(?P<approx>~\s*)?(?P<low>\d[\d,]*(?:\.\d+)?)\s*[-–]\s*(?P<high>\d[\d,]*(?:\.\d+)?)\s*(?:US\s*)?(?:quarts?|qt)\b")
US_QUART_TOKEN = re.compile(r"(?i)(?<![A-Za-z0-9.])(?P<approx>~\s*)?(?P<value>\d[\d,]*(?:\.\d+)?)\s*(?:US\s*)?(?:quarts?|qt)\b")


def to_metric_interval(raw: str) -> str:
    """Convert miles to km and US quarts to L; retain enough precision for capacities."""
    def km(value: str) -> str:
        amount = float(value.replace(",", ""))
        return f"{math.ceil(amount * 1.609344):,}"

    def liters(value: str) -> str:
        amount = float(value.replace(",", "")) * 0.946352946
        return f"{amount:.2f}".rstrip("0").rstrip(".")

    converted = MILE_RANGE.sub(lambda m: f"{km(m['low'])}-{km(m['high'])} km", raw)
    converted = MILE_TOKEN.sub(lambda m: f"{km(m['value'])} km", converted)
    converted = US_QUART_RANGE.sub(lambda m: f"{m['approx'] or ''}{liters(m['low'])}-{liters(m['high'])} L", converted)
    return US_QUART_TOKEN.sub(lambda m: f"{m['approx'] or ''}{liters(m['value'])} L", converted)


def repair_known_csv_column_shifts(row: dict) -> None:
    if row.get("dataRepairCode"):
        return
    if row.get("make") == "Dodge" and row.get("model") == "Dakota" and row.get("years") == "2000-2011":
        engine_text = row.get("marketRegion", "")
        if row.get("engineTrim") == "North America" and "L" in engine_text:
            row["marketRegion"] = "North America"
            row["engineTrim"] = engine_text
            row["dataRepairCode"] = "CSV_MARKET_ENGINE_SWAP_DODGE_DAKOTA"
            row["dataRepairNote"] = "Source Market and Engine columns were swapped; fields were reassigned, but engine variants and intervals remain unverified."
    elif row.get("make") == "Suzuki" and row.get("model") == "Grand Vitara" and row.get("years") == "2000-2013":
        engine_text = row.get("marketRegion", "")
        oil_text = row.get("engineTrim", "")
        filter_text = row.get("oilRecommendation", "")
        interval_text = row.get("sourceFilterDescription", row.get("filterDescription", ""))
        severe_text = row.get("sourceNormalIntervalRaw", row.get("normalIntervalRaw", ""))
        reliability = row.get("sourceSevereIntervalRaw", row.get("severeIntervalRaw", ""))
        if "L" in engine_text and oil_text.upper().startswith("SAE") and filter_text.lower().startswith(("spin-on", "cartridge")):
            row["marketRegion"] = "Not specified in source"
            row["engineTrim"] = engine_text
            row["oilRecommendation"] = oil_text
            row["filterDescription"] = filter_text
            row["sourceFilterDescription"] = filter_text
            row["sourceNormalIntervalRaw"] = interval_text
            row["sourceSevereIntervalRaw"] = severe_text
            row["sourceReliabilityRating"] = reliability
            row["dataRepairCode"] = "CSV_MISSING_MARKET_SHIFT_SUZUKI_GRAND_VITARA"
            row["dataRepairNote"] = "Source columns shifted after a missing market value; fields were realigned and market left unspecified. Engine, oil, and interval data remain unverified."


def preserve_ids_and_metric_intervals(records: list[dict]) -> None:
    for row in records:
        if not row.get("catalogId"):
            row["catalogId"] = "\x1f".join(str(row.get(field, "")) for field in DATA_FIELDS)
        repair_known_csv_column_shifts(row)
        row.setdefault("sourceNormalIntervalRaw", row.get("normalIntervalRaw", ""))
        row.setdefault("sourceSevereIntervalRaw", row.get("severeIntervalRaw", ""))
        row.setdefault("sourceFilterDescription", row.get("filterDescription", ""))
        row["normalIntervalRaw"] = to_metric_interval(row["sourceNormalIntervalRaw"])
        row["severeIntervalRaw"] = to_metric_interval(row["sourceSevereIntervalRaw"])
        row["filterDescription"] = to_metric_interval(row["sourceFilterDescription"])
        if row.get("researchNote"):
            row["researchNote"] = to_metric_interval(row["researchNote"])
        for source in row.get("additionalSources", []):
            if source.get("notes"):
                source["notes"] = to_metric_interval(source["notes"])

SUPPLEMENTS = [
    {
        "catalogId": "official-supplement:toyota-prius-2007",
        "make": "Toyota", "model": "Prius", "years": "2007", "marketRegion": "North America",
        "engineTrim": "1.5L I4 (1NZ-FXE)", "oilRecommendation": "", "filterDescription": "",
        "normalIntervalRaw": "10,000 mi / 12 mo", "severeIntervalRaw": "",
        "sourceReliabilityRating": "Official interval supplement; other specifications unverified",
        "verificationStatus": OFFICIAL_STATUS,
        "verificationSourceName": "Toyota 2007 Prius Scheduled Maintenance Guide",
        "verificationSourceUrl": "https://assets.sia.toyota.com/publications/en/omms-s/07ToyMS_MS0003/pdf/07ToyMS_MS0003.pdf",
        "verificationDate": RESEARCH_DATE,
        "researchNote": "The 2007 schedule explicitly lists engine oil and filter replacement at 10,000 miles or 12 months. No severe oil-change interval is imported from this source.",
    },
    {
        "catalogId": "official-supplement:toyota-prius-2016",
        "make": "Toyota", "model": "Prius", "years": "2016", "marketRegion": "North America",
        "engineTrim": "1.8L I4 (2ZR-FXE)", "oilRecommendation": "", "filterDescription": "",
        "normalIntervalRaw": "10,000 mi / 12 mo", "severeIntervalRaw": "",
        "sourceReliabilityRating": "Official interval supplement; other specifications unverified",
        "verificationStatus": OFFICIAL_STATUS,
        "verificationSourceName": "Toyota 2016 Prius Warranty and Maintenance Guide",
        "verificationSourceUrl": "https://assets.sia.toyota.com/publications/en/omms-s/T-MMS-Prius/pdf/2016PriusWSG.pdf",
        "verificationDate": RESEARCH_DATE,
        "researchNote": "The guide lists oil and filter replacement at 10,000 miles or 12 months when 0W-20 is used; viscosity and non-0W-20 exceptions are not imported into this interval record.",
    },
    {
        "catalogId": "official-supplement:toyota-prius-2017",
        "make": "Toyota", "model": "Prius", "years": "2017", "marketRegion": "North America",
        "engineTrim": "1.8L I4 (2ZR-FXE)", "oilRecommendation": "", "filterDescription": "",
        "normalIntervalRaw": "10,000 mi / 12 mo", "severeIntervalRaw": "5,000 mi / 6 mo",
        "sourceReliabilityRating": "Official interval supplement; other specifications unverified",
        "verificationStatus": OFFICIAL_STATUS,
        "verificationSourceName": "Toyota 2017 Prius Warranty and Maintenance Guide",
        "verificationSourceUrl": "https://assets.sia.toyota.com/publications/en/omms-s/T-MMS-17Prius/pdf/T-MMS-17Prius.pdf",
        "verificationDate": RESEARCH_DATE,
        "researchNote": "The guide specifies 10,000 miles or 12 months with 0W-20 and calls for 5,000 miles or 6 months under listed special operating conditions. Confirm the required oil grade and market-specific guide for the vehicle.",
    },
    {
        "catalogId": "official-supplement:toyota-prius-2026",
        "make": "Toyota", "model": "Prius", "years": "2026", "marketRegion": "North America",
        "engineTrim": "2.0L I4 Hybrid (M20A-FXS)", "oilRecommendation": "", "filterDescription": "",
        "normalIntervalRaw": "10,000 mi / 12 mo", "severeIntervalRaw": "5,000 mi / 6 mo",
        "sourceReliabilityRating": "Official interval supplement; other specifications unverified",
        "verificationStatus": OFFICIAL_STATUS,
        "verificationSourceName": "Toyota 2026 Prius Warranty and Maintenance Guide",
        "verificationSourceUrl": "https://assets.sia.toyota.com/publications/en/omms-s/T-MMS-26Prius/pdf/T-MMS-26Prius.pdf",
        "verificationDate": RESEARCH_DATE,
        "researchNote": "The guide lists 10,000 miles or 12 months normally, with 5,000 miles or 6 months for specified dirt/dust-road operation. Verify the market and exact powertrain against the vehicle's guide.",
    },
    {
        "catalogId": "official-supplement:lexus-ct-200h-2013",
        "make": "Lexus", "model": "CT 200h", "years": "2013", "marketRegion": "North America",
        "engineTrim": "1.8L I4 Hybrid (2ZR-FXE)", "oilRecommendation": "", "filterDescription": "",
        "normalIntervalRaw": "10,000 mi / 12 mo", "severeIntervalRaw": "5,000 mi / 6 mo",
        "sourceReliabilityRating": "Official interval supplement; other specifications unverified",
        "verificationStatus": OFFICIAL_STATUS,
        "verificationSourceName": "2013 Lexus CT 200h Warranty and Services Guide",
        "verificationSourceUrl": "https://assets.sia.toyota.com/publications/en/omms-s/L-MMS-13CT200H/pdf/MY13_Lexus%20CT%20200h_WSG_lr.pdf",
        "verificationDate": RESEARCH_DATE,
        "additionalSources": [
            {
                "sourceName": "Lexus technical bulletin L-TT-0142-13 (NHTSA-hosted)",
                "sourceUrl": "https://static.nhtsa.gov/odi/tsbs/2013/MC-10132323-9999.pdf",
                "verifiedOn": RESEARCH_DATE,
                "notes": "Lists CT 200h / 2ZR-FXE / 2011-2014, 0W-20, 10,000-mile oil interval, and 5,000-mile oil changes under specified special operating conditions.",
            }
        ],
        "researchNote": "The 2013 guide's oil/filter service is at 10,000 miles or 12 months; the Lexus bulletin confirms the 2ZR-FXE 10,000-mile interval and specified 5,000-mile special-condition interval. Routine 5,000-mile visits are not automatically oil changes.",
    },
]


def researched_interval_row(
    catalog_id: str,
    make: str,
    model: str,
    year: int,
    engine: str,
    normal: str,
    severe: str,
    source_name: str,
    source_url: str,
    note: str,
    market: str = "North America",
) -> dict:
    return {
        "catalogId": f"official-supplement:{catalog_id}",
        "make": make,
        "model": model,
        "years": str(year),
        "marketRegion": market,
        "engineTrim": engine,
        "oilRecommendation": "",
        "filterDescription": "",
        "normalIntervalRaw": normal,
        "severeIntervalRaw": severe,
        "sourceReliabilityRating": "OEM oil interval source researched; other fields may remain unverified",
        "verificationStatus": OFFICIAL_STATUS,
        "verificationSourceName": source_name,
        "verificationSourceUrl": source_url,
        "verificationDate": RESEARCH_DATE,
        "researchNote": note,
    }


SUPPLEMENTS.extend([
    researched_interval_row("infiniti-fx35-2003", "Infiniti", "FX35", 2003, "3.5L V6", "7,500 mi / 6 mo", "3,750 mi / 3 mo", "2003 Infiniti FX35 Engine Oil and Filter Maintenance Guide", "https://www.infinitiusa.com/maintenance-schedules/2003/fx35/components/engine-oil-and-filter/", "The model-year schedule lists the oil/filter interval; verify exact engine and market with the VIN-specific owner guide."),
    researched_interval_row("infiniti-g35-2003", "Infiniti", "G35", 2003, "3.5L V6", "7,500 mi / 6 mo", "3,750 mi / 3 mo", "2003 Infiniti G35 Engine Oil and Filter Maintenance Guide", "https://www.infinitiusa.com/maintenance-schedules/2003/g35/components/engine-oil-and-filter/", "The model-year schedule lists the oil/filter interval; verify exact engine and market with the VIN-specific owner guide."),
    researched_interval_row("infiniti-qx70-2017", "Infiniti", "QX70", 2017, "3.7L V6 / 5.0L V8 (engine variants not verified by interval page)", "5,000 mi / 6 mo", "5,000 mi / 6 mo", "2017 Infiniti QX70 Engine Oil and Filter Maintenance Guide", "https://www.infinitiusa.com/maintenance-schedules/2017/qx70/components/engine-oil-and-filter/", "The interval page lists standard and severe intervals; engine/trim fitment and market remain to be checked."),
    researched_interval_row("infiniti-q50-2023", "Infiniti", "Q50", 2023, "Engine variant not stated by interval source", "10,000 mi / 12 mo", "10,000 mi / 12 mo", "2023 Infiniti Q50 Engine Oil and Filter Maintenance Guide", "https://www.infinitiusa.com/maintenance-schedules/2023/q50/components/engine-oil-and-filter/", "The interval page lists standard and severe intervals; engine/trim fitment and market remain to be checked."),
    researched_interval_row("infiniti-qx56-2004", "Infiniti", "QX56", 2004, "5.6L V8 (engine code not verified by interval page)", "7,500 mi / 6 mo", "3,750 mi / 3 mo", "2004 Infiniti QX56 Engine Oil and Filter Maintenance Guide", "https://www.infinitiusa.com/maintenance-schedules/2004/qx56/components/engine-oil-and-filter/", "The model-year schedule lists the oil/filter interval; verify exact engine and market with the VIN-specific owner guide."),
    researched_interval_row("infiniti-qx80-2025", "Infiniti", "QX80", 2025, "3.5L Twin-Turbo V6 (engine variant not verified by interval page)", "10,000 mi / 12 mo", "10,000 mi / 12 mo", "2025 Infiniti QX80 Engine Oil and Filter Maintenance Guide", "https://www.infinitiusa.com/maintenance-schedules/2025/qx80/components/engine-oil-and-filter/", "The interval page lists standard and severe intervals; engine/trim fitment and market remain to be checked."),
    researched_interval_row("infiniti-qx60-2020", "Infiniti", "QX60", 2020, "3.5L V6 (engine code not verified by interval page)", "7,500 mi / 6 mo", "5,000 mi / 6 mo", "2020 Infiniti QX60 Engine Oil and Filter Maintenance Guide", "https://www.infinitiusa.com/maintenance-schedules/2020/qx60/components/engine-oil-and-filter/", "The model-year schedule lists standard and severe intervals; verify exact engine and market with the VIN-specific owner guide."),
    researched_interval_row("infiniti-qx60-2022", "Infiniti", "QX60", 2022, "3.5L V6 (engine code not verified by interval page)", "10,000 mi / 12 mo", "10,000 mi / 12 mo", "2022 Infiniti QX60 Engine Oil and Filter Maintenance Guide", "https://www.infinitiusa.com/maintenance-schedules/2022/qx60/components/engine-oil-and-filter/", "The model-year schedule lists standard and severe intervals; verify exact engine and market with the VIN-specific owner guide."),
    researched_interval_row("infiniti-qx60-2025", "Infiniti", "QX60", 2025, "3.5L V6 (engine code not verified by interval page)", "7,500 mi / 12 mo", "7,500 mi / 12 mo", "2025 Infiniti QX60 Engine Oil and Filter Maintenance Guide", "https://www.infinitiusa.com/maintenance-schedules/2025/qx60/components/engine-oil-and-filter/", "The model-year schedule lists standard and severe intervals; verify exact engine and market with the VIN-specific owner guide."),
    researched_interval_row("mazda-mazda3-2025", "Mazda", "Mazda3", 2025, "2.0L / 2.5L NA / 2.5L Turbo (engine variants not individually checked)", "16,000 km max / 12 mo", "8,000 km / 6 mo", "2025 Mazda3 Scheduled Maintenance", "https://www.mazdausa.com/static/manuals/2025/mazda3/contents/69031000.html", "The manual uses the vehicle status monitor up to 16,000 km/12 months and specifies 8,000 km/6 months for listed severe conditions; follow the monitor if it calls sooner."),
    researched_interval_row("mazda-cx5-2025", "Mazda", "CX-5", 2025, "2.5L NA / 2.5L Turbo (engine variants not individually checked)", "12,000 km max / 12 mo (flexible monitor)", "", "2025 Mazda CX-5 Scheduled Maintenance", "https://www.mazdausa.com/static/manuals/2025/cx-5/contents/07020201.html", "Flexible oil service is monitor-triggered with a maximum 12,000 km/12 months; do not treat the maximum as a fixed replacement target if the monitor calls sooner."),
    researched_interval_row("mazda-cx30-2025", "Mazda", "CX-30", 2025, "2.5L NA / 2.5L Turbo SkyActiv-G (engine variants not individually checked)", "16,000 km max / 12 mo", "8,000 km / 6 mo", "2025 Mazda CX-30 Scheduled Maintenance", "https://www.mazdausa.com/static/manuals/2025/cx-30/contents/69031000.html", "The manual uses the vehicle status monitor up to 16,000 km/12 months and specifies 8,000 km/6 months for listed severe conditions; follow the monitor if it calls sooner."),
    researched_interval_row("mazda-cx50-2025", "Mazda", "CX-50", 2025, "2.5L NA / 2.5L Turbo SkyActiv-G (engine variants not individually checked)", "16,000 km max / 12 mo", "8,000 km / 6 mo", "2025 Mazda CX-50 Scheduled Maintenance", "https://www.mazdausa.com/static/manuals/2025/cx-50/contents/69031000.html", "The manual uses the vehicle status monitor up to 16,000 km/12 months and specifies 8,000 km/6 months for listed severe conditions; follow the monitor if it calls sooner."),
    researched_interval_row("mazda-cx90-2025", "Mazda", "CX-90", 2025, "3.3L Turbo I6 / 2.5L PHEV (engine variants not individually checked)", "16,000 km max / 12 mo", "8,000 km / 6 mo", "2025 Mazda CX-90 Scheduled Maintenance", "https://www.mazdausa.com/static/manuals/2025/cx-90/contents/69031000.html", "The manual uses the vehicle status monitor up to 16,000 km/12 months and specifies 8,000 km/6 months for listed severe conditions; follow the monitor if it calls sooner."),
    researched_interval_row("lexus-lx600-2023", "Lexus", "LX 600", 2023, "3.4L Twin-Turbo V6 (engine code not verified by interval page)", "10,000 mi / 12 mo", "", "2023 Lexus LX 600 Warranty and Services Guide", "https://assets.sia.toyota.com/publications/en/omms-s/L-MMS-23X600/pdf/L-MMS-23X600.pdf", "The guide specifies oil/filter replacement at 10,000 miles/12 months or when the maintenance reminder appears; engine and market coverage should be checked for the exact vehicle."),
    researched_interval_row("lexus-rx500h-2024", "Lexus", "RX 500h", 2024, "2.4L Turbo Hybrid (engine code not verified by interval page)", "10,000 mi / 12 mo", "", "2024 Lexus RX 500h Warranty and Services Guide", "https://assets.sia.toyota.com/publications/en/omms-s/L-MMS-24RX500H/pdf/L-MMS-24RX500H.pdf", "The guide specifies oil/filter replacement at 10,000 miles/12 months or when the maintenance reminder appears; engine and market coverage should be checked for the exact vehicle."),
    researched_interval_row("subaru-crosstrek-2014", "Subaru", "Crosstrek", 2014, "2.0L FB20 / 2.0L Hybrid (engine variants not individually checked)", "12,000 km / 6 mo", "", "2014 Subaru Maintenance Recommendations", "https://contactus.subaru.com/knowledgebase/article/1002371/en-us", "The 2014 schedule lists engine oil/filter replacement every 7,500 miles/12,000 km or 6 months; engine-specific coverage remains to be checked."),
    researched_interval_row("subaru-forester-2014", "Subaru", "Forester", 2014, "2.5L FB25 / 2.0L FA20DIT Turbo (engine variants not individually checked)", "12,000 km / 6 mo", "", "2014 Subaru Maintenance Recommendations", "https://contactus.subaru.com/knowledgebase/article/1002371/en-us", "The 2014 schedule lists engine oil/filter replacement every 7,500 miles/12,000 km or 6 months; engine-specific coverage remains to be checked."),
    researched_interval_row("subaru-impreza-wrx-sti-2014", "Subaru", "Impreza / WRX / STI", 2014, "2.0L FB20 (Impreza) / 2.5L EJ255 (WRX) / 2.5L EJ257 (STI)", "12,000 km / 6 mo", "", "2014 Subaru Maintenance Recommendations", "https://contactus.subaru.com/knowledgebase/article/1002371/en-us", "The 2014 schedule lists engine oil/filter replacement every 7,500 miles/12,000 km or 6 months; engine-specific coverage remains to be checked."),
    researched_interval_row("subaru-outback-legacy-2014", "Subaru", "Outback / Legacy", 2014, "2.5L FB25 / 3.6L EZ36 H6 (engine variants not individually checked)", "12,000 km / 6 mo", "", "2014 Subaru Maintenance Recommendations", "https://contactus.subaru.com/knowledgebase/article/1002371/en-us", "The 2014 schedule lists engine oil/filter replacement every 7,500 miles/12,000 km or 6 months; engine-specific coverage remains to be checked."),
    researched_interval_row("subaru-tribeca-2014", "Subaru", "Tribeca", 2014, "3.6L EZ36 H6", "12,000 km / 6 mo", "", "2014 Subaru Maintenance Recommendations", "https://contactus.subaru.com/knowledgebase/article/1002371/en-us", "The 2014 schedule lists engine oil/filter replacement every 7,500 miles/12,000 km or 6 months; confirm the exact vehicle manual."),
    researched_interval_row("subaru-crosstrek-2017", "Subaru", "Crosstrek", 2017, "2.0L FB20", "6,000 mi / 6 mo", "", "2017 Subaru Maintenance Recommendations", "https://contactus.subaru.com/knowledgebase/article/1001834/en-us", "The 2017 schedule lists engine oil/filter replacement every 6,000 miles or 6 months; engine-specific coverage remains to be checked."),
    researched_interval_row("subaru-forester-2017", "Subaru", "Forester", 2017, "2.5L FB25 / 2.0L FA20DIT Turbo (engine variants not individually checked)", "6,000 mi / 6 mo", "", "2017 Subaru Maintenance Recommendations", "https://contactus.subaru.com/knowledgebase/article/1001834/en-us", "The 2017 schedule lists engine oil/filter replacement every 6,000 miles or 6 months; engine-specific coverage remains to be checked."),
    researched_interval_row("subaru-outback-legacy-2017", "Subaru", "Outback / Legacy", 2017, "2.5L FB25 / 3.6L EZ36 H6 (engine variants not individually checked)", "6,000 mi / 6 mo", "", "2017 Subaru Maintenance Recommendations", "https://contactus.subaru.com/knowledgebase/article/1001834/en-us", "The 2017 schedule lists engine oil/filter replacement every 6,000 miles or 6 months; engine-specific coverage remains to be checked."),
    researched_interval_row("subaru-wrx-sti-2017", "Subaru", "WRX / STI", 2017, "2.0L FA20F Turbo (WRX) / 2.5L EJ257 (STI)", "6,000 mi / 6 mo", "", "2017 Subaru Maintenance Recommendations", "https://contactus.subaru.com/knowledgebase/article/1001834/en-us", "The 2017 schedule lists engine oil/filter replacement every 6,000 miles or 6 months; engine-specific coverage remains to be checked."),
    researched_interval_row("subaru-crosstrek-2018", "Subaru", "Crosstrek", 2018, "2.0L FB20", "9,500 km / 6 mo", "", "2018 Subaru Maintenance Recommendations", "https://contactus.subaru.com/knowledgebase/article/1001833/en-us", "The 2018 schedule lists engine oil/filter replacement every 9,500 km or 6 months; engine-specific coverage remains to be checked."),
    researched_interval_row("subaru-forester-2018", "Subaru", "Forester", 2018, "2.5L FB25 / 2.0L FA20DIT Turbo (engine variants not individually checked)", "9,500 km / 6 mo", "", "2018 Subaru Maintenance Recommendations", "https://contactus.subaru.com/knowledgebase/article/1001833/en-us", "The 2018 schedule lists engine oil/filter replacement every 9,500 km or 6 months; engine-specific coverage remains to be checked."),
    researched_interval_row("subaru-outback-legacy-2018", "Subaru", "Outback / Legacy", 2018, "2.5L FB25 / 3.6L EZ36 H6 (engine variants not individually checked)", "9,500 km / 6 mo", "", "2018 Subaru Maintenance Recommendations", "https://contactus.subaru.com/knowledgebase/article/1001833/en-us", "The 2018 schedule lists engine oil/filter replacement every 9,500 km or 6 months; engine-specific coverage remains to be checked."),
    researched_interval_row("subaru-wrx-sti-2018", "Subaru", "WRX / STI", 2018, "2.0L FA20F Turbo (WRX) / 2.5L EJ257 (STI)", "9,500 km / 6 mo", "", "2018 Subaru Maintenance Recommendations", "https://contactus.subaru.com/knowledgebase/article/1001833/en-us", "The 2018 schedule lists engine oil/filter replacement every 9,500 km or 6 months; engine-specific coverage remains to be checked."),
    researched_interval_row("subaru-crosstrek-2019", "Subaru", "Crosstrek", 2019, "2.0L FB20 / 2.0L PHEV", "9,500 km / 6 mo", "", "2019 Subaru Maintenance Recommendations", "https://contactus.subaru.com/knowledgebase/article/1001832/en-us", "The 2019 schedule lists engine oil/filter replacement every 9,500 km or 6 months; engine-specific coverage remains to be checked."),
    researched_interval_row("subaru-forester-2019", "Subaru", "Forester", 2019, "2.5L FB25DI", "9,500 km / 6 mo", "", "2019 Subaru Maintenance Recommendations", "https://contactus.subaru.com/knowledgebase/article/1001832/en-us", "The 2019 schedule lists engine oil/filter replacement every 9,500 km or 6 months; engine-specific coverage remains to be checked."),
    researched_interval_row("subaru-outback-legacy-2019", "Subaru", "Outback / Legacy", 2019, "2.5L FB25 / 3.6L EZ36 H6 (engine variants not individually checked)", "9,500 km / 6 mo", "", "2019 Subaru Maintenance Recommendations", "https://contactus.subaru.com/knowledgebase/article/1001832/en-us", "The 2019 schedule lists engine oil/filter replacement every 9,500 km or 6 months; engine-specific coverage remains to be checked."),
    researched_interval_row("subaru-wrx-sti-2019", "Subaru", "WRX / STI", 2019, "2.0L FA20F Turbo (WRX) / 2.5L EJ257 (STI)", "9,500 km / 6 mo", "", "2019 Subaru Maintenance Recommendations", "https://contactus.subaru.com/knowledgebase/article/1001832/en-us", "The 2019 schedule lists engine oil/filter replacement every 9,500 km or 6 months; engine-specific coverage remains to be checked."),
    researched_interval_row("subaru-crosstrek-2020", "Subaru", "Crosstrek", 2020, "2.0L FB20 / 2.0L PHEV", "9,500 km / 6 mo", "", "2020 Subaru Maintenance Recommendations", "https://contactus.subaru.com/knowledgebase/article/1002183/en-us", "The 2020 schedule lists engine oil/filter replacement every 9,500 km or 6 months; engine-specific coverage remains to be checked."),
    researched_interval_row("subaru-forester-2020", "Subaru", "Forester", 2020, "2.5L FB25DI", "9,500 km / 6 mo", "", "2020 Subaru Maintenance Recommendations", "https://contactus.subaru.com/knowledgebase/article/1002183/en-us", "The 2020 schedule lists engine oil/filter replacement every 9,500 km or 6 months; engine-specific coverage remains to be checked."),
    researched_interval_row("subaru-outback-legacy-2020", "Subaru", "Outback / Legacy", 2020, "2.5L FB25 / 2.4L FA24T Turbo (engine variants not individually checked)", "9,500 km / 6 mo", "", "2020 Subaru Maintenance Recommendations", "https://contactus.subaru.com/knowledgebase/article/1002183/en-us", "The 2020 schedule lists engine oil/filter replacement every 9,500 km or 6 months; engine-specific coverage remains to be checked."),
    researched_interval_row("subaru-wrx-sti-2020", "Subaru", "WRX / STI", 2020, "2.0L FA20F Turbo (WRX) / 2.5L EJ257 (STI)", "9,500 km / 6 mo", "", "2020 Subaru Maintenance Recommendations", "https://contactus.subaru.com/knowledgebase/article/1002183/en-us", "The 2020 schedule lists engine oil/filter replacement every 9,500 km or 6 months; engine-specific coverage remains to be checked."),
])


def main() -> None:
    data = json.loads(CATALOG.read_text(encoding="utf-8"))
    records = [r for r in data["records"] if not str(r.get("catalogId", "")).startswith("official-supplement:")]
    records.extend(SUPPLEMENTS)
    preserve_ids_and_metric_intervals(records)
    data["records"] = records
    data["recordCount"] = len(records)
    data["distanceUnitSystem"] = "METRIC"
    data["supplementalResearchDate"] = RESEARCH_DATE
    data["officialSupplementCount"] = len(SUPPLEMENTS)
    data["warning"] = (
        "The 476 original rows from the supplied CSV are AI-generated and are not manufacturer-verified; "
        "all structured catalog distances are stored in kilometres and US-quart capacities in litres, with source wording retained separately. "
        "The source reliability ratings are not evidence. " + str(len(SUPPLEMENTS)) + " exact-year OEM interval supplements are "
        "appended and separately labeled OFFICIAL_INTERVAL_RESEARCHED. Their intervals remain excluded from "
        "scheduling until the user reviews the cited guide and explicitly confirms the selected entry. Engine, "
        "trim, oil grade, approval, capacity, and filter data may remain unverified. Confirm the exact vehicle "
        "market and owner's guide before relying on any recommendation."
    )
    CATALOG.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Wrote {len(records)} records ({len(records) - len(SUPPLEMENTS)} original AI rows + {len(SUPPLEMENTS)} official interval supplements).")


if __name__ == "__main__":
    main()
