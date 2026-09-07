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

# Known Fauxnance tickers -> company name, for legends. Falls back to the bare ticker
# for a symbol not in this table, so an unlisted symbol still renders (just tersely).
COMPANY_NAMES = {
    "INFY.NS": "Infosys",
    "RELIANCE.NS": "Reliance Industries",
}


def _display_name(symbol: str) -> str:
    company = COMPANY_NAMES.get(symbol)
    return f"{company} ({symbol})" if company else symbol


def _read_candles(db_path: Path | str):
    con = duckdb.connect(str(db_path), read_only=True)
    try:
        return con.execute(
            "SELECT symbol, date, open, high, low, close, volume "
            "FROM candles ORDER BY symbol, date"
        ).fetchdf()
    finally:
        con.close()


def _pct_change_summary(df) -> str:
    parts = []
    for symbol, group in df.groupby("symbol"):
        group = group.sort_values("date")
        first_close, last_close = group["close"].iloc[0], group["close"].iloc[-1]
        pct = (last_close - first_close) / first_close * 100
        parts.append(f"{_display_name(symbol)} {pct:+.1f}%")
    return "; ".join(parts)


def _closing_price_trend_figure(df) -> go.Figure:
    fig = go.Figure()
    for symbol, group in df.groupby("symbol"):
        fig.add_trace(go.Scatter(x=group["date"], y=group["close"], mode="lines+markers", name=_display_name(symbol)))
    fig.update_layout(
        title=f"Closing price change over the pulled period — {_pct_change_summary(df)}",
        xaxis_title="Date",
        yaxis_title="Closing price (local currency per share)",
    )
    return fig


def _peak_volume_summary(df) -> str:
    peak_rows = df.loc[df.groupby("symbol")["volume"].idxmax()][["symbol", "date"]]
    peak_dates = set(peak_rows["date"])
    if len(peak_dates) == 1:
        (shared_date,) = peak_dates
        symbols = ", ".join(_display_name(s) for s in peak_rows["symbol"])
        return f"{symbols} all peaked on {shared_date:%Y-%m-%d}"
    return "; ".join(f"{_display_name(row.symbol)} peaked {row.date:%Y-%m-%d}" for row in peak_rows.itertuples())


def _daily_volume_figure(df) -> go.Figure:
    fig = go.Figure()
    for symbol, group in df.groupby("symbol"):
        fig.add_trace(go.Bar(x=group["date"], y=group["volume"], name=_display_name(symbol)))
    fig.update_layout(
        title=f"Daily traded volume — {_peak_volume_summary(df)}",
        xaxis_title="Date",
        yaxis_title="Shares traded",
        barmode="group",
    )
    return fig


def _volatility_summary(df) -> str:
    df = df.copy()
    df["range_pct"] = (df["high"] - df["low"]) / df["low"] * 100
    avg_by_symbol = df.groupby("symbol")["range_pct"].mean().sort_values(ascending=False)
    leader = avg_by_symbol.index[0]
    parts = ", ".join(f"{_display_name(sym)} {val:.2f}%" for sym, val in avg_by_symbol.items())
    return f"{_display_name(leader)} the most volatile ({parts})"


def _daily_range_figure(df) -> go.Figure:
    df = df.copy()
    df["range_pct"] = (df["high"] - df["low"]) / df["low"] * 100
    fig = go.Figure()
    for symbol, group in df.groupby("symbol"):
        fig.add_trace(go.Scatter(x=group["date"], y=group["range_pct"], mode="lines+markers", name=_display_name(symbol)))
    fig.update_layout(
        title=f"Daily trading range (high-low as % of low) — {_volatility_summary(df)}",
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
