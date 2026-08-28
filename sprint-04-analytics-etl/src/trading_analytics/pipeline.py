from __future__ import annotations

import argparse
import logging
import sys
from dataclasses import dataclass, field
from pathlib import Path

from dotenv import load_dotenv

from trading_analytics.errors import (
    FauxnanceConnectionError,
    QuotaExhaustedError,
    SymbolRequestError,
)
from trading_analytics.extract import extract_candles
from trading_analytics.load import DEFAULT_DB_PATH, load_candles
from trading_analytics.transform import transform_candles

logger = logging.getLogger(__name__)


@dataclass
class SymbolResult:
    symbol: str
    valid_rows: int = 0
    quarantined_rows: int = 0
    loaded_rows: int = 0
    error: str | None = None


@dataclass
class PipelineResult:
    results: list[SymbolResult] = field(default_factory=list)
    stopped_early: bool = False
    stop_reason: str | None = None


def run(
    symbols: list[str],
    start: str,
    end: str,
    *,
    db_path: Path | str = DEFAULT_DB_PATH,
    use_cache: bool = True,
) -> PipelineResult:
    # Runs extract -> transform -> load for each symbol in turn.

    pipeline_result = PipelineResult()

    for symbol in symbols:
        result = SymbolResult(symbol=symbol)

        try:
            raw = extract_candles(symbol, start, end, use_cache=use_cache)
        except QuotaExhaustedError as exc:
            logger.error("quota exhausted — stopping the run: %s", exc)
            result.error = str(exc)
            pipeline_result.results.append(result)
            pipeline_result.stopped_early = True
            pipeline_result.stop_reason = str(exc)
            break
        except SymbolRequestError as exc:
            logger.error("skipping %s (bad request): %s", symbol, exc)
            result.error = str(exc)
            pipeline_result.results.append(result)
            continue
        except FauxnanceConnectionError as exc:
            logger.error("skipping %s (connection retries exhausted): %s", symbol, exc)
            result.error = str(exc)
            pipeline_result.results.append(result)
            continue

        transformed = transform_candles(raw)
        result.valid_rows = len(transformed.valid)
        result.quarantined_rows = len(transformed.quarantined)
        for q in transformed.quarantined:
            logger.warning("quarantined %s %s: %s", symbol, q.raw_date, q.reason)

        result.loaded_rows = load_candles(transformed.valid, db_path=db_path)
        pipeline_result.results.append(result)

    return pipeline_result


def main(argv: list[str] | None = None) -> int:
    load_dotenv()
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")

    parser = argparse.ArgumentParser(
        prog="trading-analytics-etl",
        description="Pull Fauxnance candles, clean them, and load them into DuckDB.",
    )
    parser.add_argument("--symbols", nargs="+", required=True, help="e.g. --symbols INFY.NS RELIANCE.NS")
    parser.add_argument("--start", required=True, help="YYYY-MM-DD")
    parser.add_argument("--end", required=True, help="YYYY-MM-DD")
    parser.add_argument("--db-path", default=str(DEFAULT_DB_PATH))
    parser.add_argument("--no-cache", action="store_true", help="bypass the .cache/ lookup and force a fresh pull")
    args = parser.parse_args(argv)

    result = run(
        args.symbols,
        args.start,
        args.end,
        db_path=Path(args.db_path),
        use_cache=not args.no_cache,
    )

    for r in result.results:
        if r.error:
            print(f"{r.symbol}: FAILED — {r.error}")
        else:
            print(f"{r.symbol}: {r.loaded_rows} loaded, {r.quarantined_rows} quarantined")

    if result.stopped_early:
        print(f"\nSTOPPED EARLY: {result.stop_reason}", file=sys.stderr)
        return 1

    return 0


if __name__ == "__main__":
    sys.exit(main())
