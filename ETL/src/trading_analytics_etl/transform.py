"""Transform phase. Pure functions — no Postgres connection, no DuckDB
connection — so these are testable without a database at all. This is what
lets test_transform.py cover nulls/type-mismatches and dead-lettering
without spinning up any real store.
"""
from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime
from decimal import Decimal, InvalidOperation
from typing import Optional

VALID_SIDES = {"BUY", "SELL"}
VALID_STATUSES = {"NEW", "FILLED", "REJECTED", "CANCELLED"}

MONTH_NAMES = [
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
]
WEEKDAY_NAMES = [
    "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday",
]


class QualityError(Exception):
    """Raised when a row fails a data-quality check. Carries a reason_code
    (stable, machine-checkable) and source_order_id, so the caller can
    dead-letter the row without re-deriving why it failed.
    """

    def __init__(self, reason_code: str, message: str, source_order_id: str):
        super().__init__(message)
        self.reason_code = reason_code
        self.message = message
        self.source_order_id = source_order_id


# --------------------------------------------------------------------------
# dim_date — generated, no source table
# --------------------------------------------------------------------------

def build_dim_date_rows(start: date, end: date) -> list[dict]:
    rows = []
    current = start
    one_day = None
    from datetime import timedelta
    one_day = timedelta(days=1)
    while current <= end:
        date_key = current.year * 10000 + current.month * 100 + current.day
        quarter = (current.month - 1) // 3 + 1
        # Monday=0 ... Sunday=6 from date.weekday(); contract just needs a
        # stable convention plus the names, so keep it simple and consistent.
        weekday_idx = current.weekday()
        rows.append(
            {
                "date_key": date_key,
                "full_date": current,
                "day": current.day,
                "month": current.month,
                "year": current.year,
                "quarter": quarter,
                "day_of_week": weekday_idx,
                "day_name": WEEKDAY_NAMES[weekday_idx],
                "month_name": MONTH_NAMES[current.month - 1],
                "is_weekday": weekday_idx < 5,
            }
        )
        current += one_day
    return rows


def date_key_for(ts: datetime) -> int:
    return ts.year * 10000 + ts.month * 100 + ts.day


# --------------------------------------------------------------------------
# dim_instrument — Type 1, overwrite in place
# --------------------------------------------------------------------------

def derive_exchange(symbol: str) -> str:
    """Fauxnance symbol scheme -> venue. Matches contracts/analytics-schema.sql's
    documented mapping exactly."""
    if symbol.endswith(".NS"):
        return "NSE"
    if symbol.endswith(".BO"):
        return "BSE"
    if symbol.startswith("FX:"):
        return "FX"
    if symbol.startswith("X:"):
        return "CRYPTO"
    return "US"


def transform_instrument(raw: dict, loaded_at: datetime) -> dict:
    return {
        "symbol": raw["symbol"],
        "name": raw["name"],
        "asset_class": raw["asset_class"],
        "currency": raw["currency"],
        "exchange": derive_exchange(raw["symbol"]),
        "tradable": bool(raw["tradable"]),
        "loaded_at": loaded_at,
    }


# --------------------------------------------------------------------------
# dim_account — Type 2: diff against the current version, only version on
# a real attribute change.
# --------------------------------------------------------------------------

@dataclass
class AccountDiffResult:
    # account_ids whose current row must be closed (end_date, is_current=FALSE)
    to_close: list[str]
    # new version rows to insert (account_key assigned by the loader, not here)
    to_insert: list[dict]


def diff_accounts(
    raw_accounts: list[dict],
    current_by_account_id: dict[str, dict],
    effective_date: date,
    loaded_at: datetime,
) -> AccountDiffResult:
    """raw_accounts: extract_accounts() output.
    current_by_account_id: account_id -> current dim_account row (from
    DuckDB, WHERE is_current = TRUE), as returned by the loader's key-map
    query. Empty/missing entry means no current version exists yet.
    """
    to_close: list[str] = []
    to_insert: list[dict] = []

    for raw in raw_accounts:
        account_id = raw["account_id"]
        existing = current_by_account_id.get(account_id)

        incoming_attrs = (raw["holder_name"], raw["status"])
        if existing is not None:
            existing_attrs = (existing["holder_name"], existing["status"])
            if existing_attrs == incoming_attrs:
                continue  # unchanged — no write at all

        if existing is not None:
            to_close.append(account_id)

        to_insert.append(
            {
                "account_id": account_id,
                "holder_name": raw["holder_name"],
                "status": raw["status"],
                "effective_date": effective_date,
                "end_date": None,
                "is_current": True,
                "source_id": raw["source_id"],
                "loaded_at": loaded_at,
            }
        )

    return AccountDiffResult(to_close=to_close, to_insert=to_insert)


# --------------------------------------------------------------------------
# fact_trades — the seven checks, then trade_value
# --------------------------------------------------------------------------

def _to_decimal(value, field_name: str, source_order_id: str) -> Decimal:
    if value is None:
        raise QualityError(
            f"{field_name.upper()}_NULL", f"{field_name} is null", source_order_id
        )
    try:
        return Decimal(str(value))
    except (InvalidOperation, ValueError, TypeError):
        raise QualityError(
            f"{field_name.upper()}_NOT_NUMERIC",
            f"{field_name} is not a valid number: {value!r}",
            source_order_id,
        )


def fx_rate_of_record(order: dict, status: str, source_order_id: str) -> Decimal:
    """Instrument-to-account rate for an order: the fill rate once FILLED, otherwise the
    placement rate; 1 when the extract predates the rate columns."""
    raw = order.get("fill_fx_rate") if status == "FILLED" else None
    if raw is None:
        raw = order.get("placement_fx_rate")
    if raw is None:
        return Decimal("1")
    rate = _to_decimal(raw, "fx_rate", source_order_id)
    if rate <= 0:
        raise QualityError("FX_RATE_NOT_POSITIVE", f"fx rate must be > 0, got {rate}", source_order_id)
    return rate


def validate_and_build_fact_row(
    order: dict,
    instrument_key_map: dict[str, int],
    account_key_map: dict[str, int],
    known_date_keys: set[int],
    trade_key: int,
    loaded_at: datetime,
) -> dict:
    """Runs every check named in the contract, in a fixed order, raising
    QualityError on the first failure. Returns a ready-to-load fact_trades
    row dict on success. Never mutates any dimension — an unresolved key is
    always a dead-letter, never a placeholder row.
    """
    source_order_id = str(order.get("order_id", "UNKNOWN"))

    quantity = order.get("quantity")
    if quantity is None or not isinstance(quantity, int) or quantity <= 0:
        raise QualityError(
            "QTY_NOT_POSITIVE", f"quantity must be > 0, got {quantity!r}", source_order_id
        )

    price = _to_decimal(order.get("price"), "price", source_order_id)
    if price <= 0:
        raise QualityError(
            "PRICE_NOT_POSITIVE", f"price must be > 0, got {price}", source_order_id
        )

    side = order.get("side")
    if side not in VALID_SIDES:
        raise QualityError("INVALID_SIDE", f"side must be BUY or SELL, got {side!r}", source_order_id)

    status = order.get("status")
    if status not in VALID_STATUSES:
        raise QualityError(
            "INVALID_STATUS", f"status must be one of {VALID_STATUSES}, got {status!r}", source_order_id
        )

    symbol = order.get("symbol")
    if symbol not in instrument_key_map:
        raise QualityError(
            "INSTRUMENT_NOT_RESOLVED", f"instrument not found: symbol={symbol!r}", source_order_id
        )
    instrument_key = instrument_key_map[symbol]

    account_id_str = order.get("account_id_str")
    if account_id_str not in account_key_map:
        raise QualityError(
            "ACCOUNT_NOT_RESOLVED", f"account not found: account_id={account_id_str!r}", source_order_id
        )
    account_key = account_key_map[account_id_str]

    created_on = order.get("created_on")
    if not isinstance(created_on, datetime):
        raise QualityError(
            "CREATED_ON_INVALID", f"created_on is not a timestamp: {created_on!r}", source_order_id
        )
    date_key = date_key_for(created_on)
    if date_key not in known_date_keys:
        raise QualityError(
            "DATE_NOT_RESOLVED", f"date_key {date_key} not found in dim_date", source_order_id
        )

    executed_price_raw = order.get("executed_price")
    executed_price: Optional[Decimal] = None
    if executed_price_raw is not None:
        executed_price = _to_decimal(executed_price_raw, "executed_price", source_order_id)

    # trade_value: quantity * executed_price if FILLED, else quantity * price.
    # Recomputed here, never trusted from upstream — per contract.
    if status == "FILLED" and executed_price is not None:
        trade_value = Decimal(quantity) * executed_price
    else:
        trade_value = Decimal(quantity) * price

    # Multi-currency: trade_value is in the instrument's currency (right for exposure by
    # instrument); trade_value_account converts it into the account's currency (all USD), the
    # measure that sums across instruments. A FILLED order converts at the rate fetched at fill,
    # anything else at the rate captured at placement. Orders from before migration 022 have 1.
    fx_rate = fx_rate_of_record(order, status, source_order_id)
    trade_value_account = (trade_value * fx_rate).quantize(Decimal("0.01"))

    return {
        "trade_key": trade_key,
        "account_key": account_key,
        "instrument_key": instrument_key,
        "date_key": date_key,
        "side": side,
        "quantity": quantity,
        "price": price,
        "status": status,
        "executed_price": executed_price,
        "trade_value": trade_value,
        "fx_rate": fx_rate,
        "trade_value_account": trade_value_account,
        "source_order_id": source_order_id,
        "created_at": created_on,
        "loaded_at": loaded_at,
    }
