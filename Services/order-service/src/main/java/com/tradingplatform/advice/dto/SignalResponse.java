package com.tradingplatform.advice.dto;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import com.tradingplatform.advice.engine.Reason;
import com.tradingplatform.advice.engine.Signal;
import com.tradingplatform.advice.engine.SignalEngine;
import com.tradingplatform.domain.model.Instrument;

/**
 * Advice for one instrument.
 *
 * @param symbol      instrument symbol
 * @param name        instrument name
 * @param currency    ISO 4217 code of the prices
 * @param held        the account holds it
 * @param watched     it is on one of the account's watchlists
 * @param signal      BUY, SELL or HOLD; null when there was not enough data (see summary)
 * @param confidence  0-100; null without a signal
 * @param summary     one-sentence explanation (or why there is no signal)
 * @param reasons     every factor considered, with its effect
 * @param indicators  the numbers behind the signal
 */
public record SignalResponse(
    String symbol,
    String name,
    String currency,
    boolean held,
    boolean watched,
    Signal signal,
    Integer confidence,
    String summary,
    List<ReasonResponse> reasons,
    Indicators indicators
) {
    /** SELL first (most urgent for holdings), then BUY, HOLD, and instruments without a signal; then by symbol */
    public static final Comparator<SignalResponse> DISPLAY_ORDER = Comparator
        .comparingInt((SignalResponse s) -> s.signal() == null ? 3 : switch (s.signal()) {
            case SELL -> 0;
            case BUY -> 1;
            case HOLD -> 2;
        })
        .thenComparing(SignalResponse::symbol);

    public record ReasonResponse(String factor, Reason.Impact impact, String detail) {
    }

    public record Indicators(BigDecimal price, BigDecimal sma20, BigDecimal sma50, BigDecimal rsi14,
                             BigDecimal momentum20Percent, BigDecimal unrealisedPercent, int daysOfHistory) {
    }

    public static SignalResponse from(Instrument instrument, boolean held, boolean watched,
                                      SignalEngine.Evaluation evaluation) {
        SignalEngine.Indicators i = evaluation.indicators();
        return new SignalResponse(
            instrument.getSymbol(),
            instrument.getName(),
            instrument.getCurrency(),
            held,
            watched,
            evaluation.signal(),
            evaluation.confidence(),
            evaluation.summary(),
            evaluation.reasons().stream().map(r -> new ReasonResponse(r.factor(), r.impact(), r.detail())).toList(),
            new Indicators(i.price(), i.sma20(), i.sma50(), i.rsi14(), i.momentum20Percent(), i.unrealisedPercent(), i.closes())
        );
    }
}
