package com.tradingplatform.mappers;

import java.math.BigDecimal;
import java.time.Instant;
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
    int insertOrder(UUID id,
            long accountId,
            long instrumentId,
            OrderSide side,
            int quantity,
            BigDecimal price,
            OrderStatus status,
            String idempotencyKey);

    @ConstructorArgs({
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

    @Update("UPDATE orders SET status = 'CANCELLED' WHERE id = #{id} AND status = 'NEW'")
    int cancelOrderIfNew(@Param("id") UUID id);

    @Update("UPDATE orders SET status = #{status}, rejected_reason = #{rejectedReason}, executed_price = #{executedPrice}, fee = #{fee} WHERE id = #{id}")
    int updateOrderStatus(@Param("id") UUID id, @Param("status") com.tradingplatform.enums.OrderStatus status, @Param("rejectedReason") String rejectedReason, @Param("executedPrice") BigDecimal executedPrice, @Param("fee") BigDecimal fee);

    /**
     * Guarded state transition: updates order status only if current status matches expected.
     * This is the mechanism that prevents duplicate deliveries from double-processing.
     *
     * Returns: 0 if current status != expectedStatus (another delivery already processed)
     *          1 if update succeeded
     *
     * Example: UPDATE orders SET status = 'FILLED', executed_price = ?, executed_on = ?, rejected_reason = ?
     *          WHERE id = ? AND status = 'NEW'
     */
    @Update("UPDATE orders SET status = #{newStatus}, executed_price = #{executedPrice}, executed_on = #{executedOn}, rejected_reason = #{rejectReason} " +
            "WHERE id = #{id} AND status = #{expectedStatus}")
    int updateOrderStatusGuarded(
        @Param("id") UUID id,
        @Param("expectedStatus") OrderStatus expectedStatus,
        @Param("newStatus") OrderStatus newStatus,
        @Param("executedPrice") BigDecimal executedPrice,
        @Param("executedOn") Instant executedOn,
        @Param("rejectReason") String rejectReason
    );
}
