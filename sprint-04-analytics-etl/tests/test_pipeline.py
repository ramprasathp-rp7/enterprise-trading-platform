import duckdb
import pytest

from trading_analytics.errors import (
    FauxnanceConnectionError,
    QuotaExhaustedError,
    SymbolRequestError,
)
from trading_analytics.pipeline import run


def test_run_happy_path_multiple_symbols(
    monkeypatch, reliance_candles_raw, infy_candles_raw, tmp_path
):
    raw_by_symbol = {"RELIANCE.NS": reliance_candles_raw, "INFY.NS": infy_candles_raw}

    def fake_extract(symbol, start, end, use_cache=True):
        return raw_by_symbol[symbol]

    monkeypatch.setattr("trading_analytics.pipeline.extract_candles", fake_extract)

    db_path = tmp_path / "test.duckdb"
    result = run(["RELIANCE.NS", "INFY.NS"], "2026-07-01", "2026-07-31", db_path=db_path)

    assert result.stopped_early is False
    by_symbol = {r.symbol: r for r in result.results}
    assert by_symbol["RELIANCE.NS"].valid_rows == 9
    assert by_symbol["RELIANCE.NS"].loaded_rows == 9
    assert by_symbol["INFY.NS"].valid_rows == 8
    assert by_symbol["INFY.NS"].loaded_rows == 8

    con = duckdb.connect(str(db_path))
    assert con.execute("SELECT count(*) FROM candles").fetchone()[0] == 17
    con.close()


def test_run_stops_immediately_on_quota_exhausted(monkeypatch, tmp_path):
    calls = []

    def fake_extract(symbol, start, end, use_cache=True):
        calls.append(symbol)
        if symbol == "AAPL":
            raise QuotaExhaustedError(retry_after_seconds=1800)
        raise AssertionError(f"extract should never be called for {symbol} after AAPL trips quota")

    monkeypatch.setattr("trading_analytics.pipeline.extract_candles", fake_extract)

    result = run(["AAPL", "ACME", "FX:EURUSD"], "2026-07-01", "2026-07-31", db_path=tmp_path / "t.duckdb")

    assert result.stopped_early is True
    assert "quota exhausted" in result.stop_reason.lower() or "Fauxnance daily quota exhausted" in result.stop_reason
    assert calls == ["AAPL"]  # ACME and FX:EURUSD never attempted
    assert len(result.results) == 1
    assert result.results[0].error is not None


def test_run_continues_after_symbol_request_error(monkeypatch, reliance_candles_raw, tmp_path):
    def fake_extract(symbol, start, end, use_cache=True):
        if symbol == "NOPE":
            raise SymbolRequestError("NOPE", 404, "unknown symbol")
        return reliance_candles_raw

    monkeypatch.setattr("trading_analytics.pipeline.extract_candles", fake_extract)

    result = run(["NOPE", "RELIANCE.NS"], "2026-07-01", "2026-07-31", db_path=tmp_path / "t.duckdb")

    assert result.stopped_early is False
    by_symbol = {r.symbol: r for r in result.results}
    assert by_symbol["NOPE"].error is not None
    assert by_symbol["NOPE"].loaded_rows == 0
    assert by_symbol["RELIANCE.NS"].loaded_rows == 9  # continued after the failure


def test_run_continues_after_connection_error(monkeypatch, reliance_candles_raw, tmp_path):
    def fake_extract(symbol, start, end, use_cache=True):
        if symbol == "FLAKY":
            raise FauxnanceConnectionError("no response after 3 attempts")
        return reliance_candles_raw

    monkeypatch.setattr("trading_analytics.pipeline.extract_candles", fake_extract)

    result = run(["FLAKY", "RELIANCE.NS"], "2026-07-01", "2026-07-31", db_path=tmp_path / "t.duckdb")

    assert result.stopped_early is False
    by_symbol = {r.symbol: r for r in result.results}
    assert by_symbol["FLAKY"].error is not None
    assert by_symbol["RELIANCE.NS"].loaded_rows == 9


def test_run_records_quarantine_counts_without_failing(monkeypatch, malformed_candles_raw, tmp_path):
    def fake_extract(symbol, start, end, use_cache=True):
        return malformed_candles_raw

    monkeypatch.setattr("trading_analytics.pipeline.extract_candles", fake_extract)

    result = run(["TATASTEEL.BO"], "2026-07-01", "2026-07-31", db_path=tmp_path / "t.duckdb")

    assert result.stopped_early is False
    r = result.results[0]
    assert r.error is None  # quarantining bad rows is not a symbol-level failure
    assert r.valid_rows == 0
    assert r.quarantined_rows == 7
    assert r.loaded_rows == 0
