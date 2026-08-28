#Dashboard: Produces three self-contained Plotly chart artefacts showing historical market-data trends

from __future__ import annotations

from pathlib import Path

import duckdb
import plotly.graph_objects as go

from trading_analytics.load import DEFAULT_DB_PATH

DEFAULT_OUTPUT_DIR = Path(".")

CHART_FILENAMES = {
    "closing-price-trend": "closing-price-trend.html",
    "daily-volume": "daily-volume.html",
    "daily-range": "daily-range.html",
}


def _read_candles(db_path: Path | str):
    con = duckdb.connect(str(db_path), read_only=True)
    try:
        return con.execute(
            "SELECT symbol, date, open, high, low, close, volume "
            "FROM candles ORDER BY symbol, date"
        ).fetchdf()
    finally:
        con.close()


def _closing_price_trend_figure(df) -> go.Figure:
    fig = go.Figure()
    for symbol, group in df.groupby("symbol"):
        fig.add_trace(go.Scatter(x=group["date"], y=group["close"], mode="lines+markers", name=symbol))
    fig.update_layout(
        title="Closing price over time, by symbol",
        xaxis_title="Date",
        yaxis_title="Closing price (local currency per share)",
    )
    return fig


def _daily_volume_figure(df) -> go.Figure:
    fig = go.Figure()
    for symbol, group in df.groupby("symbol"):
        fig.add_trace(go.Bar(x=group["date"], y=group["volume"], name=symbol))
    fig.update_layout(
        title="Daily traded volume, by symbol",
        xaxis_title="Date",
        yaxis_title="Shares traded",
        barmode="group",
    )
    return fig


def _daily_range_figure(df) -> go.Figure:
    df = df.copy()
    df["range_pct"] = (df["high"] - df["low"]) / df["low"] * 100
    fig = go.Figure()
    for symbol, group in df.groupby("symbol"):
        fig.add_trace(go.Scatter(x=group["date"], y=group["range_pct"], mode="lines+markers", name=symbol))
    fig.update_layout(
        title="Daily trading range (high-low as % of low), by symbol",
        xaxis_title="Date",
        yaxis_title="Daily range (%)",
    )
    return fig


def build_dashboard(
    db_path: Path | str = DEFAULT_DB_PATH,
    output_dir: Path | str = DEFAULT_OUTPUT_DIR,
) -> dict[str, Path]:
    df = _read_candles(db_path)
    if df.empty:
        raise ValueError(
            f"no rows in the candles table at {db_path} — run the pipeline "
            "(trading-analytics-etl) before building the dashboard"
        )

    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    figures = {
        "closing-price-trend": _closing_price_trend_figure(df),
        "daily-volume": _daily_volume_figure(df),
        "daily-range": _daily_range_figure(df),
    }

    written: dict[str, Path] = {}
    for key, fig in figures.items():
        path = output_dir / CHART_FILENAMES[key]
        fig.write_html(str(path), full_html=True, include_plotlyjs=True)
        written[key] = path

    return written


def main(argv: list[str] | None = None) -> int:
    import argparse

    parser = argparse.ArgumentParser(description="Build the market-data trend charts from DuckDB.")
    parser.add_argument("--db-path", default=str(DEFAULT_DB_PATH))
    parser.add_argument("--output-dir", default=str(DEFAULT_OUTPUT_DIR))
    args = parser.parse_args(argv)

    written = build_dashboard(db_path=args.db_path, output_dir=args.output_dir)
    for key, path in written.items():
        print(f"wrote {key}: {path}")
    return 0


if __name__ == "__main__":
    import sys

    sys.exit(main())
