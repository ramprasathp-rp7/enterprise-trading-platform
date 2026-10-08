package com.tradingplatform.watchlist.alerts.evaluator;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * In-memory index that answers, per quote, "does any customer care?" without touching the database.
 *
 * - watched: symbols with at least one active alert. Rebuilt from the database on every evaluation
 *   run and updated immediately when an alert is created or resumed.
 * - lastSeen: reception time of the newest quote already evaluated per symbol, so an unchanged
 *   quote is skipped. Forgotten when an alert is added, so a new alert is checked against the
 *   current price on the next run.
 *
 * Only quotes that pass both checks reach the indexed PRICE_ALERTS lookup.
 */
@Component
public class ActiveAlertIndex {

    private final Set<String> watched = ConcurrentHashMap.newKeySet();
    private final Map<String, Instant> lastSeen = new ConcurrentHashMap<>();

    /** Replaces the watched set with what the database says is active */
    public void replaceWatched(Collection<String> activeSymbols) {
        Set<String> fresh = Set.copyOf(activeSymbols);
        watched.retainAll(fresh);
        watched.addAll(fresh);
        lastSeen.keySet().retainAll(fresh);
    }

    /** An alert on this symbol was created or resumed */
    public void watch(String symbol) {
        watched.add(symbol);
        lastSeen.remove(symbol);
    }

    public boolean isWatched(String symbol) {
        return watched.contains(symbol);
    }

    public Set<String> watchedSymbols() {
        return Set.copyOf(watched);
    }

    /** True when someone watches the symbol and this quote has not been evaluated yet */
    public boolean needsEvaluation(String symbol, Instant quoteAt) {
        if (!watched.contains(symbol)) {
            return false;
        }
        Instant seen = lastSeen.get(symbol);
        return seen == null || quoteAt.isAfter(seen);
    }

    public void markEvaluated(String symbol, Instant quoteAt) {
        lastSeen.put(symbol, quoteAt);
    }
}
