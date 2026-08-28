"""Transform tests against the provided fixtures — no network, ever.

Covers the "Unit Test Execution Paths" named in the story plus one assertion
per documented defect in fixtures/README.md, so every one of the six is
individually proven, not just implied by the malformed fixture passing as a
whole.
"""

import pandas as pd

from trading_analytics.transform import transform_candles


# valid input transforms to expected output

def test_valid_input_transforms_to_expected_output(reliance_candles_raw):
    result = transform_candles(reliance_candles_raw)

    assert len(result.quarantined) == 0
    assert len(result.valid) == 9  # nine trading days in the fixture
    assert list(result.valid.columns) == [
        "symbol", "currency", "date", "open", "high", "low", "close", "adjclose", "volume", "synthetic",
    ]
    assert result.valid["symbol"].eq("RELIANCE.NS").all()
    assert pd.api.types.is_datetime64_any_dtype(result.valid["date"])
    # Sorted by date, oldest first.
    assert result.valid["date"].is_monotonic_increasing


def test_calendar_gap_is_not_a_defect(reliance_candles_raw):
    result = transform_candles(reliance_candles_raw)
    dates = set(result.valid["date"].dt.strftime("%Y-%m-%d"))
    assert "2026-07-08" not in dates
    assert len(result.quarantined) == 0


# legitimate live-API behaviour that must NOT be quarantined

def test_null_volume_is_valid_not_quarantined(infy_candles_raw):
    result = transform_candles(infy_candles_raw)
    assert len(result.quarantined) == 0
    row = result.valid.loc[result.valid["date"] == "2026-07-06"]
    assert len(row) == 1
    assert pd.isna(row.iloc[0]["volume"])


def test_synthetic_flag_is_valid_and_preserved(infy_candles_raw):
    result = transform_candles(infy_candles_raw)
    row = result.valid.loc[result.valid["date"] == "2026-07-08"]
    assert len(row) == 1
    assert bool(row.iloc[0]["synthetic"]) is True


# empty dataset handled without error

def test_empty_dataset_handled_without_error():
    empty_envelope = {"data": {"symbol": "ACME", "currency": "USD", "candles": []}, "meta": {}}
    result = transform_candles(empty_envelope)
    assert result.valid.empty
    assert result.quarantined == []
    assert list(result.valid.columns) == [
        "symbol", "currency", "date", "open", "high", "low", "close", "adjclose", "volume", "synthetic",
    ]


# the six documented defects, each asserted individually 

def _quarantine_reasons(result):
    return [q.reason for q in result.quarantined]


def test_duplicate_date_is_quarantined_not_guessed(malformed_candles_raw):
    result = transform_candles(malformed_candles_raw)
    dup_reasons = [q for q in result.quarantined if q.raw_date == "2026-07-01"]
    # BOTH copies quarantined — neither close is trusted over the other.
    assert len(dup_reasons) == 2
    assert all("duplicate date" in q.reason for q in dup_reasons)
    assert "2026-07-01" not in set(result.valid["date"].dt.strftime("%Y-%m-%d"))


def test_missing_required_field_is_rejected(malformed_candles_raw):
    result = transform_candles(malformed_candles_raw)
    match = [q for q in result.quarantined if q.raw_date == "2026-07-02"]
    assert len(match) == 1
    assert "missing required field: close" in match[0].reason
    assert "2026-07-02" not in set(result.valid["date"].dt.strftime("%Y-%m-%d"))


def test_out_of_range_value_is_flagged(malformed_candles_raw):
    result = transform_candles(malformed_candles_raw)
    match = [q for q in result.quarantined if q.raw_date == "2026-07-06"]
    assert len(match) == 1
    assert "non-numeric value in field: open" in match[0].reason


def test_rejects_a_high_below_a_low(malformed_candles_raw):
    result = transform_candles(malformed_candles_raw)
    match = [q for q in result.quarantined if q.raw_date == "2026-07-07"]
    assert len(match) == 1
    assert "high is below low" in match[0].reason
    assert "2026-07-07" not in set(result.valid["date"].dt.strftime("%Y-%m-%d"))


def test_negative_volume_is_quarantined(malformed_candles_raw):
    result = transform_candles(malformed_candles_raw)
    match = [q for q in result.quarantined if q.raw_date == "2026-07-08"]
    assert len(match) == 1
    assert "negative volume" in match[0].reason


def test_non_iso_date_is_quarantined(malformed_candles_raw):
    result = transform_candles(malformed_candles_raw)
    match = [q for q in result.quarantined if q.raw_date == "09/07/2026"]
    assert len(match) == 1
    assert "unparseable date" in match[0].reason


def test_malformed_fixture_yields_zero_valid_rows(malformed_candles_raw):
    result = transform_candles(malformed_candles_raw)
    assert result.valid.empty
    assert len(result.quarantined) == 7
