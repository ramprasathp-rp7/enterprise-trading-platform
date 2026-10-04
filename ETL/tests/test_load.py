from datetime import datetime, timezone

from trading_analytics_etl import load, transform


def _validate(order, maps, trade_key, now):
    return transform.validate_and_build_fact_row(
        order, maps["instrument_key_map"], maps["account_key_map"],
        maps["known_date_keys"], trade_key=trade_key, loaded_at=now,
    )


def test_incremental_load_populates_fact_trades(duck_con, seeded_dims, now):
    """Required path: an incremental load populates FACT_TRADES."""
    order = {
        "order_id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
        "account_id_str": "ACC-000001",
        "symbol": "INFY.NS",
        "side": "BUY",
        "quantity": 5,
        "price": "100.00",
        "executed_price": "101.00",
        "status": "FILLED",
        "created_on": datetime(2026, 9, 15, 10, 0, tzinfo=timezone.utc),
    }
    row = _validate(order, seeded_dims, trade_key=1, now=now)
    loaded = load.load_fact_trades(duck_con, [row])

    assert loaded == 1
    (count,) = duck_con.execute("SELECT COUNT(*) FROM fact_trades").fetchone()
    assert count == 1


def test_second_load_with_no_new_data_adds_no_rows(duck_con, seeded_dims, now):
    """Required path: a second load with no new data adds no rows."""
    order = {
        "order_id": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
        "account_id_str": "ACC-000001",
        "symbol": "INFY.NS",
        "side": "SELL",
        "quantity": 3,
        "price": "50.00",
        "executed_price": "49.50",
        "status": "FILLED",
        "created_on": datetime(2026, 9, 16, 10, 0, tzinfo=timezone.utc),
    }
    row = _validate(order, seeded_dims, trade_key=1, now=now)
    load.load_fact_trades(duck_con, [row])

    # Re-run the load with the exact same extracted row (simulating a
    # watermark boundary re-read, or a full rerun with nothing new).
    row_again = _validate(order, seeded_dims, trade_key=99, now=now)
    load.load_fact_trades(duck_con, [row_again])

    (count,) = duck_con.execute("SELECT COUNT(*) FROM fact_trades").fetchone()
    assert count == 1  # merged on source_order_id, not duplicated


def test_invalid_row_is_dead_lettered_and_valid_rows_still_load(duck_con, seeded_dims, now):
    """Required path: an invalid row is dead-lettered with its reason and
    the load continues (the good row alongside it still loads)."""
    good_order = {
        "order_id": "cccccccc-cccc-cccc-cccc-cccccccccccc",
        "account_id_str": "ACC-000001",
        "symbol": "INFY.NS",
        "side": "BUY",
        "quantity": 1,
        "price": "10.00",
        "executed_price": "10.00",
        "status": "FILLED",
        "created_on": datetime(2026, 9, 17, 10, 0, tzinfo=timezone.utc),
    }
    bad_order = {
        "order_id": "dddddddd-dddd-dddd-dddd-dddddddddddd",
        "account_id_str": "ACC-000001",
        "symbol": "INFY.NS",
        "side": "BUY",
        "quantity": -50,  # invalid
        "price": "10.00",
        "executed_price": None,
        "status": "NEW",
        "created_on": datetime(2026, 9, 17, 10, 0, tzinfo=timezone.utc),
    }

    valid_rows = []
    failures = []
    trade_key = 1
    for order in (good_order, bad_order):
        try:
            valid_rows.append(_validate(order, seeded_dims, trade_key, now))
            trade_key += 1
        except transform.QualityError as e:
            failures.append(
                {
                    "source_order_id": e.source_order_id,
                    "reason_code": e.reason_code,
                    "reason": e.message,
                    "payload": order,
                }
            )

    load.load_fact_trades(duck_con, valid_rows)
    load.load_dead_letters(duck_con, failures, batch_id="test-batch", seen_at=now)

    (fact_count,) = duck_con.execute("SELECT COUNT(*) FROM fact_trades").fetchone()
    assert fact_count == 1

    dlq_row = duck_con.execute(
        "SELECT source_order_id, reason_code, batch_id FROM dead_letter_trades"
    ).fetchone()
    assert dlq_row == (bad_order["order_id"], "QTY_NOT_POSITIVE", "test-batch")


def test_dead_letter_upsert_increments_attempts_instead_of_duplicating(duck_con, now):
    failure = {
        "source_order_id": "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee",
        "reason_code": "INSTRUMENT_NOT_RESOLVED",
        "reason": "instrument not found",
        "payload": {"created_on": now.isoformat()},
    }
    load.load_dead_letters(duck_con, [failure], batch_id="batch-1", seen_at=now)
    load.load_dead_letters(duck_con, [failure], batch_id="batch-2", seen_at=now)

    (count,) = duck_con.execute("SELECT COUNT(*) FROM dead_letter_trades").fetchone()
    assert count == 1
    (attempts, batch_id) = duck_con.execute(
        "SELECT attempts, batch_id FROM dead_letter_trades"
    ).fetchone()
    assert attempts == 2
    assert batch_id == "batch-2"


def test_dim_instrument_upsert_overwrites_in_place(duck_con, now):
    v1 = transform.transform_instrument(
        {"symbol": "AAPL", "name": "Apple", "asset_class": "EQUITY",
         "currency": "USD", "tradable": True}, now)
    load.load_dim_instrument(duck_con, [v1], next_key_start=load.get_next_instrument_key(duck_con))

    v2 = transform.transform_instrument(
        {"symbol": "AAPL", "name": "Apple Inc.", "asset_class": "EQUITY",
         "currency": "USD", "tradable": False}, now)
    load.load_dim_instrument(duck_con, [v2], next_key_start=load.get_next_instrument_key(duck_con))

    (count,) = duck_con.execute("SELECT COUNT(*) FROM dim_instrument").fetchone()
    assert count == 1  # same key, overwritten, not a second row
    (name, tradable) = duck_con.execute(
        "SELECT name, tradable FROM dim_instrument WHERE symbol = 'AAPL'"
    ).fetchone()
    assert name == "Apple Inc."
    assert tradable is False


def test_dim_account_type2_closes_old_version_on_change(duck_con, now):
    load.insert_new_account_versions(
        duck_con,
        [{"account_id": "ACC-X", "holder_name": "Original", "status": "ACTIVE",
          "effective_date": now.date(), "end_date": None, "is_current": True,
          "source_id": 42, "loaded_at": now}],
        next_key_start=1,
    )
    current = load.get_current_accounts_by_account_id(duck_con)
    diff = transform.diff_accounts(
        [{"account_id": "ACC-X", "holder_name": "Original", "status": "SUSPENDED", "source_id": 42}],
        current, now.date(), now,
    )
    load.close_current_account_versions(duck_con, diff.to_close, now.date())
    load.insert_new_account_versions(duck_con, diff.to_insert, load.get_next_account_key(duck_con))

    rows = duck_con.execute(
        "SELECT status, is_current, end_date FROM dim_account WHERE account_id = 'ACC-X' ORDER BY account_key"
    ).fetchall()
    assert len(rows) == 2
    assert rows[0] == ("ACTIVE", False, now.date())
    assert rows[1][0] == "SUSPENDED"
    assert rows[1][1] is True
