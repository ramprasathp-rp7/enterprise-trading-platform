"""Reconciliation, required by contracts/analytics-schema.sql: for a given
day, row count and summed trade_value in fact_trades must equal the same
figures computed directly against Postgres.

Also reports that day's dead_letter_trades count alongside the comparison,
so a mismatch fully explained by quarantined rows (expected) is
distinguishable from one that isn't (a real bug) — that distinction is the
point, not just a pass/fail check.
"""
from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from decimal import Decimal

TOLERANCE = Decimal("0.01")


@dataclass
class ReconciliationResult:
    trade_date: date
    duckdb_count: int
    duckdb_total: Decimal
    postgres_count: int
    postgres_total: Decimal
    dead_lettered_count: int
    # Summed trade_value_account (account currency, USD); defaults keep older callers working
    duckdb_total_account: Decimal = Decimal("0")
    postgres_total_account: Decimal = Decimal("0")

    @property
    def counts_match(self) -> bool:
        return self.duckdb_count == self.postgres_count

    @property
    def totals_match(self) -> bool:
        return abs(self.duckdb_total - self.postgres_total) <= TOLERANCE

    @property
    def account_totals_match(self) -> bool:
        return abs(self.duckdb_total_account - self.postgres_total_account) <= TOLERANCE

    @property
    def ok(self) -> bool:
        return self.counts_match and self.totals_match and self.account_totals_match


def postgres_daily_account_total(pg_conn, trade_date: date) -> Decimal:
    """Same day's trade value in account currency, at each order's rate of record."""
    with pg_conn.cursor() as cur:
        cur.execute(
            """
            SELECT COALESCE(SUM(ROUND(
                       CASE WHEN status = 'FILLED'
                            THEN quantity * executed_price * COALESCE(fill_fx_rate, placement_fx_rate)
                            ELSE quantity * price * placement_fx_rate
                       END, 2)
                   ), 0)
              FROM orders
             WHERE created_on::date = %s
            """,
            (trade_date,),
        )
        (total,) = cur.fetchone()
        return Decimal(total)


def duckdb_daily_account_total(duck_con, trade_date: date) -> Decimal:
    row = duck_con.execute(
        """
        SELECT COALESCE(SUM(f.trade_value_account), 0)
          FROM fact_trades f
          JOIN dim_date d ON d.date_key = f.date_key
         WHERE d.full_date = ?
        """,
        [trade_date],
    ).fetchone()
    return Decimal(str(row[0]))


def postgres_daily_aggregate(pg_conn, trade_date: date) -> tuple[int, Decimal]:
    with pg_conn.cursor() as cur:
        cur.execute(
            """
            SELECT COUNT(*),
                   COALESCE(SUM(
                       CASE WHEN status = 'FILLED'
                            THEN quantity * executed_price
                            ELSE quantity * price
                       END
                   ), 0)
              FROM orders
             WHERE created_on::date = %s
            """,
            (trade_date,),
        )
        count, total = cur.fetchone()
        return count, Decimal(total)


def duckdb_daily_aggregate(duck_con, trade_date: date) -> tuple[int, Decimal]:
    row = duck_con.execute(
        """
        SELECT COUNT(*), COALESCE(SUM(f.trade_value), 0)
          FROM fact_trades f
          JOIN dim_date d ON d.date_key = f.date_key
         WHERE d.full_date = ?
        """,
        [trade_date],
    ).fetchone()
    return row[0], Decimal(str(row[1]))


def dead_letter_count_for_day(duck_con, trade_date: date) -> int:
    # dead_letter_trades has no date_key (it's outside the star schema), so
    # this uses the payload's created_on. Cheap enough at this data volume;
    # revisit if dead-lettered order volume ever justifies its own index.
    rows = duck_con.execute("SELECT payload FROM dead_letter_trades").fetchall()
    import json
    count = 0
    for (payload_json,) in rows:
        payload = json.loads(payload_json)
        created_on = payload.get("created_on", "")
        if created_on.startswith(trade_date.isoformat()):
            count += 1
    return count


def reconcile_day(pg_conn, duck_con, trade_date: date) -> ReconciliationResult:
    pg_count, pg_total = postgres_daily_aggregate(pg_conn, trade_date)
    duck_count, duck_total = duckdb_daily_aggregate(duck_con, trade_date)
    dlq_count = dead_letter_count_for_day(duck_con, trade_date)
    return ReconciliationResult(
        trade_date=trade_date,
        duckdb_count=duck_count,
        duckdb_total=duck_total,
        postgres_count=pg_count,
        postgres_total=pg_total,
        dead_lettered_count=dlq_count,
        duckdb_total_account=duckdb_daily_account_total(duck_con, trade_date),
        postgres_total_account=postgres_daily_account_total(pg_conn, trade_date),
    )
