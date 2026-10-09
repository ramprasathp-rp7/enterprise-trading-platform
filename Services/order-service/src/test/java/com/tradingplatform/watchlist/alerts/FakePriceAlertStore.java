package com.tradingplatform.watchlist.alerts;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.dao.DuplicateKeyException;

import com.tradingplatform.watchlist.alerts.mapper.PriceAlertMapper;
import com.tradingplatform.watchlist.alerts.mapper.PriceAlertTriggerMapper;
import com.tradingplatform.watchlist.alerts.model.AlertDirection;
import com.tradingplatform.watchlist.alerts.model.AlertMode;
import com.tradingplatform.watchlist.alerts.model.DeliveryStatus;
import com.tradingplatform.watchlist.alerts.model.PriceAlert;
import com.tradingplatform.watchlist.alerts.model.PriceAlertTrigger;

/** In-memory PRICE_ALERTS and PRICE_ALERT_TRIGGERS that mirror the SQL in both mappers. */
public class FakePriceAlertStore implements PriceAlertMapper, PriceAlertTriggerMapper {

    public final Map<Long, PriceAlert> alerts = new LinkedHashMap<>();
    public final Map<Long, PriceAlertTrigger> triggers = new LinkedHashMap<>();
    /** How many times the evaluator went to the database for a symbol's alerts */
    public final AtomicInteger findActiveBySymbolCalls = new AtomicInteger();

    private final AtomicLong alertIds = new AtomicLong();
    private final AtomicLong triggerIds = new AtomicLong();

    /** Seeds an active alert directly, as if created through the API */
    public PriceAlert seed(long accountId, String symbol, AlertDirection direction, String threshold, AlertMode mode) {
        PriceAlert alert = new PriceAlert(accountId, symbol, direction, new BigDecimal(threshold), mode);
        insert(alert);
        return alert;
    }

    // PriceAlertMapper

    @Override
    public List<PriceAlert> findByAccountId(Long accountId, Boolean active, String symbol) {
        return alerts.values().stream()
            .filter(a -> a.getAccountId().equals(accountId))
            .filter(a -> active == null || a.isActive() == active)
            .filter(a -> symbol == null || a.getSymbol().equals(symbol))
            .sorted(Comparator.comparing(PriceAlert::getAlertId).reversed())
            .toList();
    }

    @Override
    public PriceAlert findById(Long alertId, Long accountId) {
        PriceAlert alert = alerts.get(alertId);
        return alert != null && alert.getAccountId().equals(accountId) ? alert : null;
    }

    @Override
    public int countByAccountId(Long accountId) {
        return (int) alerts.values().stream().filter(a -> a.getAccountId().equals(accountId)).count();
    }

    @Override
    public int countActiveByAccountId(Long accountId) {
        return (int) alerts.values().stream().filter(a -> a.getAccountId().equals(accountId) && a.isActive()).count();
    }

    @Override
    public boolean existsActiveRule(Long accountId, String symbol, AlertDirection direction, BigDecimal threshold, AlertMode mode) {
        return alerts.values().stream().anyMatch(a -> a.isActive()
            && a.getAccountId().equals(accountId) && a.getSymbol().equals(symbol)
            && a.getDirection() == direction && a.getThreshold().compareTo(threshold) == 0 && a.getMode() == mode);
    }

    @Override
    public int insert(PriceAlert alert) {
        if (existsActiveRule(alert.getAccountId(), alert.getSymbol(), alert.getDirection(), alert.getThreshold(), alert.getMode())) {
            throw new DuplicateKeyException("uq_price_alerts_active_rule");
        }
        alert.setAlertId(alertIds.incrementAndGet());
        alert.setActive(true);
        alert.setCreatedAt(Instant.now());
        alert.setUpdatedAt(alert.getCreatedAt());
        alerts.put(alert.getAlertId(), alert);
        return 1;
    }

    @Override
    public int updateActive(Long alertId, Long accountId, boolean active, String deactivatedReason) {
        PriceAlert alert = findById(alertId, accountId);
        if (alert == null) {
            return 0;
        }
        alert.setActive(active);
        alert.setDeactivatedReason(deactivatedReason);
        return 1;
    }

    @Override
    public int delete(Long alertId, Long accountId) {
        if (findById(alertId, accountId) == null) {
            return 0;
        }
        alerts.remove(alertId);
        triggers.values().removeIf(t -> t.getAlertId().equals(alertId));
        return 1;
    }

    @Override
    public List<String> findActiveSymbols() {
        return alerts.values().stream().filter(PriceAlert::isActive).map(PriceAlert::getSymbol).distinct().toList();
    }

    @Override
    public List<PriceAlert> findActiveBySymbol(String symbol) {
        findActiveBySymbolCalls.incrementAndGet();
        return alerts.values().stream().filter(a -> a.isActive() && a.getSymbol().equals(symbol)).toList();
    }

    @Override
    public int recordFired(Long alertId, BigDecimal price, Instant quoteAt, boolean deactivate) {
        PriceAlert alert = alerts.get(alertId);
        if (alert == null || !alert.isActive()
                || (alert.getLastQuoteAt() != null && !alert.getLastQuoteAt().isBefore(quoteAt))) {
            return 0;
        }
        alert.setLastQuoteAt(quoteAt);
        alert.setLastTriggeredAt(Instant.now());
        alert.setLastTriggeredPrice(price);
        alert.setTriggerCount(alert.getTriggerCount() + 1);
        if (deactivate) {
            alert.setActive(false);
            alert.setDeactivatedReason(PriceAlert.TRIGGERED_ONCE);
        }
        return 1;
    }

    // PriceAlertTriggerMapper

    @Override
    public int upsertPending(PriceAlertTrigger trigger) {
        PriceAlertTrigger existing = triggers.values().stream()
            .filter(t -> t.getAlertId().equals(trigger.getAlertId()) && t.getQuoteAt().equals(trigger.getQuoteAt()))
            .findFirst().orElse(null);
        if (existing != null) {
            if (existing.getDeliveryStatus() == DeliveryStatus.DELIVERED) {
                return 0;
            }
            existing.setDeliveryStatus(DeliveryStatus.PENDING);
            existing.setReason(trigger.getReason());
            trigger.setTriggerId(existing.getTriggerId());
            return 1;
        }
        PriceAlertTrigger row = copyOf(trigger);
        row.setTriggerId(triggerIds.incrementAndGet());
        row.setTriggeredAt(Instant.now());
        row.setDeliveryStatus(DeliveryStatus.PENDING);
        triggers.put(row.getTriggerId(), row);
        trigger.setTriggerId(row.getTriggerId());
        return 1;
    }

    @Override
    public int updateDeliveryStatus(Long triggerId, DeliveryStatus status) {
        PriceAlertTrigger trigger = triggers.get(triggerId);
        if (trigger == null) {
            return 0;
        }
        trigger.setDeliveryStatus(status);
        return 1;
    }

    /** The stored trigger row, or null */
    public PriceAlertTrigger trigger(Long triggerId) {
        return triggers.get(triggerId);
    }

    /** Channels recorded per trigger id (the real table stores them in channels / in_app) */
    public final java.util.Map<Long, String> channelsByTrigger = new java.util.HashMap<>();
    public final java.util.Map<Long, Boolean> inAppByTrigger = new java.util.HashMap<>();

    @Override
    public int updateChannels(Long triggerId, String channels, boolean inApp) {
        if (!triggers.containsKey(triggerId)) {
            return 0;
        }
        channelsByTrigger.put(triggerId, channels);
        inAppByTrigger.put(triggerId, inApp);
        return 1;
    }

    @Override
    public List<PriceAlertTrigger> findByAlertId(Long alertId, Long accountId) {
        return triggers.values().stream()
            .filter(t -> t.getAlertId().equals(alertId) && t.getAccountId().equals(accountId))
            .sorted(Comparator.comparing(PriceAlertTrigger::getTriggerId).reversed())
            .toList();
    }

    @Override
    public List<PriceAlertTrigger> findHistoryByAccountId(Long accountId, int limit) {
        return triggers.values().stream()
            .filter(t -> t.getAccountId().equals(accountId))
            .sorted(Comparator.comparing(PriceAlertTrigger::getTriggerId).reversed())
            .limit(limit)
            .toList();
    }

    @Override
    public List<PriceAlertTrigger> findRecentByAccountId(Long accountId, Instant since, int limit) {
        return triggers.values().stream()
            .filter(t -> t.getAccountId().equals(accountId))
            .filter(t -> since == null || t.getTriggeredAt().isAfter(since))
            .sorted(Comparator.comparing(PriceAlertTrigger::getTriggerId).reversed())
            .limit(limit)
            .toList();
    }

    public List<PriceAlertTrigger> triggersFor(PriceAlert alert) {
        return new ArrayList<>(triggers.values().stream()
            .filter(t -> Objects.equals(t.getAlertId(), alert.getAlertId()))
            .toList());
    }

    private static PriceAlertTrigger copyOf(PriceAlertTrigger t) {
        PriceAlertTrigger c = new PriceAlertTrigger();
        c.setAlertId(t.getAlertId());
        c.setAccountId(t.getAccountId());
        c.setPrice(t.getPrice());
        c.setQuoteAt(t.getQuoteAt());
        c.setReason(t.getReason());
        c.setSymbol(t.getSymbol());
        c.setDirection(t.getDirection());
        c.setThreshold(t.getThreshold());
        c.setMode(t.getMode());
        return c;
    }
}
