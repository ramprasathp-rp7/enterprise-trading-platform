# Computes the exact numbers behind the three candidate business claims in claims.md
# Usage: python compute_claim_candidates.py --db-path trading_analytics.duckdb

from __future__ import annotations

import argparse

import duckdb


def compute(db_path: str) -> None:
    con = duckdb.connect(db_path, read_only=True)
    try:
        symbols = [r[0] for r in con.execute("SELECT DISTINCT symbol FROM candles ORDER BY symbol").fetchall()]
        if not symbols:
            print("No symbols found — run the pipeline before this script.")
            return

        print("=" * 70)
        print("CLAIM 1 candidates — total return over the pulled window")
        print("=" * 70)
        for symbol in symbols:
            row = con.execute(
                """
                WITH bounds AS (
                    SELECT min(date) AS first_date, max(date) AS last_date FROM candles WHERE symbol = ?
                )
                SELECT
                    b.first_date, first.close AS first_close,
                    b.last_date, last.close AS last_close,
                    round((last.close - first.close) / first.close * 100, 2) AS pct_change
                FROM bounds b
                JOIN candles first ON first.symbol = ? AND first.date = b.first_date
                JOIN candles last  ON last.symbol  = ? AND last.date  = b.last_date
                """,
                [symbol, symbol, symbol],
            ).fetchone()
            first_date, first_close, last_date, last_close, pct_change = row
            direction = "rose" if pct_change >= 0 else "fell"
            print(
                f"{symbol}: {direction} {abs(pct_change)}% "
                f"from {first_date} (close {first_close}) to {last_date} (close {last_close})"
            )

        print()
        print("=" * 70)
        print("CLAIM 2 candidates — average daily range (high-low as % of low)")
        print("=" * 70)
        ranked = con.execute(
            """
            SELECT symbol, round(avg((high - low) / low * 100), 3) AS avg_range_pct
            FROM candles
            GROUP BY symbol
            ORDER BY avg_range_pct DESC
            """
        ).fetchall()
        for symbol, avg_range_pct in ranked:
            print(f"{symbol}: average daily range {avg_range_pct}% of low")
        if len(ranked) >= 2:
            most, least = ranked[0], ranked[-1]
            if least[1] > 0:
                multiple = round(most[1] / least[1], 1)
                print(f"\n-> {most[0]} averaged {multiple}x {least[0]}'s daily range over the same window")

        print()
        print("=" * 70)
        print("CLAIM 3 candidates — volume: average and single largest day")
        print("=" * 70)
        for symbol in symbols:
            avg_vol = con.execute(
                "SELECT round(avg(volume)) FROM candles WHERE symbol = ? AND volume IS NOT NULL", [symbol]
            ).fetchone()[0]
            peak = con.execute(
                "SELECT date, volume FROM candles WHERE symbol = ? AND volume IS NOT NULL "
                "ORDER BY volume DESC LIMIT 1",
                [symbol],
            ).fetchone()
            if avg_vol is None:
                print(f"{symbol}: no non-null volume data")
                continue
            peak_date, peak_vol = peak
            print(f"{symbol}: average daily volume {int(avg_vol):,} shares; peak {int(peak_vol):,} on {peak_date}")

        print()
        print("=" * 70)
        print("Row counts and date ranges (for the Notes section of claims.md)")
        print("=" * 70)
        for symbol, count, first_date, last_date in con.execute(
            "SELECT symbol, count(*), min(date), max(date) FROM candles GROUP BY symbol ORDER BY symbol"
        ).fetchall():
            print(f"{symbol}: {count} rows, {first_date} to {last_date}")

    finally:
        con.close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--db-path", default="trading_analytics.duckdb")
    args = parser.parse_args()
    compute(args.db_path)
