import json
from pathlib import Path

import pytest

FIXTURES_DIR = Path(__file__).resolve().parent.parent / "fixtures"


def _load(name: str) -> dict:
    with (FIXTURES_DIR / name).open("r", encoding="utf-8") as f:
        return json.load(f)


@pytest.fixture
def reliance_candles_raw() -> dict:
    return _load("candles-reliance-ns-2026-07.json")


@pytest.fixture
def infy_candles_raw() -> dict:
    return _load("candles-infy-ns-2026-07.json")


@pytest.fixture
def malformed_candles_raw() -> dict:
    return _load("candles-malformed.json")
