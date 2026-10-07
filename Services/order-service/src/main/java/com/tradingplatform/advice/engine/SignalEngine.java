package com.tradingplatform.advice.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Turns an instrument's recent daily closes (and, for holdings, the position) into a
 * BUY / SELL / HOLD signal with the reasons behind it.
 *
 * Research-driven, rule-based and explainable: every rule that moves the score adds a reason in
 * plain language, so the customer can see why. The rules are common technical-analysis heuristics:
 *
 * | Factor | Rule | Score |
 * |--------|------|-------|
 * | Trend | 20-day average above / below the 50-day average (by more than 0.5%) | +1 / -1 |
 * | Price vs average | Price above / below its 20-day average | +0.5 / -0.5 |
 * | RSI(14) | Below 30 oversold / above 70 overbought | +1 / -1 |
 * | Momentum | 20-day change above +5% / below -5% | +0.5 / -0.5 |
 * | Take profit (holdings) | Up 25%+ on cost while RSI is above 65 | -1 |
 * | Cut losses (holdings) | Down 15%+ on cost while the trend is down | -1 |
 *
 * Score >= 1.5 is BUY, <= -1.5 is SELL, anything between is HOLD. Confidence grows with the
 * size of the score. With fewer than {@value #MIN_CLOSES} closes, or no price, there is no signal.
 *
 * Educational only; not investment advice.
 */
public final class SignalEngine {

    /** Closes needed: the 50-day average needs 50 */
    public static final int MIN_CLOSES = 50;
    static final double BUY_THRESHOLD = 1.5;
    static final double SELL_THRESHOLD = -1.5;

    private SignalEngine() {
    }

    /** A position held in the instrument: quantity and average cost */
    public record Holding(int quantity, BigDecimal averageCost) {
    }

    /**
     * Result for one instrument.
     *
     * @param signal     BUY, SELL or HOLD; null when there was not enough data
     * @param confidence 0-100 (how strongly the factors agree); null without a signal
     * @param score      sum of the factor scores
     * @param summary    one-sentence explanation
     * @param reasons    every factor considered, with its effect
     * @param indicators the computed indicators (null fields when not computable)
     */
    public record Evaluation(Signal signal, Integer confidence, double score, String summary,
                             List<Reason> reasons, Indicators indicators) {

        public boolean hasSignal() {
            return signal != null;
        }
    }

    public record Indicators(BigDecimal price, BigDecimal sma20, BigDecimal sma50, BigDecimal rsi14,
                             BigDecimal momentum20Percent, BigDecimal unrealisedPercent, int closes) {
    }

    /**
     * Evaluates one instrument.
     *
     * @param closesOldestFirst daily closes, oldest first
     * @param livePrice         latest price (e.g. the cached quote); null to use the last close
     * @param holding           the customer's position, or null if it is only watched
     */
    public static Evaluation evaluate(List<BigDecimal> closesOldestFirst, BigDecimal livePrice, Holding holding) {
        List<BigDecimal> closes = closesOldestFirst == null ? List.of()
            : closesOldestFirst.stream().filter(c -> c != null && c.signum() > 0).toList();
        BigDecimal price = livePrice != null && livePrice.signum() > 0 ? livePrice
            : closes.isEmpty() ? null : closes.get(closes.size() - 1);

        if (price == null) {
            return insufficient("No price is available for this instrument yet.", closes.size(), null);
        }
        if (closes.size() < MIN_CLOSES) {
            return insufficient(String.format(Locale.US,
                "Only %d day%s of price history; at least %d are needed for a reliable signal.",
                closes.size(), closes.size() == 1 ? "" : "s", MIN_CLOSES), closes.size(), price);
        }

        double p = price.doubleValue();
        double sma20 = average(closes, 20);
        double sma50 = average(closes, 50);
        double rsi = rsi(closes, 14);
        double close20Ago = closes.get(closes.size() - 21).doubleValue();
        double momentum = (closes.get(closes.size() - 1).doubleValue() / close20Ago - 1) * 100;
        Double unrealised = holding != null && holding.averageCost() != null && holding.averageCost().signum() > 0
            ? (p / holding.averageCost().doubleValue() - 1) * 100 : null;

        List<Reason> reasons = new ArrayList<>();
        double score = 0;

        // Trend
        boolean uptrend = sma20 > sma50 * 1.005;
        boolean downtrend = sma20 < sma50 * 0.995;
        if (uptrend) {
            score += 1;
            reasons.add(new Reason("Trend", Reason.Impact.POSITIVE, String.format(Locale.US,
                "Uptrend: the 20-day average (%s) is above the 50-day average (%s).", money(sma20), money(sma50))));
        } else if (downtrend) {
            score -= 1;
            reasons.add(new Reason("Trend", Reason.Impact.NEGATIVE, String.format(Locale.US,
                "Downtrend: the 20-day average (%s) is below the 50-day average (%s).", money(sma20), money(sma50))));
        } else {
            reasons.add(new Reason("Trend", Reason.Impact.NEUTRAL, String.format(Locale.US,
                "No clear trend: the 20-day (%s) and 50-day (%s) averages are close together.", money(sma20), money(sma50))));
        }

        // Price against its short-term average
        if (p > sma20) {
            score += 0.5;
            reasons.add(new Reason("Price vs 20-day average", Reason.Impact.POSITIVE, String.format(Locale.US,
                "The price (%s) is trading above its 20-day average.", money(p))));
        } else if (p < sma20) {
            score -= 0.5;
            reasons.add(new Reason("Price vs 20-day average", Reason.Impact.NEGATIVE, String.format(Locale.US,
                "The price (%s) is trading below its 20-day average.", money(p))));
        }

        // RSI
        if (rsi < 30) {
            score += 1;
            reasons.add(new Reason("RSI(14)", Reason.Impact.POSITIVE, String.format(Locale.US,
                "Oversold: RSI is %.0f (below 30), so recent selling may be overdone.", rsi)));
        } else if (rsi > 70) {
            score -= 1;
            reasons.add(new Reason("RSI(14)", Reason.Impact.NEGATIVE, String.format(Locale.US,
                "Overbought: RSI is %.0f (above 70), so the recent rise may be stretched.", rsi)));
        } else {
            reasons.add(new Reason("RSI(14)", Reason.Impact.NEUTRAL, String.format(Locale.US,
                "RSI is %.0f, between oversold (30) and overbought (70).", rsi)));
        }

        // Momentum
        if (momentum > 5) {
            score += 0.5;
            reasons.add(new Reason("Momentum", Reason.Impact.POSITIVE, String.format(Locale.US,
                "Strong momentum: up %.1f%% over the last 20 trading days.", momentum)));
        } else if (momentum < -5) {
            score -= 0.5;
            reasons.add(new Reason("Momentum", Reason.Impact.NEGATIVE, String.format(Locale.US,
                "Weak momentum: down %.1f%% over the last 20 trading days.", Math.abs(momentum))));
        } else {
            reasons.add(new Reason("Momentum", Reason.Impact.NEUTRAL, String.format(Locale.US,
                "Little momentum: %+.1f%% over the last 20 trading days.", momentum)));
        }

        // Position-specific rules
        if (unrealised != null) {
            if (unrealised >= 25 && rsi > 65) {
                score -= 1;
                reasons.add(new Reason("Take profit", Reason.Impact.NEGATIVE, String.format(Locale.US,
                    "You are up %.1f%% on your average cost of %s while momentum looks stretched; consider taking some profit.",
                    unrealised, money(holding.averageCost().doubleValue()))));
            } else if (unrealised <= -15 && downtrend) {
                score -= 1;
                reasons.add(new Reason("Cut losses", Reason.Impact.NEGATIVE, String.format(Locale.US,
                    "You are down %.1f%% on your average cost of %s and the trend is still falling.",
                    Math.abs(unrealised), money(holding.averageCost().doubleValue()))));
            } else {
                reasons.add(new Reason("Your position", Reason.Impact.NEUTRAL, String.format(Locale.US,
                    "You hold %d at an average cost of %s (%+.1f%%).",
                    holding.quantity(), money(holding.averageCost().doubleValue()), unrealised)));
            }
        }

        Signal signal = score >= BUY_THRESHOLD ? Signal.BUY : score <= SELL_THRESHOLD ? Signal.SELL : Signal.HOLD;
        int confidence = signal == Signal.HOLD
            ? (int) Math.round(Math.max(0, 1 - Math.abs(score) / BUY_THRESHOLD) * 60 + 30)
            : (int) Math.round(Math.min(1, (Math.abs(score) - 1.5) / 2.0) * 40 + 60);

        Indicators indicators = new Indicators(price, scale(sma20), scale(sma50), scale(rsi, 1),
            scale(momentum, 2), unrealised == null ? null : scale(unrealised, 2), closes.size());
        return new Evaluation(signal, confidence, score, summary(signal, reasons, holding != null), List.copyOf(reasons), indicators);
    }

    /** "Signals favour buying: an uptrend, oversold RSI and strong momentum." */
    private static String summary(Signal signal, List<Reason> reasons, boolean held) {
        Reason.Impact wanted = signal == Signal.BUY ? Reason.Impact.POSITIVE
            : signal == Signal.SELL ? Reason.Impact.NEGATIVE : null;
        List<String> drivers = reasons.stream()
            .filter(r -> wanted != null && r.impact() == wanted)
            .map(SignalEngine::phrase)
            .toList();
        return switch (signal) {
            case BUY -> "Signals favour buying: " + join(drivers) + ".";
            case SELL -> (held ? "Signals favour selling or reducing: " : "Signals favour avoiding for now: ") + join(drivers) + ".";
            case HOLD -> held ? "Mixed signals: no strong case to buy more or sell; hold."
                : "Mixed signals: no strong case to buy yet; keep watching.";
        };
    }

    /** Short phrase for a factor in the summary, e.g. "an uptrend", "oversold RSI" */
    private static String phrase(Reason r) {
        boolean up = r.impact() == Reason.Impact.POSITIVE;
        return switch (r.factor()) {
            case "Trend" -> up ? "an uptrend" : "a downtrend";
            case "Price vs 20-day average" -> up ? "price above its 20-day average" : "price below its 20-day average";
            case "RSI(14)" -> up ? "oversold RSI" : "overbought RSI";
            case "Momentum" -> up ? "strong momentum" : "weak momentum";
            case "Take profit" -> "a large gain on your cost";
            case "Cut losses" -> "a growing loss in a falling trend";
            default -> r.factor().toLowerCase(Locale.ROOT);
        };
    }

    private static String join(List<String> parts) {
        if (parts.isEmpty()) {
            return "several factors";
        }
        if (parts.size() == 1) {
            return parts.get(0);
        }
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
    }

    private static Evaluation insufficient(String why, int closes, BigDecimal price) {
        return new Evaluation(null, null, 0, why,
            List.of(new Reason("Data", Reason.Impact.NEUTRAL, why)),
            new Indicators(price, null, null, null, null, null, closes));
    }

    /** Simple moving average of the last n closes */
    static double average(List<BigDecimal> closes, int n) {
        return closes.subList(closes.size() - n, closes.size()).stream()
            .mapToDouble(BigDecimal::doubleValue).average().orElse(0);
    }

    /** Relative Strength Index over the last n changes (simple average of gains and losses) */
    static double rsi(List<BigDecimal> closes, int n) {
        double gains = 0;
        double losses = 0;
        for (int i = closes.size() - n; i < closes.size(); i++) {
            double change = closes.get(i).doubleValue() - closes.get(i - 1).doubleValue();
            if (change > 0) {
                gains += change;
            } else {
                losses -= change;
            }
        }
        if (losses == 0) {
            return gains == 0 ? 50 : 100;
        }
        double rs = (gains / n) / (losses / n);
        return 100 - 100 / (1 + rs);
    }

    private static String money(double value) {
        return String.format(Locale.US, "%,.2f", value);
    }

    private static BigDecimal scale(double value) {
        return scale(value, 4);
    }

    private static BigDecimal scale(double value, int places) {
        return BigDecimal.valueOf(value).setScale(places, RoundingMode.HALF_UP);
    }

    /** Lookup helper for callers that only need the signal */
    public static Optional<Signal> signalOf(Evaluation evaluation) {
        return Optional.ofNullable(evaluation.signal());
    }
}
