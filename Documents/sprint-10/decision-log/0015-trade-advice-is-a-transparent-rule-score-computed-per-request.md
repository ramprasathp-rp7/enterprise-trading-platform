# 0015 Trade advice is a transparent rule score, computed on every request, with no signal when data is thin

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-07 |
| Decided by | raahulkumardebug (trade advisor) |

## Context

The advisor must give a BUY, SELL or HOLD signal for every instrument a customer holds or watches, using
positions, watchlists and the current market price. The sprint also asks that it is rule-based and
explainable: a customer who is told to sell should be able to see why.

The inputs available are the daily candles in `candle_data`, the live price from the cache (decision 0009), and the
customer's positions. A 50-day moving average needs 50 closes, and a new instrument may have fewer.

## Options considered

| Option | For | Against |
|---|---|---|
| A trained model | Could find patterns rules miss | Cannot give a plain-language reason. Needs training data and evaluation the project does not have. A black box in a financial screen |
| A third-party advice API | No logic to maintain | An outside dependency and quota, and the reasoning is not ours to explain |
| A scheduled job that stores signals | Cheap to read | Signals can be hours behind the price they claim to reflect. Needs a table and a job |
| A pure scoring function, computed per request | Always uses the latest price. Every point of the score has a written reason. Nothing stored. Easy to unit test | Costs one candle read per symbol on every request |

## Decision

`SignalEngine` is a static function from (daily closes, live price, optional holding) to a signal. It adds up
a score from six rules, and each rule that moves the score appends a sentence saying so:

| Factor | Rule | Score |
|---|---|---|
| Trend | 20-day average above or below the 50-day by more than 0.5% | +1 / -1 |
| Price vs average | Price above or below its 20-day average | +0.5 / -0.5 |
| RSI(14) | Below 30 / above 70 | +1 / -1 |
| Momentum | 20-day change above +5% / below -5% | +0.5 / -0.5 |
| Take profit (holdings) | Up 25% or more on cost while RSI is above 65 | -1 |
| Cut losses (holdings) | Down 15% or more on cost while the trend is down | -1 |

A score of 1.5 or more is BUY, -1.5 or less is SELL, anything between is HOLD. With fewer than 50 closes, or no
price, the engine returns **no signal** and an explanation, not HOLD. What decided it between a score and a model is that a customer can read
the reasons and disagree with one of them, which is not possible with a trained model.

`TradeAdviceService` reads the last 120 daily closes per instrument and the cached price (falling back to the
last close), and the response always carries the disclaimer that signals are educational, not investment advice.

## Consequences

- The weights and thresholds are common heuristics, not tuned or backtested. They are constants in one file,
  so changing them changes advice for every customer at once.
- "No signal" and HOLD mean different things on screen. A new instrument shows an explanation instead of
  a misleading HOLD.
- The holding rules compare price with the position's instrument-currency average cost (decision 0008), so FX moves are not part of the
  take-profit and cut-loss tests.
- Nothing is stored, so there is no history of what the advisor said and no way to measure whether it was right.
- The cost grows with the number of held and watched symbols. It is acceptable for the 25-instrument universe.
