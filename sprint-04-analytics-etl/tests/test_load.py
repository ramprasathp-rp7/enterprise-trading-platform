import duckdb
import pandas as pd

from trading_analytics.load import load_candles
from trading_analytics.transform import transform_candles


def test_load_writes_expected_row_count(reliance_candles_raw, tmp_path):
    valid = transform_candles(reliance_candles_raw).valid
    db_path = tmp_path / "test.duckdb"

    written = load_candles(valid, db_path=db_path)

    assert written == 9
    con = duckdb.connect(str(db_path))
    assert con.execute("SELECT count(*) FROM candles").fetchone()[0] == 9
    con.close()


def test_load_is_idempotent_on_rerun(reliance_candles_raw, tmp_path):
    valid = transform_candles(reliance_candles_raw).valid
    db_path = tmp_path / "test.duckdb"

    load_candles(valid, db_path=db_path)
    load_candles(valid, db_path=db_path)  # same data, run twice

    con = duckdb.connect(str(db_path))
    assert con.execute("SELECT count(*) FROM candles").fetchone()[0] == 9
    con.close()


def test_load_updates_changed_row_in_place(reliance_candles_raw, tmp_path):
    valid = transform_candles(reliance_candles_raw).valid
    db_path = tmp_path / "test.duckdb"
    load_candles(valid, db_path=db_path)

    tweaked = valid.copy()
    tweaked.loc[tweaked["date"] == "2026-07-01", "close"] = 9999.99
    load_candles(tweaked, db_path=db_path)

    con = duckdb.connect(str(db_path))
    row_count = con.execute("SELECT count(*) FROM candles").fetchone()[0]
    close = con.execute(
        "SELECT close FROM candles WHERE symbol='RELIANCE.NS' AND date='2026-07-01'"
    ).fetchone()[0]
    con.close()

    assert row_count == 9  # no duplicate row from the re-load
    assert close == 9999.99  # the existing row was replaced, not left stale


def test_load_multiple_symbols_coexist(reliance_candles_raw, infy_candles_raw, tmp_path):
    db_path = tmp_path / "test.duckdb"
    load_candles(transform_candles(reliance_candles_raw).valid, db_path=db_path)
    load_candles(transform_candles(infy_candles_raw).valid, db_path=db_path)

    con = duckdb.connect(str(db_path))
    by_symbol = dict(con.execute("SELECT symbol, count(*) FROM candles GROUP BY symbol").fetchall())
    con.close()

    assert by_symbol == {"RELIANCE.NS": 9, "INFY.NS": 8}


def test_load_empty_frame_writes_nothing(reliance_candles_raw, tmp_path):
    valid = transform_candles(reliance_candles_raw).valid
    empty = pd.DataFrame(columns=valid.columns)
    db_path = tmp_path / "test.duckdb"

    written = load_candles(empty, db_path=db_path)

    assert written == 0
    # No table was even created — nothing to assert further, and no error raised.
