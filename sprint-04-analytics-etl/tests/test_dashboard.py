import re

from trading_analytics.dashboard import CHART_FILENAMES, build_dashboard
from trading_analytics.load import load_candles
from trading_analytics.transform import transform_candles


def _build(reliance_candles_raw, infy_candles_raw, tmp_path):
    db_path = tmp_path / "test.duckdb"
    load_candles(transform_candles(reliance_candles_raw).valid, db_path=db_path)
    load_candles(transform_candles(infy_candles_raw).valid, db_path=db_path)

    output_dir = tmp_path / "charts"
    written = build_dashboard(db_path=db_path, output_dir=output_dir)
    return written


def test_writes_three_separate_files(reliance_candles_raw, infy_candles_raw, tmp_path):
    written = _build(reliance_candles_raw, infy_candles_raw, tmp_path)
    assert set(written.keys()) == {"closing-price-trend", "daily-volume", "daily-range"}
    for key, path in written.items():
        assert path.exists(), f"{key} artefact was not written to {path}"
        assert path.name == CHART_FILENAMES[key]
        assert path.stat().st_size > 100_000 


def test_output_ships_the_height_css(reliance_candles_raw, infy_candles_raw, tmp_path):
    written = _build(reliance_candles_raw, infy_candles_raw, tmp_path)
    for path in written.values():
        html = path.read_text(encoding="utf-8")
        assert re.search(r"html\s*,\s*body\s*\{\s*height:\s*100%", html), (
            f"{path} is missing the html/body height:100% rule that makes the chart's "
            "own height:100% div resolve to a real size instead of collapsing to zero"
        )


def test_no_external_script_tags(reliance_candles_raw, infy_candles_raw, tmp_path):
    written = _build(reliance_candles_raw, infy_candles_raw, tmp_path)
    for path in written.values():
        html = path.read_text(encoding="utf-8")
        external_scripts = re.findall(r'<script[^>]*src="https?://[^"]*"', html)
        assert external_scripts == [], f"{path} has external script tag(s): {external_scripts}"


def test_both_symbols_present_in_each_chart(reliance_candles_raw, infy_candles_raw, tmp_path):
    written = _build(reliance_candles_raw, infy_candles_raw, tmp_path)
    for key, path in written.items():
        html = path.read_text(encoding="utf-8")
        assert "RELIANCE.NS" in html, f"{key} is missing RELIANCE.NS"
        assert "INFY.NS" in html, f"{key} is missing INFY.NS"


def test_raises_a_clear_error_on_empty_candles_table(tmp_path):
    import duckdb

    from trading_analytics.load import _CREATE_CANDLES_TABLE

    db_path = tmp_path / "empty.duckdb"
    con = duckdb.connect(str(db_path))
    con.execute(_CREATE_CANDLES_TABLE)
    con.close()

    try:
        build_dashboard(db_path=db_path, output_dir=tmp_path / "charts")
        assert False, "expected a ValueError for an empty candles table"
    except ValueError as exc:
        assert "no rows" in str(exc)
