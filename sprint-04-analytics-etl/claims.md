# Claims

| # | Claim | Chart artefact |
|---|---|---|
| 1 | INFY.NS's closing price fell 25.67% over the pulled year, from ₹1,500.10 on 2025-08-28 to ₹1,115.08 on 2026-08-27. | charts/closing-price-trend.html |
| 2 | INFY.NS's daily trading range averaged 2.22% of its low price over the pulled year — 1.3x RELIANCE.NS's 1.71% over the same window — making INFY.NS the more volatile of the two large-cap NSE names pulled. | charts/daily-range.html |
| 3 | INFY.NS and RELIANCE.NS both recorded their single highest-volume trading day of the past year on the same date, 2026-05-29 — 69,085,430 and 42,634,247 shares respectively, 6.2x and 3.2x each stock's own yearly daily average (11,141,694 and 13,370,477) — consistent with a shared market-wide event that day rather than stock-specific news. | charts/daily-volume.html |

## Notes

**Date range pulled:** 2025-08-01 to 2026-08-01 (RELIANCE.NS and INFY.NS: 261
trading days each, 2025-08-28 to 2026-08-27). TATASTEEL.BO was pulled over the
same request window but Fauxnance only returned 30 rows, 2026-07-17 to
2026-08-27 — a much shorter real history than the other two names.

**Symbols in scope, and why each is here:**

- **RELIANCE.NS** — NSE, large-cap conglomerate (energy, telecom, retail).
  Satisfies the "at least two NSE or BSE instruments" requirement on its own
  merits, and high liquidity means few if any calendar gaps to explain away.
- **INFY.NS** — NSE, IT services major. Chosen alongside RELIANCE.NS
  specifically for sector contrast (old-economy conglomerate vs. IT
  services), which is what claims 2 and 3 both rest on.
- **TATASTEEL.BO** — BSE, not NSE, rounds out actual exchange coverage rather
  than pulling three NSE names and technically-but-thinly satisfying the
  requirement. Its short real history (30 days vs. 261) is why it doesn't
  headline a claim here: a 30-day window isn't comparable to a 261-day one
  for a year-over-year return or volume-baseline claim. It's still in the
  charts for reference, and is the same symbol used in the Sprint 3 seed
  data — there, marked non-tradable on our own platform, which is unrelated
  to whether the exchange itself still prices it.

**What the transform did with rejected rows:** 0 quarantined across all three
symbols in this pull (confirmed by `trading-analytics-etl`'s own per-symbol
"N loaded, 0 quarantined" output at pull time). If you've re-pulled since and
gotten a different figure, update this line — don't leave a stale zero.

**Claim considered and withdrawn:** A fourth candidate — TATASTEEL.BO's 3.57%
rise over its 30-day window — was dropped. Not because it's untrue, but
because a 30-day return isn't comparable to the year-long windows the other
two claims use, and stating it side-by-side without that caveat would
misrepresent how much history actually backs it.

**A caveat on claim 3 worth bringing to review:** TATASTEEL.BO's own peak
volume day (2026-08-20) isn't compared here, because its data starts
2026-07-17 — nearly two months after RELIANCE.NS and INFY.NS's shared peak on
2026-05-29. It has no candle on that date at all, so "did TATASTEEL.BO also
peak that day" isn't answerable from this pull, not "no."

**How a teammate runs this pipeline:** 
```
pip install -e "sprint-04-analytics-etl[dev]"

pytest sprint-04-analytics-etl

trading-analytics-etl --symbols RELIANCE.NS INFY.NS TATASTEEL.BO --start 2025-08-01 --end 2026-08-01

python -m trading_analytics.dashboard --db-path trading_analytics.duckdb --output-dir sprint-04-analytics-etl/charts
```