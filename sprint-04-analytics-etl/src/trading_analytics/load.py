# Load: writes the transformed result into DuckDB
from __future__ import annotations
from pathlib import Path
import duckdb
import pandas as pd

DEFAULT_DB_PATH = Path("trading_analytics.duckdb")

_CREATE_CANDLES_TABLE = """
CREATE TABLE IF NOT EXISTS candles (
    symbol     VARCHAR   NOT NULL,
    currency   VARCHAR   NOT NULL,
    date       DATE      NOT NULL,
    open       DOUBLE    NOT NULL,
    high       DOUBLE    NOT NULL,
    low        DOUBLE    NOT NULL,
    close      DOUBLE    NOT NULL,
    adjclose   DOUBLE    NOT NULL,
    volume     BIGINT,
    synthetic  BOOLEAN   NOT NULL,
    loaded_at  TIMESTAMP NOT NULL,
    PRIMARY KEY (symbol, date)
);
"""


def load_candles(valid: pd.DataFrame, db_path: Path | str = DEFAULT_DB_PATH) -> int:
    # Writes transform_candles()'s valid frame into the `candles` table.

    if valid.empty:
        return 0

    con = duckdb.connect(str(db_path))
    try:
        con.execute(_CREATE_CANDLES_TABLE)

        frame = valid.copy()
        frame["loaded_at"] = pd.Timestamp.now(tz="UTC").tz_localize(None)
        # DuckDB's DATE type expects a date, not a full timestamp.
        frame["date"] = pd.to_datetime(frame["date"]).dt.date

        con.register("incoming", frame)
        con.execute(
            """
            DELETE FROM candles
            USING incoming
            WHERE candles.symbol = incoming.symbol
              AND candles.date   = incoming.date
            """
        )
        con.execute(
            """
            INSERT INTO candles
            SELECT symbol, currency, date, open, high, low, close, adjclose,
                   volume, synthetic, loaded_at
            FROM incoming
            """
        )
        con.unregister("incoming")
        return len(frame)
    finally:
        con.close()
