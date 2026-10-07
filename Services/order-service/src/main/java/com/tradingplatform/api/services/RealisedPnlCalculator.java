package com.tradingplatform.api.services;

import com.tradingplatform.api.mappers.OrderMapper;
import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.domain.enums.OrderSide;
import com.tradingplatform.domain.enums.OrderStatus;
import com.tradingplatform.domain.model.Order;
import com.tradingplatform.domain.model.Instrument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Calculates realised profit and loss for an account, in the account's currency.
 * Realised P&L is booked at the moment of a SELL order execution and never recomputed.
 * Formula: quantitySold * (executedPrice * fillFxRate - averageCostAccountAtSale), stored on the order.
 */
@Service
public class RealisedPnlCalculator {
    
    private static final Logger log = LoggerFactory.getLogger(RealisedPnlCalculator.class);

    private final OrderMapper orderMapper;
    private final InstrumentMapper instrumentMapper;

    public RealisedPnlCalculator(OrderMapper orderMapper, InstrumentMapper instrumentMapper) {
        this.orderMapper = Objects.requireNonNull(orderMapper, "orderMapper cannot be null");
        this.instrumentMapper = Objects.requireNonNull(instrumentMapper, "instrumentMapper cannot be null");
    }

    /**
     * Calculate total realised P&L for an account.
     * Aggregates P&L from all SELL orders with EXECUTED status.
     * 
     * @param accountId Account ID
     * @return Total realised P&L (positive = profit, negative = loss)
     */
    public BigDecimal calculateTotalRealisedPnl(Long accountId) {
        return calculateRealisedPnlBySymbol(accountId, null, null, null)
                .values()
                .stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculate realised P&L for an account within a date range.
     * 
     * @param accountId Account ID
     * @param from Lower bound (inclusive), null for unbounded
     * @param to Upper bound (inclusive), null for unbounded
     * @return Total realised P&L within the range
     */
    public BigDecimal calculateRealisedPnlInRange(Long accountId, LocalDate from, LocalDate to) {
        return calculateRealisedPnlBySymbol(accountId, from, to, null)
                .values()
                .stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculate realised P&L by symbol for an account.
     * Used when bySymbol=true is requested in the API.
     * 
     * @param accountId Account ID
     * @param from Lower bound (inclusive), null for unbounded
     * @param to Upper bound (inclusive), null for unbounded
     * @param symbol Restrict to one symbol, null for all symbols
     * @return Map of symbol to realised P&L
     */
    public Map<String, BigDecimal> calculateRealisedPnlBySymbol(Long accountId, LocalDate from, LocalDate to, String symbol) {
        Map<String, BigDecimal> result = new HashMap<>();
        
        // Get all executed orders for the account
        List<Order> executedOrders = orderMapper.findOrdersByAccountIdAndStatus(accountId, OrderStatus.FILLED);
        if (executedOrders == null || executedOrders.isEmpty()) {
            return result;
        }

        for (Order order : executedOrders) {
            // Only process SELL orders
            if (!OrderSide.SELL.equals(order.getSide())) {
                continue;
            }

            // Filter by date range if provided
            if (from != null || to != null) {
                LocalDate orderDate = order.getCreatedOn()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();
                
                if (from != null && orderDate.isBefore(from)) {
                    continue;
                }
                if (to != null && orderDate.isAfter(to)) {
                    continue;
                }
            }

            // Get the instrument to retrieve the symbol
            Instrument instrument = instrumentMapper.findById(order.getInstrumentId());
            if (instrument == null) {
                log.warn("Instrument {} not found for order {}", order.getInstrumentId(), order.getId());
                continue;
            }

            String sym = instrument.getSymbol();
            
            // Filter by symbol if provided
            if (symbol != null && !symbol.equals(sym)) {
                continue;
            }

            // Calculate P&L for this sell order
            BigDecimal pnl = calculateOrderRealisedPnl(order);
            result.put(sym, result.getOrDefault(sym, BigDecimal.ZERO).add(pnl));
        }

        return result;
    }

    /**
     * Realised P&L for a single filled SELL order, in account currency.
     *
     * It is booked by settlement at the moment the SELL fills, as
     * {@code quantity * (executedPrice * fillFxRate - averageCostAccountAtSale)}, and stored on the
     * order, so it is read here, never recomputed: it does not depend on today's price or today's FX rate.
     *
     * @param sellOrder A FILLED SELL order
     * @return Realised P&L (positive = profit, negative = loss); zero if none was booked
     */
    private BigDecimal calculateOrderRealisedPnl(Order sellOrder) {
        BigDecimal pnl = sellOrder.getRealisedPnl();
        if (pnl == null) {
            log.warn("Order {} has no booked realised P&L; counting it as zero", sellOrder.getId());
            return BigDecimal.ZERO;
        }
        log.debug("Realised P&L for order {}: {}", sellOrder.getId(), pnl);
        return pnl;
    }
}
