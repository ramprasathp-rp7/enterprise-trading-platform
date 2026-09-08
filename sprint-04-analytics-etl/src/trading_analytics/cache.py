#Disk cache for raw Fauxnance responses

from __future__ import annotations

import json
from pathlib import Path

DEFAULT_CACHE_DIR = Path(__file__).resolve().parent.parent.parent / ".cache"


def cache_path(symbol: str, start: str, end: str, cache_dir: Path = DEFAULT_CACHE_DIR) -> Path:
    #Deterministic path for one (symbol, date range) pull
    safe_symbol = symbol.replace(":", "_").replace(".", "-").replace("/", "_")
    return cache_dir / f"{safe_symbol}__{start}__{end}.json"


def read_cache(symbol: str, start: str, end: str, cache_dir: Path = DEFAULT_CACHE_DIR) -> dict | None:
    #Returns the cached raw response, or None if nothing is cached yet.
    path = cache_path(symbol, start, end, cache_dir)
    if not path.exists():
        return None
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def write_cache(symbol: str, start: str, end: str, raw_response: dict, cache_dir: Path = DEFAULT_CACHE_DIR) -> None:
    #Writes a raw response to the cache, creating cache_dir if needed.
    cache_dir.mkdir(parents=True, exist_ok=True)
    path = cache_path(symbol, start, end, cache_dir)
    with path.open("w", encoding="utf-8") as f:
        json.dump(raw_response, f, indent=2)
