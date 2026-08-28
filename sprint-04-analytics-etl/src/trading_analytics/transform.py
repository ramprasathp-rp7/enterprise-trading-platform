# Transform: takes data, returns data. Parsing, typing, cleaning, deriving.
'''
  1. Duplicate date, conflicting values  -> quarantined (BOTH copies; which
     one is right can't be decided here, so neither is trusted).
  2. Missing required field (close)      -> quarantined ("missing field").
  3. Non-numeric value ("n/a")           -> quarantined ("not a number").
  4. High below low                      -> quarantined. This is the one the
     README calls out by name as never acceptable to load; test named
     test_rejects_a_high_below_a_low asserts it never reaches the valid frame.
  5. Negative volume                     -> quarantined ("negative volume").
  6. Non-ISO date (09/07/2026)           -> quarantined ("unparseable date").
'''

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date

import pandas as pd

REQUIRED_NUMERIC_FIELDS = ("open", "high", "low", "close", "adjclose")


@dataclass
class QuarantinedCandle:
    symbol: str
    raw_date: object  # whatever was in the "date" field — may not even be a string
    reason: str
    raw_candle: dict


@dataclass
class TransformResult:
    valid: pd.DataFrame
    quarantined: list[QuarantinedCandle] = field(default_factory=list)


def _quarantine(symbol: str, candle: dict, reason: str) -> QuarantinedCandle:
    return QuarantinedCandle(symbol=symbol, raw_date=candle.get("date"), reason=reason, raw_candle=candle)


def _parse_iso_date(value) -> date | None:
    if not isinstance(value, str):
        return None
    try:
        return date.fromisoformat(value)
    except ValueError:
        return None


def _to_float(value) -> float | None:
    if value is None or isinstance(value, bool):
        return None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def transform_candles(raw: dict) -> TransformResult:
    data = raw.get("data", {})
    symbol = data.get("symbol", "")
    currency = data.get("currency", "")
    candles = data.get("candles", []) or []

    quarantined: list[QuarantinedCandle] = []

    # Defect 1
    date_counts: dict[object, int] = {}
    for c in candles:
        date_counts[c.get("date")] = date_counts.get(c.get("date"), 0) + 1
    duplicated_dates = {d for d, n in date_counts.items() if n > 1}

    rows = []
    for c in candles:
        raw_date = c.get("date")

        if raw_date in duplicated_dates:
            quarantined.append(_quarantine(symbol, c, "duplicate date with conflicting values"))
            continue

        parsed_date = _parse_iso_date(raw_date)
        if parsed_date is None:
            quarantined.append(_quarantine(symbol, c, "unparseable date (not ISO 8601)"))
            continue
        
        # Defect 2,3
        
        numeric_values: dict[str, float] = {}
        bad_field = None
        missing_field = None
        for field_name in REQUIRED_NUMERIC_FIELDS:
            if field_name not in c:
                missing_field = field_name
                break
            v = _to_float(c[field_name])
            if v is None:
                bad_field = field_name
                break
            numeric_values[field_name] = v

        if missing_field is not None:
            quarantined.append(_quarantine(symbol, c, f"missing required field: {missing_field}"))
            continue
        if bad_field is not None:
            quarantined.append(_quarantine(symbol, c, f"non-numeric value in field: {bad_field}"))
            continue

        # Defect 4
        if numeric_values["high"] < numeric_values["low"]:
            quarantined.append(_quarantine(symbol, c, "high is below low"))
            continue

        # Defect 5
        volume_raw = c.get("volume")
        volume = None
        if volume_raw is not None:
            v = _to_float(volume_raw)
            if v is None:
                quarantined.append(_quarantine(symbol, c, "non-numeric value in field: volume"))
                continue
            if v < 0:
                quarantined.append(_quarantine(symbol, c, "negative volume"))
                continue
            volume = int(v)

        rows.append(
            {
                "symbol": symbol,
                "currency": currency,
                "date": pd.Timestamp(parsed_date),
                "open": numeric_values["open"],
                "high": numeric_values["high"],
                "low": numeric_values["low"],
                "close": numeric_values["close"],
                "adjclose": numeric_values["adjclose"],
                "volume": volume,
                "synthetic": bool(c.get("synthetic", False)),
            }
        )

    # Defect 6
    columns = ["symbol", "currency", "date", "open", "high", "low", "close", "adjclose", "volume", "synthetic"]
    valid = pd.DataFrame(rows, columns=columns)
    valid["date"] = pd.to_datetime(valid["date"])
    valid["volume"] = valid["volume"].astype("Int64")
    valid = valid.sort_values("date").reset_index(drop=True)

    return TransformResult(valid=valid, quarantined=quarantined)
