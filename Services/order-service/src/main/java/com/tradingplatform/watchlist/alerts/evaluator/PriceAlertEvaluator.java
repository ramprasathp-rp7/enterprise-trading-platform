package com.tradingplatform.watchlist.alerts.evaluator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.tradingplatform.api.events.QuotePayload;
import com.tradingplatform.api.services.MarketDataCache;
import com.tradingplatform.watchlist.alerts.mapper.PriceAlertMapper;
import com.tradingplatform.watchlist.alerts.mapper.PriceAlertTriggerMapper;
import com.tradingplatform.watchlist.alerts.model.AlertMode;
import com.tradingplatform.watchlist.alerts.model.DeliveryStatus;
import com.tradingplatform.watchlist.alerts.model.PriceAlert;
import com.tradingplatform.watchlist.alerts.model.PriceAlertTrigger;
import com.tradingplatform.watchlist.alerts.notify.AlertNotifier;
import com.tradingplatform.watchlist.alerts.notify.PriceAlertNotification;

/**
 * Checks market-data quotes against active price alerts.
 *
 * Source: the same MarketDataCache that serves GET /api/v1/market-data/quotes/{symbol}/latest
 * (the watchlist's live price). The cache is only read. Orders and trade-events are not consumed:
 * neither says anything about what a customer is curious about.
 *
 * Per quote:
 * 1. ActiveAlertIndex decides whether anyone watches the symbol and whether this quote is new.
 *    Most quotes stop here without a database call.
 * 2. Quotes without a price are ignored. Stale quotes (Fauxnance serving its last price, for example
 *    while the market is closed) are evaluated too, so an alert set past the last price still fires.
 *    A stale quote is identified by when it was observed (quoteAsOf), not when the cache received it:
 *    the same old price arriving again every refresh fires an alert at most once.
 * 3. Active alerts for the symbol are loaded through the partial index on PRICE_ALERTS(symbol).
 * 4. Each alert fires when crossed (ABOVE: price >= threshold, BELOW: price <= threshold) and the
 *    quote is newer than the last one that fired it.
 *
 * A firing is recorded as a PENDING trigger with its reason, handed to the AlertNotifier, and then
 * written back to the alert (ONCE deactivates, EVERY_QUOTE stays active). If the notifier throws,
 * the trigger becomes FAILED, the alert is left as it was, and the quote is not marked evaluated,
 * so the next run retries it. One failing alert never stops the others.
 */
@Component
public class PriceAlertEvaluator {

    private static final Logger logger = LoggerFactory.getLogger(PriceAlertEvaluator.class);

    /** Result of checking one alert against one quote */
    enum Outcome { NOT_CROSSED, FIRED, FAILED }

    private final MarketDataCache marketDataCache;
    private final PriceAlertMapper alertMapper;
    private final PriceAlertTriggerMapper triggerMapper;
    private final AlertNotifier notifier;
    private final ActiveAlertIndex index;

    public PriceAlertEvaluator(MarketDataCache marketDataCache,
                               PriceAlertMapper alertMapper,
                               PriceAlertTriggerMapper triggerMapper,
                               AlertNotifier notifier,
                               ActiveAlertIndex index) {
        this.marketDataCache = marketDataCache;
        this.alertMapper = alertMapper;
        this.triggerMapper = triggerMapper;
        this.notifier = notifier;
        this.index = index;
    }

    /** Evaluates the newest cached quote of every watched symbol. */
    @Scheduled(fixedDelayString = "${price-alerts.evaluate.interval-ms:30000}",
               initialDelayString = "${price-alerts.evaluate.initial-delay-ms:30000}")
    public void evaluateLatestQuotes() {
        try {
            index.replaceWatched(alertMapper.findActiveSymbols());
        } catch (RuntimeException e) {
            logger.warn("Price alert evaluation skipped: could not load active symbols", e);
            return;
        }

        int fired = 0;
        for (String symbol : index.watchedSymbols()) {
            Optional<QuotePayload> quote = marketDataCache.getLatestQuote(symbol);
            Optional<Instant> receivedAt = marketDataCache.getQuoteTimestamp(symbol);
            if (quote.isPresent() && receivedAt.isPresent()) {
                fired += onQuote(symbol, quote.get(), receivedAt.get());
            }
        }
        if (fired > 0) {
            logger.info("Price alert evaluation fired {} alert(s)", fired);
        }
    }

    /**
     * Decides whether any customer cares about this quote and fires their alerts.
     *
     * @return number of alerts that fired and were delivered
     */
    public int onQuote(String symbol, QuotePayload quote, Instant quoteAt) {
        if (!index.needsEvaluation(symbol, quoteAt)) {
            return 0;
        }
        BigDecimal price = quote.price();
        if (price == null) {
            index.markEvaluated(symbol, quoteAt);
            return 0;
        }
        boolean stale = Boolean.TRUE.equals(quote.stale());
        // A stale quote is the same observation every refresh: key it on when it was observed
        Instant observedAt = stale && quote.quoteAsOf() != null ? quote.quoteAsOf() : quoteAt;

        List<PriceAlert> alerts;
        try {
            alerts = alertMapper.findActiveBySymbol(symbol);
        } catch (RuntimeException e) {
            logger.warn("Could not load price alerts for {}; will retry", symbol, e);
            return 0;
        }

        int fired = 0;
        boolean allSettled = true;
        for (PriceAlert alert : alerts) {
            Outcome outcome;
            try {
                outcome = evaluate(alert, price, observedAt, stale);
            } catch (RuntimeException e) {
                logger.warn("Price alert {} could not be evaluated; will retry", alert.getAlertId(), e);
                outcome = Outcome.FAILED;
            }
            if (outcome == Outcome.FIRED) {
                fired++;
            } else if (outcome == Outcome.FAILED) {
                allSettled = false;
            }
        }
        if (allSettled) {
            index.markEvaluated(symbol, quoteAt);
        }
        return fired;
    }

    Outcome evaluate(PriceAlert alert, BigDecimal price, Instant quoteAt) {
        return evaluate(alert, price, quoteAt, false);
    }

    Outcome evaluate(PriceAlert alert, BigDecimal price, Instant quoteAt, boolean stale) {
        if (alert.getLastQuoteAt() != null && !quoteAt.isAfter(alert.getLastQuoteAt())) {
            return Outcome.NOT_CROSSED;
        }
        if (!alert.getDirection().isCrossedBy(price, alert.getThreshold())) {
            return Outcome.NOT_CROSSED;
        }

        PriceAlertTrigger trigger = new PriceAlertTrigger(alert, price, quoteAt, reason(alert, price, stale));
        triggerMapper.upsertPending(trigger);
        if (trigger.getTriggerId() != null) {
            try {
                notifier.notify(PriceAlertNotification.from(trigger));
            } catch (RuntimeException e) {
                triggerMapper.updateDeliveryStatus(trigger.getTriggerId(), DeliveryStatus.FAILED);
                logger.warn("Price alert {} fired but notification failed; alert stays active and will retry: {}",
                    alert.getAlertId(), trigger.getReason(), e);
                return Outcome.FAILED;
            }
        }
        // No triggerId means this quote was already delivered; recordFired is idempotent per quote.
        alertMapper.recordFired(alert.getAlertId(), price, quoteAt, alert.getMode() == AlertMode.ONCE);
        logger.info("Price alert fired: accountId={}, alertId={}, {}",
            alert.getAccountId(), alert.getAlertId(), trigger.getReason());
        return Outcome.FIRED;
    }

    /** Why the alert fired, e.g. "AAPL 191.2 >= 190 (ABOVE, ONCE)" */
    static String reason(PriceAlert alert, BigDecimal price) {
        return reason(alert, price, false);
    }

    /** As above; a stale price is marked, e.g. "AAPL 191.2 >= 190 (ABOVE, ONCE, stale quote)" */
    static String reason(PriceAlert alert, BigDecimal price, boolean stale) {
        return alert.getSymbol() + " " + plain(price) + " " + alert.getDirection().operator() + " "
            + plain(alert.getThreshold()) + " (" + alert.getDirection() + ", " + alert.getMode()
            + (stale ? ", stale quote" : "") + ")";
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
