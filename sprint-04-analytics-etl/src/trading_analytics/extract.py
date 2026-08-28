# Extract: obtains raw Fauxnance responses and hands them on unchanged.
from __future__ import annotations

import logging
import os
import time
from pathlib import Path

import requests

from trading_analytics.cache import DEFAULT_CACHE_DIR, read_cache, write_cache
from trading_analytics.errors import (
    FauxnanceConnectionError,
    QuotaExhaustedError,
    SymbolRequestError,
)

logger = logging.getLogger(__name__)

DEFAULT_MAX_RETRIES = 3
DEFAULT_BACKOFF_SECONDS = 1.0


def _api_key() -> str:
    key = os.environ.get("FAUXNANCE_API_KEY")
    if not key:
        raise RuntimeError(
            "FAUXNANCE_API_KEY is not set. Copy _example.env to .env and set it there "
            "— the key is never a literal in source."
        )
    return key


def _base_url() -> str:
    url = os.environ.get("FAUXNANCE_BASE_URL")
    if not url:
        raise RuntimeError("FAUXNANCE_BASE_URL is not set. See _example.env.")
    return url.rstrip("/")


def _get_with_retries(
    session: requests.Session,
    url: str,
    *,
    headers: dict,
    params: dict | None = None,
    max_retries: int = DEFAULT_MAX_RETRIES,
    backoff_seconds: float = DEFAULT_BACKOFF_SECONDS,
) -> requests.Response:
    # GET with growing backoff, but ONLY for the case nothing reached the service
    
    attempt = 0
    while True:
        attempt += 1
        try:
            return session.get(url, headers=headers, params=params, timeout=10)
        except (requests.ConnectionError, requests.Timeout) as exc:
            if attempt >= max_retries:
                raise FauxnanceConnectionError(
                    f"{url}: no response after {attempt} attempt(s): {exc}"
                ) from exc
            sleep_for = backoff_seconds * (2 ** (attempt - 1))
            logger.warning(
                "connection error on attempt %d/%d for %s, retrying in %.1fs",
                attempt, max_retries, url, sleep_for,
            )
            time.sleep(sleep_for)


def _raise_for_status(response: requests.Response, symbol: str) -> None:
    if response.status_code == 429:
        retry_after = response.headers.get("Retry-After")
        raise QuotaExhaustedError(int(retry_after) if retry_after else None)
    if 400 <= response.status_code < 500:
        raise SymbolRequestError(symbol, response.status_code, response.text[:200])
    response.raise_for_status()


def extract_candles(
    symbol: str,
    start: str,
    end: str,
    *,
    cache_dir: Path = DEFAULT_CACHE_DIR,
    use_cache: bool = True,
    session: requests.Session | None = None,
) -> dict:
    # Returns the raw CandlesResponse envelope for one symbol and date range.

    if use_cache:
        cached = read_cache(symbol, start, end, cache_dir)
        if cached is not None:
            logger.info("cache hit: %s %s..%s", symbol, start, end)
            return cached

    session = session or requests.Session()
    url = f"{_base_url()}/candles/{symbol}"
    headers = {"X-Api-Key": _api_key()}
    params = {"start": start, "end": end}

    response = _get_with_retries(session, url, headers=headers, params=params)
    _raise_for_status(response, symbol)
    raw = response.json()

    if use_cache:
        write_cache(symbol, start, end, raw, cache_dir)

    return raw


def get_usage(*, session: requests.Session | None = None) -> dict:
    # GET /usage — check quota status before assuming the API is broken
    session = session or requests.Session()
    url = f"{_base_url()}/usage"
    response = _get_with_retries(session, url, headers={"X-Api-Key": _api_key()})
    response.raise_for_status()
    return response.json()


def get_health(*, session: requests.Session | None = None) -> dict:
    # GET /health — no key required. 
    session = session or requests.Session()
    url = f"{_base_url()}/health"
    response = _get_with_retries(session, url, headers={})
    response.raise_for_status()
    return response.json()
