package com.tradingplatform.api.mappers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.tradingplatform.domain.dto.OrderStatusChange;
import com.tradingplatform.domain.enums.OrderStatus;

/**
 * MyBatis mapper for querying the order_history table.
 * 
 * The order_history table is automatically populated by trigger trg_order_history_tracking
 * on every UPDATE of status in the orders table.
 * 
 * Supports querying complete audit trail and filtered date-range queries.
 */
public interface OrderHistoryMapper {
    
    /**
     * Retrieve complete status change history for an order, sorted newest-first.
     * 
     * @param orderId the order UUID
     * @return list of status changes (newest timestamp first)
     */
    @ConstructorArgs({
        @Arg(column = "order_id", javaType = String.class),
        @Arg(column = "timestamp", javaType = Instant.class),
        @Arg(column = "from_status", javaType = OrderStatus.class),
        @Arg(column = "to_status", javaType = OrderStatus.class)
    })
    @Select("SELECT order_id, timestamp, from_status, to_status " +
            "FROM order_history " +
            "WHERE order_id = #{orderId} " +
            "ORDER BY timestamp DESC, (from_status IS NULL) DESC, id DESC")
    List<OrderStatusChange> findByOrderId(@Param("orderId") UUID orderId);
    
    /**
     * Retrieve status change history for an order within a date range.
     * Used for compliance/audit reports over specific time periods.
     * 
     * @param orderId the order UUID
     * @param from start timestamp (inclusive)
     * @param to end timestamp (inclusive)
     * @return list of status changes within range (newest timestamp first)
     */
    @ConstructorArgs({
        @Arg(column = "order_id", javaType = String.class),
        @Arg(column = "timestamp", javaType = Instant.class),
        @Arg(column = "from_status", javaType = OrderStatus.class),
        @Arg(column = "to_status", javaType = OrderStatus.class)
    })
    @Select("SELECT order_id, timestamp, from_status, to_status " +
            "FROM order_history " +
            "WHERE order_id = #{orderId} " +
            "AND timestamp BETWEEN #{from} AND #{to} " +
            "ORDER BY timestamp DESC, (from_status IS NULL) DESC, id DESC")
    List<OrderStatusChange> findByOrderIdAndDateRange(
        @Param("orderId") UUID orderId,
        @Param("from") Instant from,
        @Param("to") Instant to
    );
    
    /**
     * Retrieve all status changes for orders in an account within a date range.
     * Used for account-level audit trails and compliance reporting.
     * 
     * @param accountId the account ID
     * @param from start timestamp (inclusive)
     * @param to end timestamp (inclusive)
     * @return list of status changes for all account orders (newest timestamp first)
     */
    @ConstructorArgs({
        @Arg(column = "order_id", javaType = String.class),
        @Arg(column = "timestamp", javaType = Instant.class),
        @Arg(column = "from_status", javaType = OrderStatus.class),
        @Arg(column = "to_status", javaType = OrderStatus.class)
    })
    @Select("SELECT oh.order_id, oh.timestamp, oh.from_status, oh.to_status " +
            "FROM order_history oh " +
            "INNER JOIN orders o ON oh.order_id = o.id " +
            "WHERE o.account_id = #{accountId} " +
            "AND oh.timestamp BETWEEN #{from} AND #{to} " +
            "ORDER BY oh.timestamp DESC, (oh.from_status IS NULL) DESC, oh.id DESC")
    List<OrderStatusChange> findByAccountIdAndDateRange(
        @Param("accountId") Long accountId,
        @Param("from") Instant from,
        @Param("to") Instant to
    );
    
    /**
     * Retrieve all order status changes across all accounts (admin only).
     * Used for system-wide audit trails and compliance reporting.
     * 
     * @return list of all status changes (newest timestamp first)
     */
    @ConstructorArgs({
        @Arg(column = "order_id", javaType = String.class),
        @Arg(column = "timestamp", javaType = Instant.class),
        @Arg(column = "from_status", javaType = OrderStatus.class),
        @Arg(column = "to_status", javaType = OrderStatus.class)
    })
    @Select("SELECT order_id, timestamp, from_status, to_status " +
            "FROM order_history " +
            "ORDER BY timestamp DESC, (from_status IS NULL) DESC, id DESC")
    List<OrderStatusChange> findAllOrderHistory();
    
    /**
     * Retrieve all order status changes within a date range across all accounts (admin only).
     * Used for system-wide audit trails and compliance reporting.
     * 
     * @param from start timestamp (inclusive)
     * @param to end timestamp (inclusive)
     * @return list of status changes within range (newest timestamp first)
     */
    @ConstructorArgs({
        @Arg(column = "order_id", javaType = String.class),
        @Arg(column = "timestamp", javaType = Instant.class),
        @Arg(column = "from_status", javaType = OrderStatus.class),
        @Arg(column = "to_status", javaType = OrderStatus.class)
    })
    @Select("SELECT order_id, timestamp, from_status, to_status " +
            "FROM order_history " +
            "WHERE timestamp BETWEEN #{from} AND #{to} " +
            "ORDER BY timestamp DESC, (from_status IS NULL) DESC, id DESC")
    List<OrderStatusChange> findAllOrderHistoryByDateRange(
        @Param("from") Instant from,
        @Param("to") Instant to
    );
}
