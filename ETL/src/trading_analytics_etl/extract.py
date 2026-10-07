"""Extract phase. Reads from the operational Postgres schema and returns
plain dicts — no transform or validation logic lives here, so these
functions are easy to point at a fake connection in tests.
"""
from __future__ import annotations

from datetime import datetime
from typing import Optional


def extract_instruments(conn) -> list[dict]:
    """Full snapshot, every run — dim_instrument is Type 1 and cheap to
    rescan; there's no watermark on instruments."""
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT id, symbol, name, asset_class, currency, tradable
              FROM instruments
             ORDER BY symbol
            """
        )
        cols = [d.name for d in cur.description]
        return [dict(zip(cols, row)) for row in cur.fetchall()]


def extract_accounts(conn) -> list[dict]:
    """Full snapshot, every run. Includes accounts.id (the Postgres
    surrogate key) as source_id — dim_account.source_id is NOT NULL in the
    contract, so this must be carried through, not dropped."""
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT id AS source_id, account_id, holder_name, status, last_updated
              FROM accounts
             ORDER BY account_id
            """
        )
        cols = [d.name for d in cur.description]
        return [dict(zip(cols, row)) for row in cur.fetchall()]


def extract_orders_since(conn, watermark: Optional[datetime]) -> list[dict]:
    """Orders created since the watermark, in whatever status they reached.
    Rejected/cancelled orders are extracted, not filtered — fill rate can't
    be computed from fills alone (contract's explicit reasoning).

    Uses >= against the watermark, not >. Because the load merges on
    source_order_id (idempotent), re-reading the boundary row on a tie is
    harmless; using > risks silently missing a same-timestamp order that
    committed after the watermark was read (see design notes in the repo
    for the worked example).
    """
    sql = """
        SELECT o.id AS order_id,
               a.account_id AS account_id_str,
               i.symbol,
               o.side,
               o.quantity,
               o.price,
               o.executed_price,
               o.status,
               o.created_on,
               o.placement_fx_rate,
               o.fill_fx_rate
          FROM orders o
          JOIN accounts a ON a.id = o.account_id
          JOIN instruments i ON i.id = o.instrument_id
    """
    params: tuple = ()
    if watermark is not None:
        sql += " WHERE o.created_on >= %s"
        params = (watermark,)
    sql += " ORDER BY o.created_on"

    with conn.cursor() as cur:
        cur.execute(sql, params)
        cols = [d.name for d in cur.description]
        return [dict(zip(cols, row)) for row in cur.fetchall()]
