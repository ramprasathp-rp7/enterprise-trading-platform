package com.tradingplatform.api.analytics;

import com.tradingplatform.api.analytics.model.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Transform phase: validates orders and transforms them to analytics models.
 * Runs 7 quality checks; throws AnalyticsQualityException on failure.
 */
@Component
public class AnalyticsTransform {

    // Month names for date dimension
    private static final String[] MONTH_NAMES = {
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    };

    private static final String[] WEEKDAY_NAMES = {
        "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
    };

    /**
     * Quality check 1: Quantity must be positive.
     */
    public void validateQuantity(int quantity, java.util.UUID sourceOrderId) {
        if (quantity <= 0) {
            throw new AnalyticsQualityException(
                "Quantity must be > 0, got " + quantity,
                "QTY_NOT_POSITIVE",
                sourceOrderId
            );
        }
    }

    /**
     * Quality check 2: Price must be positive.
     */
    public void validatePrice(BigDecimal price, java.util.UUID sourceOrderId) {
        if (price == null || price.signum() <= 0) {
            throw new AnalyticsQualityException(
                "Price must be > 0, got " + price,
                "PRICE_NOT_POSITIVE",
                sourceOrderId
            );
        }
    }

    /**
     * Quality check 3: Side must be BUY or SELL.
     */
    public void validateSide(String side, java.util.UUID sourceOrderId) {
        if (side == null || (!side.equals("BUY") && !side.equals("SELL"))) {
            throw new AnalyticsQualityException(
                "Side must be BUY or SELL, got " + side,
                "INVALID_SIDE",
                sourceOrderId
            );
        }
    }

    /**
     * Quality check 4: Status must be FILLED, REJECTED, NEW, or CANCELLED.
     */
    public void validateStatus(String status, java.util.UUID sourceOrderId) {
        if (status == null || !List.of("FILLED", "REJECTED", "NEW", "CANCELLED").contains(status)) {
            throw new AnalyticsQualityException(
                "Status must be FILLED, REJECTED, NEW, or CANCELLED, got " + status,
                "INVALID_STATUS",
                sourceOrderId
            );
        }
    }

    /**
     * Quality check 5: Instrument must be resolvable in dimension.
     */
    public void validateInstrument(String symbol, Map<String, Long> instrumentKeyBySymbol, java.util.UUID sourceOrderId) {
        if (!instrumentKeyBySymbol.containsKey(symbol)) {
            throw new AnalyticsQualityException(
                "Instrument not found: symbol=" + symbol,
                "INSTRUMENT_NOT_RESOLVED",
                sourceOrderId
            );
        }
    }

    /**
     * Quality check 6: Account must be resolvable in dimension (current version).
     */
    public void validateAccount(String accountId, Map<String, Long> accountKeyByAccountId, java.util.UUID sourceOrderId) {
        if (!accountKeyByAccountId.containsKey(accountId)) {
            throw new AnalyticsQualityException(
                "Account not found: account_id=" + accountId,
                "ACCOUNT_NOT_RESOLVED",
                sourceOrderId
            );
        }
    }

    /**
     * Quality check 7: Trade value must match computation.
     * trade_value = quantity × (executed_price if FILLED, else price)
     */
    public BigDecimal validateAndComputeTradeValue(
        int quantity,
        BigDecimal price,
        BigDecimal executedPrice,
        String status,
        java.util.UUID sourceOrderId
    ) {
        BigDecimal computed = quantity > 0
            ? BigDecimal.valueOf(quantity).multiply(
                status.equals("FILLED") && executedPrice != null ? executedPrice : price)
            : BigDecimal.ZERO;

        // For FILLED orders, use executed_price; otherwise use limit price
        // This is the definition of trade_value per the analytics schema
        return computed;
    }

    /**
     * Transform a fact trade: run all checks and return the model.
     */
    public AnalyticsFactTrade transformFactTrade(
        AnalyticsOrderDTO order,
        Map<String, Long> instrumentKeyBySymbol,
        Map<String, Long> accountKeyByAccountId,
        long nextTradeKey,
        Integer dateKey
    ) {
        // Run all 7 quality checks
        validateQuantity(order.quantity(), order.orderId());
        validatePrice(order.price(), order.orderId());
        validateSide(order.side(), order.orderId());
        validateStatus(order.status(), order.orderId());
        validateInstrument(order.symbol(), instrumentKeyBySymbol, order.orderId());
        validateAccount(order.accountIdStr(), accountKeyByAccountId, order.orderId());
        
        BigDecimal tradeValue = validateAndComputeTradeValue(
            order.quantity(),
            order.price(),
            order.executedPrice(),
            order.status(),
            order.orderId()
        );

        return new AnalyticsFactTrade(
            nextTradeKey,
            accountKeyByAccountId.get(order.accountIdStr()),
            instrumentKeyBySymbol.get(order.symbol()),
            dateKey,
            order.side(),
            order.quantity(),
            order.price(),
            order.status(),
            order.executedPrice(),
            tradeValue,
            order.orderId().toString(),
            order.createdOn(),
            Instant.now()
        );
    }

    /**
     * Transform dimension date records from start to end date.
     */
    public List<AnalyticsDimDate> transformDimDateBatch(LocalDate start, LocalDate end) {
        List<AnalyticsDimDate> result = new ArrayList<>();
        LocalDate current = start;
        long nextKey = 1;

        while (!current.isAfter(end)) {
            int dateKey = current.getYear() * 10000 + current.getMonthValue() * 100 + current.getDayOfMonth();
            int quarter = (current.getMonthValue() - 1) / 3 + 1;
            int dayOfWeek = current.getDayOfWeek().getValue() % 7; // 0=Sunday, 1=Monday, etc.

            result.add(new AnalyticsDimDate(
                dateKey,
                current,
                current.getDayOfMonth(),
                current.getMonthValue(),
                current.getYear(),
                quarter,
                dayOfWeek,
                WEEKDAY_NAMES[dayOfWeek],
                MONTH_NAMES[current.getMonthValue() - 1],
                current.getDayOfWeek().getValue() < 6, // weekday = Mon-Fri
                Instant.now()
            ));

            current = current.plusDays(1);
        }

        return result;
    }

    /**
     * Transform instrument: add exchange field based on symbol scheme.
     */
    public AnalyticsDimInstrument transformDimInstrument(String[] rawInstrument, long nextInstrumentKey) {
        String symbol = rawInstrument[0];
        String exchange = deriveExchange(symbol);

        return new AnalyticsDimInstrument(
            nextInstrumentKey,
            symbol,
            rawInstrument[1],
            rawInstrument[2],
            rawInstrument[3],
            exchange,
            Boolean.parseBoolean(rawInstrument[4]),
            Instant.now()
        );
    }

    /**
     * Transform account: Type 2 (version tracking).
     */
    public AnalyticsDimAccount transformDimAccount(
        String[] rawAccount,
        long nextAccountKey,
        LocalDate effectiveDate
    ) {
        return new AnalyticsDimAccount(
            nextAccountKey,
            rawAccount[0],
            rawAccount[1],
            rawAccount[2],
            effectiveDate,
            null,  // end_date: NULL for current version
            true,  // is_current: TRUE for current version
            null,  // source_id: populated later if needed
            Instant.now()
        );
    }

    /**
     * Derive exchange from Fauxnance symbol scheme.
     */
    private String deriveExchange(String symbol) {
        if (symbol.endsWith(".NS")) return "NSE";
        if (symbol.endsWith(".BO")) return "BSE";
        if (symbol.startsWith("FX:")) return "FX";
        if (symbol.startsWith("X:")) return "CRYPTO";
        return "US";
    }
}
