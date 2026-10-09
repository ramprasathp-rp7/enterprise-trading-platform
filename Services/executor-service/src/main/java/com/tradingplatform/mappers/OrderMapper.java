package com.tradingplatform.mappers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tradingplatform.domain.Order;
import com.tradingplatform.domain.PendingOutcome;
import com.tradingplatform.enums.OrderSide;
import com.tradingplatform.enums.OrderStatus;

public interface OrderMapper {
    @Arg(column = "id", javaType = java.util.UUID.class, id = true)
    @Arg(column = "account_id", javaType = Long.class)
    @Arg(column = "instrument_id", javaType = Long.class)
    @Arg(column = "side", javaType = OrderSide.class)
    @Arg(column = "quantity", javaType = int.class)
    @Arg(column = "price", javaType = BigDecimal.class)
    @Arg(column = "idempotency_key", javaType = String.class)
    @Arg(column = "created_on", javaType = java.time.Instant.class)
    @Arg(column = "status", javaType = OrderStatus.class)
    @Arg(column = "executed_price", javaType = BigDecimal.class)
    @Arg(column = "fee", javaType = BigDecimal.class)
    @Select("SELECT id, account_id, instrument_id, side, quantity, price, status, executed_price, fee, idempotency_key, created_on, " +
            "placement_fx_rate, fill_fx_rate, realised_pnl " +
            "FROM orders WHERE id = #{id}")
    Order findOrderById(@Param("id") UUID id);

    /**
     * Find all orders for a given account with a specific status.
     * Used by AvailableBalanceService to calculate reserved funds for pending orders.
     */
    @Arg(column = "id", javaType = java.util.UUID.class, id = true)
    @Arg(column = "account_id", javaType = Long.class)
    @Arg(column = "instrument_id", javaType = Long.class)
    @Arg(column = "side", javaType = OrderSide.class)
    @Arg(column = "quantity", javaType = int.class)
    @Arg(column = "price", javaType = BigDecimal.class)
    @Arg(column = "idempotency_key", javaType = String.class)
    @Arg(column = "created_on", javaType = java.time.Instant.class)
    @Arg(column = "status", javaType = OrderStatus.class)
    @Arg(column = "executed_price", javaType = BigDecimal.class)
    @Arg(column = "fee", javaType = BigDecimal.class)
    @Select("SELECT id, account_id, instrument_id, side, quantity, price, status, executed_price, fee, idempotency_key, created_on, " +
            "placement_fx_rate, fill_fx_rate, realised_pnl " +
            "FROM orders WHERE account_id = #{accountId} AND status = #{status}")
    List<Order> findOrdersByAccountIdAndStatus(@Param("accountId") Long accountId, @Param("status") OrderStatus status);

    /** Current status, without locking; used to drop cancelled orders before they reach the executor. */
    @Select("SELECT status FROM orders WHERE id = #{id}")
    com.tradingplatform.enums.OrderStatus findOrderStatus(@Param("id") UUID id);

    @Select("SELECT status FROM orders WHERE id = #{id} FOR UPDATE")
    com.tradingplatform.enums.OrderStatus findOrderStatusForUpdate(@Param("id") UUID id);

    @Update("UPDATE orders SET status = 'CANCELLED', cancelled_on = CURRENT_TIMESTAMP, event_pending = true " +
            "WHERE id = #{id} AND status = 'NEW'")
    int cancelOrderIfNew(@Param("id") UUID id);

    /**
     * Records the FX rate of record for a fill (instrument-to-account, fetched fresh at fill) and, for a
     * SELL, the realised P&L booked in account currency.
     */
    @Update("UPDATE orders SET fill_fx_rate = #{fillFxRate}, realised_pnl = #{realisedPnl} WHERE id = #{id}")
    int recordFill(@Param("id") UUID id, @Param("fillFxRate") BigDecimal fillFxRate, @Param("realisedPnl") BigDecimal realisedPnl);

    /**
     * Settles a NEW order as FILLED or REJECTED and leaves its trade event pending (event_pending)
     * until Kafka acknowledges it; see {@link #markEventPublished}.
     */
    @Update("UPDATE orders SET status = #{status}, rejected_reason = #{rejectedReason}, executed_price = #{executedPrice}, fee = COALESCE(#{fee}, 0), executed_on = #{executedOn}, event_pending = true WHERE id = #{id} AND status = 'NEW'")
    int updateOrderStatus(@Param("id") UUID id, @Param("status") com.tradingplatform.enums.OrderStatus status, @Param("rejectedReason") String rejectedReason, @Param("executedPrice") BigDecimal executedPrice, @Param("fee") BigDecimal fee, @Param("executedOn") java.time.Instant executedOn);

    /**
     * Records that Kafka acknowledged the event for the given status. Conditional on the status, so a
     * late acknowledgement of an older event never clears a newer one.
     */
    @Update("UPDATE orders SET event_pending = false WHERE id = #{id} AND status = #{status} AND event_pending")
    int markEventPublished(@Param("id") UUID id, @Param("status") OrderStatus status);

    /**
     * FILLED / REJECTED orders whose trade event Kafka has not acknowledged, last updated before
     * olderThan, oldest first. Used by OutcomeReplayScheduler.
     */
    @Arg(column = "id", javaType = java.util.UUID.class, id = true)
    @Arg(column = "account_id", javaType = long.class)
    @Arg(column = "status", javaType = OrderStatus.class)
    @Arg(column = "price", javaType = BigDecimal.class)
    @Arg(column = "executed_price", javaType = BigDecimal.class)
    @Arg(column = "fee", javaType = BigDecimal.class)
    @Arg(column = "rejected_reason", javaType = String.class)
    @Arg(column = "executed_on", javaType = java.time.Instant.class)
    @Arg(column = "fill_fx_rate", javaType = BigDecimal.class)
    @Select("SELECT id, account_id, status, price, executed_price, fee, rejected_reason, executed_on, fill_fx_rate " +
            "FROM orders WHERE status IN ('FILLED', 'REJECTED') AND event_pending AND updated_on < #{olderThan} " +
            "ORDER BY updated_on LIMIT #{limit}")
    List<PendingOutcome> findPendingOutcomes(@Param("olderThan") java.time.Instant olderThan, @Param("limit") int limit);
}
