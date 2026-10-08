from datetime import date, datetime, timezone
from decimal import Decimal

import pytest

from trading_analytics_etl import transform


def _good_order(**overrides):
    order = {
        "order_id": "11111111-1111-1111-1111-111111111111",
        "account_id_str": "ACC-000001",
        "symbol": "INFY.NS",
        "side": "BUY",
        "quantity": 10,
        "price": "1800.00",
        "executed_price": "1805.50",
        "status": "FILLED",
        "created_on": datetime(2026, 9, 15, 10, 0, tzinfo=timezone.utc),
    }
    order.update(overrides)
    return order


def _maps():
    return {
        "instrument_key_map": {"INFY.NS": 1},
        "account_key_map": {"ACC-000001": 1},
        "known_date_keys": {20260915},
    }


def test_valid_order_builds_fact_row():
    maps = _maps()
    row = transform.validate_and_build_fact_row(
        _good_order(), maps["instrument_key_map"], maps["account_key_map"],
        maps["known_date_keys"], trade_key=1, loaded_at=datetime.now(timezone.utc),
    )
    assert row["trade_value"] == Decimal("10") * Decimal("1805.50")
    assert row["date_key"] == 20260915


def test_trade_value_uses_price_when_not_filled():
    maps = _maps()
    order = _good_order(status="REJECTED", executed_price=None)
    row = transform.validate_and_build_fact_row(
        order, maps["instrument_key_map"], maps["account_key_map"],
        maps["known_date_keys"], trade_key=1, loaded_at=datetime.now(timezone.utc),
    )
    assert row["trade_value"] == Decimal("10") * Decimal("1800.00")
    assert row["executed_price"] is None


@pytest.mark.parametrize(
    "overrides,expected_reason_code",
    [
        ({"quantity": -50}, "QTY_NOT_POSITIVE"),
        ({"quantity": 0}, "QTY_NOT_POSITIVE"),
        ({"quantity": None}, "QTY_NOT_POSITIVE"),
        ({"price": None}, "PRICE_NULL"),
        ({"price": "not-a-number"}, "PRICE_NOT_NUMERIC"),
        ({"price": "-5.00"}, "PRICE_NOT_POSITIVE"),
        ({"side": "HOLD"}, "INVALID_SIDE"),
        ({"status": "PENDING"}, "INVALID_STATUS"),
        ({"symbol": "DOES.NOT.EXIST"}, "INSTRUMENT_NOT_RESOLVED"),
        ({"account_id_str": "ACC-999999"}, "ACCOUNT_NOT_RESOLVED"),
        ({"created_on": "not-a-datetime"}, "CREATED_ON_INVALID"),
    ],
)
def test_invalid_rows_are_rejected_with_reason(overrides, expected_reason_code):
    """Covers required test paths: nulls, type mismatches, and dead-letter
    reasons all coming back as QualityError rather than an unhandled crash."""
    maps = _maps()
    order = _good_order(**overrides)
    with pytest.raises(transform.QualityError) as exc_info:
        transform.validate_and_build_fact_row(
            order, maps["instrument_key_map"], maps["account_key_map"],
            maps["known_date_keys"], trade_key=1, loaded_at=datetime.now(timezone.utc),
        )
    assert exc_info.value.reason_code == expected_reason_code
    assert exc_info.value.source_order_id == order["order_id"]


def test_date_not_in_dim_date_is_rejected():
    maps = _maps()
    order = _good_order(created_on=datetime(1999, 1, 1, tzinfo=timezone.utc))
    with pytest.raises(transform.QualityError) as exc_info:
        transform.validate_and_build_fact_row(
            order, maps["instrument_key_map"], maps["account_key_map"],
            maps["known_date_keys"], trade_key=1, loaded_at=datetime.now(timezone.utc),
        )
    assert exc_info.value.reason_code == "DATE_NOT_RESOLVED"


def test_derive_exchange():
    assert transform.derive_exchange("INFY.NS") == "NSE"
    assert transform.derive_exchange("RELI.BO") == "BSE"
    assert transform.derive_exchange("FX:EURUSD") == "FX"
    assert transform.derive_exchange("X:BTCUSD") == "CRYPTO"
    assert transform.derive_exchange("AAPL") == "US"


def test_diff_accounts_skips_unchanged_versions_new_and_inserts_first_time():
    now = datetime(2026, 9, 29, tzinfo=timezone.utc)
    raw = [
        {"account_id": "ACC-1", "holder_name": "Jane", "status": "ACTIVE", "source_id": 1},
        {"account_id": "ACC-2", "holder_name": "Bob", "status": "SUSPENDED", "source_id": 2},
        {"account_id": "ACC-3", "holder_name": "New Guy", "status": "ACTIVE", "source_id": 3},
    ]
    current = {
        "ACC-1": {"holder_name": "Jane", "status": "ACTIVE", "account_key": 10},  # unchanged
        "ACC-2": {"holder_name": "Bob", "status": "ACTIVE", "account_key": 20},   # status changed
        # ACC-3 has no current version at all
    }
    result = transform.diff_accounts(raw, current, now.date(), now)

    assert result.to_close == ["ACC-2"]
    inserted_ids = {r["account_id"] for r in result.to_insert}
    assert inserted_ids == {"ACC-2", "ACC-3"}  # ACC-1 unchanged -> no write at all


# --- multi-currency: trade_value stays in the instrument's currency, trade_value_account is USD ---

def _build(order):
    maps = _maps()
    return transform.validate_and_build_fact_row(
        order, maps["instrument_key_map"], maps["account_key_map"],
        maps["known_date_keys"], trade_key=1, loaded_at=datetime.now(timezone.utc),
    )


def test_filled_order_converts_at_the_fill_rate():
    # INR 18,055.00 placed at 0.0125 USD per INR, filled when the rate was 0.0128
    row = _build(_good_order(placement_fx_rate="0.0125", fill_fx_rate="0.0128"))
    assert row["trade_value"] == Decimal("18055.00")
    assert row["fx_rate"] == Decimal("0.0128")
    assert row["trade_value_account"] == Decimal("231.10")


def test_unfilled_order_converts_at_the_placement_rate():
    row = _build(_good_order(status="CANCELLED", executed_price=None, placement_fx_rate="0.0125", fill_fx_rate=None))
    assert row["fx_rate"] == Decimal("0.0125")
    assert row["trade_value_account"] == Decimal("225.00")


def test_orders_without_rates_convert_at_one():
    row = _build(_good_order())
    assert row["fx_rate"] == Decimal("1")
    assert row["trade_value_account"] == row["trade_value"]


def test_non_positive_fx_rate_is_dead_lettered():
    with pytest.raises(transform.QualityError) as err:
        _build(_good_order(fill_fx_rate="0"))
    assert err.value.reason_code == "FX_RATE_NOT_POSITIVE"
