"""Watermark bookkeeping in Postgres (migration 009, analytics_watermarks).

Deliberately kept in Postgres rather than moved into DuckDB: Postgres is the
source of truth for "how far have I read from the source", it's inspectable
with normal tooling, and it means a locked/rebuilt DuckDB file never blocks
knowing where extraction last left off.

Only fact_trades is watermarked. dim_instrument and dim_account are full
snapshot + idempotent merge every run — see contracts/analytics-schema.sql's
load-order notes, which only describe the fact load as incremental.
"""
from __future__ import annotations

import uuid
from datetime import datetime, timezone
from typing import Optional

FACT_TRADES_COMPONENT = "fact_trades"


def get_watermark(conn, component: str = FACT_TRADES_COMPONENT) -> Optional[datetime]:
    """Return the last-processed created_on for this component, or None if
    the component has never run (or the row doesn't exist yet)."""
    with conn.cursor() as cur:
        cur.execute(
            "SELECT last_processed_created_on FROM analytics_watermarks WHERE component = %s",
            (component,),
        )
        row = cur.fetchone()
        return row[0] if row else None


def advance_watermark(
    conn,
    max_created_on: datetime,
    batch_id: Optional[uuid.UUID] = None,
    component: str = FACT_TRADES_COMPONENT,
) -> uuid.UUID:
    """Advance the watermark to max_created_on. Call only after the DuckDB
    transaction for this batch has committed successfully — advancing first
    risks the DuckDB write failing after Postgres thinks it's done, which
    would silently skip those rows on the next run.
    """
    batch_id = batch_id or uuid.uuid4()
    with conn.cursor() as cur:
        cur.execute(
            """
            INSERT INTO analytics_watermarks
                (component, last_processed_created_on, batch_id, last_run_at, updated_at)
            VALUES (%s, %s, %s, now(), now())
            ON CONFLICT (component) DO UPDATE SET
                last_processed_created_on = EXCLUDED.last_processed_created_on,
                batch_id = EXCLUDED.batch_id,
                last_run_at = now(),
                updated_at = now()
            """,
            (component, max_created_on, batch_id),
        )
    conn.commit()
    return batch_id


def utcnow() -> datetime:
    return datetime.now(timezone.utc)
