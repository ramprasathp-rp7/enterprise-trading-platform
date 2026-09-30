package com.tradingplatform.mappers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tradingplatform.domain.Order;
import com.tradingplatform.enums.OrderSide;
import com.tradingplatform.enums.OrderStatus;

public interface OrderMapper {
    @Insert("INSERT INTO orders (id, account_id, instrument_id, side, quantity, price, status, idempotency_key) " +
            "VALUES (#{id}, #{accountId}, #{instrumentId}, #{side}, #{quantity}, #{price}, #{status}, #{idempotencyKey})")
    int insertOrder(@Param("id") UUID id,
            @Param("accountId") long accountId,
            @Param("instrumentId") long instrumentId,
            @Param("side") OrderSide side,
            @Param("quantity") int quantity,
            @Param("price") BigDecimal price,
            @Param("status") OrderStatus status,
            @Param("idempotencyKey") String idempotencyKey);

    @ConstructorArgs({
        @Arg(column = "id", javaType = java.util.UUID.class, id = true),
        @Arg(column = "account_id", javaType = Long.class),
        @Arg(column = "instrument_id", javaType = Long.class),
        @Arg(column = "side", javaType = OrderSide.class),
        @Arg(column = "quantity", javaType = int.class),
        @Arg(column = "price", javaType = BigDecimal.class),
        @Arg(column = "idempotency_key", javaType = String.class),
        @Arg(column = "created_on", javaType = java.time.Instant.class),
        @Arg(column = "status", javaType = OrderStatus.class),
        @Arg(column = "executed_price", javaType = BigDecimal.class),
        @Arg(column = "fee", javaType = BigDecimal.class)
    })
    @Select("SELECT id, account_id, instrument_id, side, quantity, price, status, executed_price, fee, idempotency_key, created_on " +
            "FROM orders WHERE id = #{id}")
    Order findOrderById(@Param("id") UUID id);

    /**
     * Find all orders for a given account with a specific status.
     * Used by AvailableBalanceService to calculate reserved funds for pending orders.
     */
    @ConstructorArgs({
        @Arg(column = "id", javaType = java.util.UUID.class, id = true),
        @Arg(column = "account_id", javaType = Long.class),
        @Arg(column = "instrument_id", javaType = Long.class),
        @Arg(column = "side", javaType = OrderSide.class),
        @Arg(column = "quantity", javaType = int.class),
        @Arg(column = "price", javaType = BigDecimal.class),
        @Arg(column = "idempotency_key", javaType = String.class),
        @Arg(column = "created_on", javaType = java.time.Instant.class),
        @Arg(column = "status", javaType = OrderStatus.class),
        @Arg(column = "executed_price", javaType = BigDecimal.class),
        @Arg(column = "fee", javaType = BigDecimal.class)
    })
    @Select("SELECT id, account_id, instrument_id, side, quantity, price, status, executed_price, fee, idempotency_key, created_on " +
            "FROM orders WHERE account_id = #{accountId} AND status = #{status}")
    List<Order> findOrdersByAccountIdAndStatus(@Param("accountId") Long accountId, @Param("status") OrderStatus status);

    @Select("SELECT status FROM orders WHERE id = #{id} FOR UPDATE")
    com.tradingplatform.enums.OrderStatus findOrderStatusForUpdate(@Param("id") UUID id);

    @Update("UPDATE orders SET status = 'CANCELLED' WHERE id = #{id} AND status = 'NEW'")
    int cancelOrderIfNew(@Param("id") UUID id);

    @Update("UPDATE orders SET status = #{status}, rejected_reason = #{rejectedReason}, executed_price = #{executedPrice}, fee = COALESCE(#{fee}, 0), executed_on = #{executedOn} WHERE id = #{id} AND status = 'NEW'")
    int updateOrderStatus(@Param("id") UUID id, @Param("status") com.tradingplatform.enums.OrderStatus status, @Param("rejectedReason") String rejectedReason, @Param("executedPrice") BigDecimal executedPrice, @Param("fee") BigDecimal fee, @Param("executedOn") java.time.Instant executedOn);
}