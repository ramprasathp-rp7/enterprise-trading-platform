package com.tradingplatform.watchlist.alerts.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.domain.exception.InstrumentNotFoundException;
import com.tradingplatform.domain.exception.InvalidOrderException;
import com.tradingplatform.domain.model.Instrument;
import com.tradingplatform.watchlist.alerts.dto.CreatePriceAlertRequest;
import com.tradingplatform.watchlist.alerts.dto.PriceAlertResponse;
import com.tradingplatform.watchlist.alerts.dto.PriceAlertTriggerResponse;
import com.tradingplatform.watchlist.alerts.evaluator.ActiveAlertIndex;
import com.tradingplatform.watchlist.alerts.exception.PriceAlertConflictException;
import com.tradingplatform.watchlist.alerts.exception.PriceAlertNotFoundException;
import com.tradingplatform.watchlist.alerts.mapper.PriceAlertMapper;
import com.tradingplatform.watchlist.alerts.mapper.PriceAlertTriggerMapper;
import com.tradingplatform.watchlist.alerts.model.PriceAlert;

/**
 * Price alerts: per-account price levels checked against market data by PriceAlertEvaluator.
 *
 * Rules:
 * - the symbol must exist in INSTRUMENTS (INS-404); threshold must be positive (VAL-422);
 * - an account holds at most price-alerts.max-active-per-account active alerts and
 *   price-alerts.max-per-account alerts in total (ALR-409), so an unbounded creation route cannot
 *   flood the evaluator that checks every quote;
 * - the same active rule (symbol, direction, threshold, mode) cannot exist twice (ALR-409);
 * - an alert belonging to another account is reported as not found (ALR-404).
 */
@Service
public class PriceAlertService {

    private static final Logger logger = LoggerFactory.getLogger(PriceAlertService.class);

    /** Most triggers returned to the notification bell per poll */
    static final int RECENT_TRIGGER_LIMIT = 50;
    /** NUMERIC(18,6) leaves 12 integer digits */
    private static final BigDecimal MAX_THRESHOLD = new BigDecimal("1000000000000");

    private final PriceAlertMapper alertMapper;
    private final PriceAlertTriggerMapper triggerMapper;
    private final InstrumentMapper instrumentMapper;
    private final ActiveAlertIndex index;
    private final int maxActivePerAccount;
    private final int maxPerAccount;

    public PriceAlertService(PriceAlertMapper alertMapper,
                             PriceAlertTriggerMapper triggerMapper,
                             InstrumentMapper instrumentMapper,
                             ActiveAlertIndex index,
                             @Value("${price-alerts.max-active-per-account:20}") int maxActivePerAccount,
                             @Value("${price-alerts.max-per-account:100}") int maxPerAccount) {
        this.alertMapper = alertMapper;
        this.triggerMapper = triggerMapper;
        this.instrumentMapper = instrumentMapper;
        this.index = index;
        this.maxActivePerAccount = maxActivePerAccount;
        this.maxPerAccount = maxPerAccount;
    }

    /** The account's alerts, newest first, optionally filtered */
    @Transactional(readOnly = true)
    public List<PriceAlertResponse> getAlerts(long accountId, Boolean active, String symbol) {
        String normalised = symbol == null || symbol.isBlank() ? null : symbol.trim().toUpperCase(Locale.ROOT);
        return alertMapper.findByAccountId(accountId, active, normalised).stream()
            .map(PriceAlertResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public PriceAlertResponse getAlert(long accountId, long alertId) {
        return PriceAlertResponse.from(requireAlert(accountId, alertId));
    }

    @Transactional
    public PriceAlertResponse createAlert(long accountId, CreatePriceAlertRequest request) {
        BigDecimal threshold = request.threshold();
        if (request.direction() == null || request.mode() == null) {
            throw new InvalidOrderException("direction and mode are required");
        }
        if (threshold == null || threshold.signum() <= 0 || threshold.compareTo(MAX_THRESHOLD) >= 0) {
            throw new InvalidOrderException("threshold must be greater than 0 and less than " + MAX_THRESHOLD);
        }

        String requested = request.symbol() == null ? "" : request.symbol().trim().toUpperCase(Locale.ROOT);
        Instrument instrument = requested.isEmpty() ? null : instrumentMapper.findBySymbol(requested);
        if (instrument == null) {
            throw new InstrumentNotFoundException(requested);
        }
        String symbol = instrument.getSymbol();

        if (alertMapper.countByAccountId(accountId) >= maxPerAccount) {
            throw new PriceAlertConflictException(
                "Price alert limit reached (" + maxPerAccount + " per account); delete an alert first");
        }
        requireActiveSlot(accountId);
        if (alertMapper.existsActiveRule(accountId, symbol, request.direction(), threshold, request.mode())) {
            throw duplicateRule(symbol);
        }

        PriceAlert alert = new PriceAlert(accountId, symbol, request.direction(), threshold, request.mode());
        try {
            alertMapper.insert(alert);
        } catch (DuplicateKeyException e) {
            // Lost a race with a concurrent create of the same rule
            throw duplicateRule(symbol);
        }
        index.watch(symbol);
        logger.info("Created price alert: accountId={}, alertId={}, {} {} {} {}",
            accountId, alert.getAlertId(), symbol, request.direction(), threshold, request.mode());
        return PriceAlertResponse.from(requireAlert(accountId, alert.getAlertId()));
    }

    /** Pauses (active=false) or resumes (active=true) an alert. Resuming takes an active slot again. */
    @Transactional
    public PriceAlertResponse setActive(long accountId, long alertId, boolean active) {
        PriceAlert alert = requireAlert(accountId, alertId);
        if (alert.isActive() == active) {
            return PriceAlertResponse.from(alert);
        }

        if (active) {
            requireActiveSlot(accountId);
            try {
                alertMapper.updateActive(alertId, accountId, true, null);
            } catch (DuplicateKeyException e) {
                throw duplicateRule(alert.getSymbol());
            }
            index.watch(alert.getSymbol());
        } else {
            alertMapper.updateActive(alertId, accountId, false, PriceAlert.PAUSED);
        }
        logger.info("{} price alert: accountId={}, alertId={}", active ? "Resumed" : "Paused", accountId, alertId);
        return PriceAlertResponse.from(requireAlert(accountId, alertId));
    }

    @Transactional
    public void deleteAlert(long accountId, long alertId) {
        if (alertMapper.delete(alertId, accountId) == 0) {
            throw new PriceAlertNotFoundException(alertId);
        }
        logger.info("Deleted price alert: accountId={}, alertId={}", accountId, alertId);
    }

    /** Every firing of one alert, newest first */
    @Transactional(readOnly = true)
    public List<PriceAlertTriggerResponse> getTriggers(long accountId, long alertId) {
        requireAlert(accountId, alertId);
        return triggerMapper.findByAlertId(alertId, accountId).stream()
            .map(PriceAlertTriggerResponse::from)
            .toList();
    }

    /** Recent firings across the account's alerts, newest first; the UI's notification bell polls this */
    @Transactional(readOnly = true)
    public List<PriceAlertTriggerResponse> getRecentTriggers(long accountId, Instant since) {
        return triggerMapper.findRecentByAccountId(accountId, since, RECENT_TRIGGER_LIMIT).stream()
            .map(PriceAlertTriggerResponse::from)
            .toList();
    }

    private void requireActiveSlot(long accountId) {
        if (alertMapper.countActiveByAccountId(accountId) >= maxActivePerAccount) {
            throw new PriceAlertConflictException(
                "Active price alert limit reached (" + maxActivePerAccount + " per account); pause or delete one first");
        }
    }

    private PriceAlert requireAlert(long accountId, long alertId) {
        PriceAlert alert = alertMapper.findById(alertId, accountId);
        if (alert == null) {
            throw new PriceAlertNotFoundException(alertId);
        }
        return alert;
    }

    private static PriceAlertConflictException duplicateRule(String symbol) {
        return new PriceAlertConflictException("An identical active alert on " + symbol + " already exists");
    }
}
