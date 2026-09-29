import importlib.resources
from datetime import date, datetime, timezone

import duckdb
import pytest

from trading_analytics_etl import load, transform


def _schema_path() -> str:
    return str(
        importlib.resources.files("trading_analytics_etl").joinpath("sql/analytics-schema.sql")
    )


@pytest.fixture
def duck_con():
    con = duckdb.connect(":memory:")
    load.ensure_schema(con, _schema_path())
    yield con
    con.close()


@pytest.fixture
def now():
    return datetime(2026, 9, 29, 12, 0, 0, tzinfo=timezone.utc)


@pytest.fixture
def seeded_dims(duck_con, now):
    """A duck_con with one instrument and one current account already
    loaded, plus the small dim_date slice the tests' order dates fall in.
    Returns the key maps the fact-row validator needs.
    """
    load.load_dim_date(
        duck_con, transform.build_dim_date_rows(date(2026, 9, 1), date(2026, 9, 30))
    )
    load.load_dim_instrument(
        duck_con,
        [transform.transform_instrument(
            {"symbol": "INFY.NS", "name": "Infosys", "asset_class": "EQUITY",
             "currency": "INR", "tradable": True}, now)],
        next_key_start=1,
    )
    load.insert_new_account_versions(
        duck_con,
        [{"account_id": "ACC-000001", "holder_name": "Jane Doe", "status": "ACTIVE",
          "effective_date": now.date(), "end_date": None, "is_current": True,
          "source_id": 1, "loaded_at": now}],
        next_key_start=1,
    )
    return {
        "instrument_key_map": load.get_instrument_key_map(duck_con),
        "account_key_map": load.get_account_key_map(duck_con),
        "known_date_keys": load.get_known_date_keys(duck_con),
    }
