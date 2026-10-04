package com.tradingplatform.api.services;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.tradingplatform.api.mappers.OrderHistoryMapper;
import com.tradingplatform.api.mappers.OrderMapper;
import com.tradingplatform.domain.dto.OrderStatusChange;
import com.tradingplatform.domain.model.Order;
import com.tradingplatform.domain.exception.OrderNotFoundException;

/**
 * Service for querying order status change history.
 * 
 * The order_history table is automatically maintained by a PostgreSQL trigger
 * that fires on every order status UPDATE. This service provides convenient
 * methods to retrieve the complete audit trail.
 */
@Service
public class OrderHistoryService {
    
    private final OrderHistoryMapper orderHistoryMapper;
    private final OrderMapper orderMapper;
    
    public OrderHistoryService(OrderHistoryMapper orderHistoryMapper, OrderMapper orderMapper) {
        this.orderHistoryMapper = orderHistoryMapper;
        this.orderMapper = orderMapper;
    }

    public long getOrderAccountId(UUID orderId) {
        return getOrderOrThrow(orderId).getAccountId();
    }
    
    /**
     * Get complete status change history for an order.
     * 
     * @param orderId the order UUID
     * @return list of status transitions (newest first)
     * @throws OrderNotFoundException if order does not exist
     */
    public List<OrderStatusChange> getOrderHistory(UUID orderId) {
        // Verify order exists
        getOrderOrThrow(orderId);

        return orderHistoryMapper.findByOrderId(orderId)
        .stream()
        .map(this::addPrefixToOrderStatusChange)  // ← Add this
        .toList();
    }
    
    /**
     * Get status change history for an order within a specific date range.
     * Used for time-bounded audit queries and compliance reporting.
     * 
     * @param orderId the order UUID
     * @param from start timestamp (inclusive)
     * @param to end timestamp (inclusive)
     * @return list of status transitions within range (newest first)
     * @throws OrderNotFoundException if order does not exist
     */
    public List<OrderStatusChange> getOrderHistory(UUID orderId, Instant from, Instant to) {
        // Verify order exists
        getOrderOrThrow(orderId);
        
        // Validate date range
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("fromDate must be before toDate");
        }
        
        return orderHistoryMapper.findByOrderIdAndDateRange(orderId, from, to)
        .stream()
        .map(this::addPrefixToOrderStatusChange)  // ← Add this
        .toList();
    }
    
    /**
     * Get status change history for all orders in an account within a date range.
     * Useful for account-level audit trails and compliance reporting.
     * 
     * @param accountId the account ID
     * @param from start timestamp (inclusive)
     * @param to end timestamp (inclusive)
     * @return list of status transitions for all account orders (newest first)
     */
    public List<OrderStatusChange> getAccountOrderHistory(Long accountId, Instant from, Instant to) {
        // Validate date range
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("fromDate must be before toDate");
        }
        
        return orderHistoryMapper.findByAccountIdAndDateRange(accountId, from, to)
        .stream()
        .map(this::addPrefixToOrderStatusChange)  // ← Add this
        .toList();
    }

    private Order getOrderOrThrow(UUID orderId) {
        Order order = orderMapper.findOrderById(orderId);
        if (order == null) {
            throw new OrderNotFoundException("Order not found: " + orderId);
        }
        return order;
    }

    /**
     * Get complete order history across all accounts (admin only).
     * Used for system-wide audit trails and compliance reporting.
     * 
     * @return list of all status transitions (newest first)
     */
    public List<OrderStatusChange> getAllOrderHistory() {
        return orderHistoryMapper.findAllOrderHistory()
            .stream()
            .map(this::addPrefixToOrderStatusChange)
            .toList();
    }
    
    /**
     * Get complete order history across all accounts within a date range (admin only).
     * Used for system-wide audit trails and compliance reporting.
     * 
     * @param from start timestamp (inclusive)
     * @param to end timestamp (inclusive)
     * @return list of status transitions within range (newest first)
     */
    public List<OrderStatusChange> getAllOrderHistory(Instant from, Instant to) {
        // Validate date range
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("fromDate must be before toDate");
        }
        
        return orderHistoryMapper.findAllOrderHistoryByDateRange(from, to)
            .stream()
            .map(this::addPrefixToOrderStatusChange)
            .toList();
    }

    private OrderStatusChange addPrefixToOrderStatusChange(OrderStatusChange change) {
    return new OrderStatusChange(
        "ORD-" + change.orderId(),
        change.timestamp(),
        change.fromStatus(),
        change.toStatus()
    );
}
}
