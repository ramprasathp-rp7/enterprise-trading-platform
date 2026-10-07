package com.tradingplatform.api.mappers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tradingplatform.domain.enums.OrderSide;
import com.tradingplatform.domain.enums.OrderStatus;
import com.tradingplatform.domain.model.Order;

public interface OrderMapper {
    @Insert("INSERT INTO orders (id, account_id, instrument_id, side, quantity, price, status, idempotency_key) " +
            "VALUES (#{id}, #{accountId}, #{instrumentId}, #{side}, #{quantity}, #{price}, #{status}, #{idempotencyKey})")
    int insertOrder(UUID id,
            long accountId,
            long instrumentId,
            OrderSide side,
            int quantity,
            BigDecimal price,
            OrderStatus status,
            String idempotencyKey);

    @ConstructorArgs({
    @Arg(column = "id", javaType = java.util.UUID.class),          // ← ADD THIS
    @Arg(column = "account_id", javaType = Long.class),
    @Arg(column = "instrument_id", javaType = Long.class),
    @Arg(column = "side", javaType = OrderSide.class),
    @Arg(column = "quantity", javaType = int.class),
    @Arg(column = "price", javaType = BigDecimal.class),
    @Arg(column = "idempotency_key", javaType = String.class)
    })
    @Select("SELECT id, account_id, instrument_id, side, quantity, price, status, executed_price, fee, idempotency_key, created_on " +
            "FROM orders WHERE id = #{id}")
    Order findOrderById(@Param("id") UUID id);

    /**
     * Find all orders for a given account with a specific status.
     * Used by AvailableBalanceService to calculate reserved funds for pending orders.
     */
    @ConstructorArgs({
        @Arg(column = "account_id", javaType = Long.class),
        @Arg(column = "instrument_id", javaType = Long.class),
        @Arg(column = "side", javaType = OrderSide.class),
        @Arg(column = "quantity", javaType = int.class),
        @Arg(column = "price", javaType = BigDecimal.class),
        @Arg(column = "idempotency_key", javaType = String.class)
    })
    @Select("SELECT id, account_id, instrument_id, side, quantity, price, status, executed_price, fee, idempotency_key, created_on " +
            "FROM orders WHERE account_id = #{accountId} AND status = #{status}")
    List<Order> findOrdersByAccountIdAndStatus(@Param("accountId") Long accountId, @Param("status") OrderStatus status);

    @Update("UPDATE orders SET status = 'CANCELLED' WHERE id = #{id} AND status = 'NEW'")
    int cancelOrderIfNew(@Param("id") UUID id);

    /**
     * Settles an order: sets its terminal status, executed price and executed_on. Used for FILLED by
     * OrderSettlementService (SYNC mode). ck_orders_executed_on_iff_terminal requires executed_on on
     * FILLED and REJECTED rows; a rejection also needs a reason, so rejections use {@link #rejectOrder}.
     */
    @Update("UPDATE orders SET status = #{status}, executed_price = #{executedPrice}, executed_on = CURRENT_TIMESTAMP WHERE id = #{id}")
    int updateOrderStatus(@Param("id") UUID id, @Param("status") OrderStatus status, @Param("executedPrice") BigDecimal executedPrice);

    /**
     * Marks an order REJECTED with a reason, as ck_orders_rejected_reason_iff_rejected and
     * ck_orders_executed_on_iff_terminal require. Used when SYNC-mode settlement fails.
     */
    @Update("UPDATE orders SET status = 'REJECTED', rejected_reason = #{reason}, executed_price = NULL, executed_on = CURRENT_TIMESTAMP WHERE id = #{id}")
    int rejectOrder(@Param("id") UUID id, @Param("reason") String reason);

    /**
     * Update the fee amount on an order.
     * Used after settlement to persist calculated fees for audit trail.
     */
    @Update("UPDATE orders SET fee = #{fee} WHERE id = #{id}")
    int updateOrderFee(@Param("id") UUID id, @Param("fee") BigDecimal fee);
}
