package com.tradingplatform.watchlist.alerts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tradingplatform.api.events.QuotePayload;
import com.tradingplatform.api.services.MarketDataCache;
import com.tradingplatform.watchlist.alerts.evaluator.ActiveAlertIndex;
import com.tradingplatform.watchlist.alerts.evaluator.PriceAlertEvaluator;
import com.tradingplatform.watchlist.alerts.model.AlertDirection;
import com.tradingplatform.watchlist.alerts.model.AlertMode;
import com.tradingplatform.watchlist.alerts.model.DeliveryStatus;
import com.tradingplatform.watchlist.alerts.model.PriceAlert;
import com.tradingplatform.watchlist.alerts.model.PriceAlertTrigger;
import com.tradingplatform.watchlist.alerts.notify.AlertNotifier;
import com.tradingplatform.watchlist.alerts.notify.InAppAlertNotifier;
import com.tradingplatform.watchlist.alerts.notify.PriceAlertNotification;

class PriceAlertEvaluatorTest {

    private static final long ACCOUNT = 1L;
    private static final Instant T0 = Instant.parse("2026-10-06T10:00:00Z");

    private FakePriceAlertStore store;
    private ActiveAlertIndex index;
    private RecordingNotifier notifier;
    private MarketDataCache cache;
    private PriceAlertEvaluator evaluator;

    @BeforeEach
    void setUp() {
        store = new FakePriceAlertStore();
        index = new ActiveAlertIndex();
        notifier = new RecordingNotifier(new InAppAlertNotifier(store));
        cache = new MarketDataCache(Duration.ofHours(24));
        evaluator = new PriceAlertEvaluator(cache, store, store, notifier, index);
    }

    @Test
    @DisplayName("A quote crossing the threshold triggers the alert, with the reason recorded")
    void quoteCrossingThreshold_triggersAlert() {
        PriceAlert above = watch(store.seed(ACCOUNT, "AAPL", AlertDirection.ABOVE, "190.00", AlertMode.EVERY_QUOTE));
        PriceAlert below = watch(store.seed(ACCOUNT, "MSFT", AlertDirection.BELOW, "400", AlertMode.EVERY_QUOTE));

        int firedAapl = evaluator.onQuote("AAPL", quote("AAPL", "191.20"), T0);
        int firedMsft = evaluator.onQuote("MSFT", quote("MSFT", "399.50"), T0);

        assertEquals(1, firedAapl);
        assertEquals(1, firedMsft);
        PriceAlertTrigger trigger = store.triggersFor(above).get(0);
        assertEquals("AAPL 191.2 >= 190 (ABOVE, EVERY_QUOTE)", trigger.getReason());
        assertEquals(DeliveryStatus.DELIVERED, trigger.getDeliveryStatus());
        assertEquals(1, above.getTriggerCount());
        assertEquals(new BigDecimal("191.20"), above.getLastTriggeredPrice());
        assertEquals(1, store.triggersFor(below).size());
    }

    @Test
    @DisplayName("Touching the level counts as crossing it")
    void quoteExactlyAtThreshold_triggersAlert() {
        PriceAlert alert = watch(store.seed(ACCOUNT, "AAPL", AlertDirection.ABOVE, "190", AlertMode.ONCE));

        assertEquals(1, evaluator.onQuote("AAPL", quote("AAPL", "190.000"), T0));
        assertEquals(1, alert.getTriggerCount());
    }

    @Test
    @DisplayName("A quote short of the threshold does not trigger the alert")
    void quoteShortOfThreshold_doesNotTrigger() {
        PriceAlert above = watch(store.seed(ACCOUNT, "AAPL", AlertDirection.ABOVE, "190", AlertMode.ONCE));
        PriceAlert below = watch(store.seed(ACCOUNT, "MSFT", AlertDirection.BELOW, "400", AlertMode.ONCE));

        assertEquals(0, evaluator.onQuote("AAPL", quote("AAPL", "189.99"), T0));
        assertEquals(0, evaluator.onQuote("MSFT", quote("MSFT", "400.01"), T0));

        assertTrue(store.triggers.isEmpty());
        assertTrue(notifier.received.isEmpty());
        assertTrue(above.isActive());
        assertTrue(below.isActive());
    }

    @Test
    @DisplayName("ONCE: fires on the first quote past the level, then deactivates")
    void onceMode_deactivatesAfterTrigger() {
        PriceAlert alert = watch(store.seed(ACCOUNT, "AAPL", AlertDirection.ABOVE, "190", AlertMode.ONCE));

        evaluator.onQuote("AAPL", quote("AAPL", "191"), T0);
        int firedAgain = evaluator.onQuote("AAPL", quote("AAPL", "195"), T0.plusSeconds(60));

        assertFalse(alert.isActive());
        assertEquals(PriceAlert.TRIGGERED_ONCE, alert.getDeactivatedReason());
        assertEquals(0, firedAgain);
        assertEquals(1, store.triggersFor(alert).size());
    }

    @Test
    @DisplayName("EVERY_QUOTE: fires on each new quote past the level, never twice for the same quote")
    void everyQuoteMode_firesOnEachNewQuote_notSameQuoteTwice() {
        PriceAlert alert = watch(store.seed(ACCOUNT, "AAPL", AlertDirection.ABOVE, "190", AlertMode.EVERY_QUOTE));

        evaluator.onQuote("AAPL", quote("AAPL", "191"), T0);
        evaluator.onQuote("AAPL", quote("AAPL", "191"), T0);              // same quote again
        evaluator.onQuote("AAPL", quote("AAPL", "189"), T0.plusSeconds(30)); // back under the level
        evaluator.onQuote("AAPL", quote("AAPL", "192"), T0.plusSeconds(60));

        assertTrue(alert.isActive());
        assertEquals(2, alert.getTriggerCount());
        assertEquals(2, store.triggersFor(alert).size());
    }

    @Test
    @DisplayName("Per-quote lookup: a symbol nobody watches never reaches the database")
    void unwatchedSymbol_skipsDbLookup() {
        watch(store.seed(ACCOUNT, "AAPL", AlertDirection.ABOVE, "190", AlertMode.ONCE));

        evaluator.onQuote("TSLA", quote("TSLA", "999"), T0);
        evaluator.onQuote("TSLA", quote("TSLA", "999"), T0.plusSeconds(1));

        assertEquals(0, store.findActiveBySymbolCalls.get());
    }

    @Test
    @DisplayName("Stale quotes still fire (e.g. the last price while the market is closed), marked in the reason")
    void staleQuote_triggers() {
        PriceAlert alert = watch(store.seed(ACCOUNT, "AAPL", AlertDirection.ABOVE, "190", AlertMode.ONCE));

        assertEquals(1, evaluator.onQuote("AAPL", stale("AAPL", "200", T0.minusSeconds(3600)), T0));

        assertFalse(alert.isActive());
        PriceAlertTrigger trigger = store.triggersFor(alert).get(0);
        assertEquals("AAPL 200 >= 190 (ABOVE, ONCE, stale quote)", trigger.getReason());
        assertEquals(T0.minusSeconds(3600), trigger.getQuoteAt(), "a stale quote is keyed on when it was observed");
    }

    @Test
    @DisplayName("EVERY_QUOTE on a stale price: the same old price arriving every refresh fires once")
    void staleQuote_sameObservationRepeated_firesOnce_newObservationFiresAgain() {
        PriceAlert alert = watch(store.seed(ACCOUNT, "AAPL", AlertDirection.ABOVE, "190", AlertMode.EVERY_QUOTE));
        Instant lastClose = T0.minusSeconds(3600);

        evaluator.onQuote("AAPL", stale("AAPL", "200", lastClose), T0);
        evaluator.onQuote("AAPL", stale("AAPL", "200", lastClose), T0.plusSeconds(300));
        evaluator.onQuote("AAPL", stale("AAPL", "200", lastClose), T0.plusSeconds(600));
        assertEquals(1, alert.getTriggerCount());

        // The market opens: a fresh quote is new data and fires again
        evaluator.onQuote("AAPL", quote("AAPL", "201"), T0.plusSeconds(900));
        assertEquals(2, alert.getTriggerCount());
        assertTrue(alert.isActive());
    }

    @Test
    @DisplayName("A stale quote below an ABOVE level does not fire")
    void staleQuote_notCrossed_doesNotTrigger() {
        watch(store.seed(ACCOUNT, "AAPL", AlertDirection.ABOVE, "250", AlertMode.ONCE));

        assertEquals(0, evaluator.onQuote("AAPL", stale("AAPL", "200", T0.minusSeconds(3600)), T0));
        assertTrue(store.triggers.isEmpty());
    }

    @Test
    @DisplayName("A quote without a price is ignored")
    void quoteWithoutPrice_isIgnored() {
        watch(store.seed(ACCOUNT, "AAPL", AlertDirection.ABOVE, "190", AlertMode.ONCE));

        QuotePayload noPrice = new QuotePayload("AAPL", null, null, null, "USD", null, null, null, "CLOSED", true, T0);

        assertEquals(0, evaluator.onQuote("AAPL", noPrice, T0));
        assertTrue(store.triggers.isEmpty());
    }

    @Test
    @DisplayName("A triggered alert is delivered through notifications rather than to a log")
    void alertDeliveredThroughNotifier_notLog() {
        PriceAlert alert = watch(store.seed(ACCOUNT, "AAPL", AlertDirection.BELOW, "150", AlertMode.ONCE));

        evaluator.onQuote("AAPL", quote("AAPL", "149.5"), T0);

        assertEquals(1, notifier.received.size());
        PriceAlertNotification sent = notifier.received.get(0);
        assertEquals(ACCOUNT, sent.accountId());
        assertEquals(alert.getAlertId(), sent.alertId());
        assertEquals("AAPL", sent.symbol());
        assertEquals(new BigDecimal("149.5"), sent.price());
        assertEquals(DeliveryStatus.DELIVERED, store.triggers.get(sent.triggerId()).getDeliveryStatus());
    }

    @Test
    @DisplayName("When the notifier raises: trigger FAILED, alert stays active, other alerts still fire, retry succeeds")
    void notifierThrows_recordsFailed_keepsAlertActive_otherAlertsStillProcessed() {
        PriceAlert failing = watch(store.seed(ACCOUNT, "AAPL", AlertDirection.ABOVE, "190", AlertMode.ONCE));
        PriceAlert healthy = watch(store.seed(2L, "AAPL", AlertDirection.ABOVE, "180", AlertMode.ONCE));
        notifier.failFor = failing.getAlertId();

        int fired = evaluator.onQuote("AAPL", quote("AAPL", "195"), T0);

        assertEquals(1, fired);
        assertFalse(healthy.isActive());
        assertTrue(failing.isActive());
        assertNull(failing.getLastQuoteAt());
        PriceAlertTrigger failed = store.triggersFor(failing).get(0);
        assertEquals(DeliveryStatus.FAILED, failed.getDeliveryStatus());

        // Notifications recover: the same quote is retried on the next run and reuses the trigger row
        notifier.failFor = null;
        int retried = evaluator.onQuote("AAPL", quote("AAPL", "195"), T0);

        assertEquals(1, retried);
        assertFalse(failing.isActive());
        assertEquals(1, store.triggersFor(failing).size());
        assertEquals(DeliveryStatus.DELIVERED, store.triggersFor(failing).get(0).getDeliveryStatus());
        assertEquals(1, store.triggersFor(healthy).size());
    }

    @Test
    @DisplayName("Scheduled run reads the market-data cache for watched symbols only")
    void evaluateLatestQuotes_readsCacheForWatchedSymbols() {
        PriceAlert alert = store.seed(ACCOUNT, "AAPL", AlertDirection.ABOVE, "190", AlertMode.ONCE);
        cache.updateQuote("AAPL", quote("AAPL", "191"), T0);
        cache.updateQuote("TSLA", quote("TSLA", "250"), T0);

        evaluator.evaluateLatestQuotes();

        assertFalse(alert.isActive());
        assertEquals(1, store.findActiveBySymbolCalls.get());
    }

    @Test
    @DisplayName("A new alert already past its level fires on the next run")
    void newAlert_isCheckedAgainstCurrentQuote() {
        cache.updateQuote("AAPL", quote("AAPL", "191"), T0);
        watch(store.seed(ACCOUNT, "AAPL", AlertDirection.BELOW, "100", AlertMode.ONCE));
        evaluator.evaluateLatestQuotes(); // quote seen, nothing crossed

        PriceAlert added = watch(store.seed(ACCOUNT, "AAPL", AlertDirection.ABOVE, "190", AlertMode.ONCE));
        evaluator.evaluateLatestQuotes();

        assertFalse(added.isActive());
    }

    private PriceAlert watch(PriceAlert alert) {
        index.watch(alert.getSymbol());
        return alert;
    }

    private static QuotePayload stale(String symbol, String price, Instant observedAt) {
        BigDecimal p = new BigDecimal(price);
        return new QuotePayload(symbol, p, p, p, "USD", null, null, null, "unknown", true, observedAt);
    }

    private static QuotePayload quote(String symbol, String price) {
        BigDecimal p = new BigDecimal(price);
        return new QuotePayload(symbol, p, p, p, "USD", BigDecimal.ZERO, BigDecimal.ZERO, p, "REGULAR", false, T0);
    }

    /** Wraps the real in-app notifier, records what it was given, and can be told to fail for one alert */
    private static final class RecordingNotifier implements AlertNotifier {
        private final AlertNotifier delegate;
        final List<PriceAlertNotification> received = new ArrayList<>();
        Long failFor;

        RecordingNotifier(AlertNotifier delegate) {
            this.delegate = delegate;
        }

        @Override
        public void notify(PriceAlertNotification notification) {
            if (notification.alertId().equals(failFor)) {
                throw new IllegalStateException("notifications unavailable");
            }
            delegate.notify(notification);
            received.add(notification);
        }
    }
}
