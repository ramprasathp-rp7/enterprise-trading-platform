package com.tradingplatform.api.mappers;

import java.math.BigDecimal;
import java.util.UUID;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tradingplatform.domain.dto.OrderResponse;
import com.tradingplatform.domain.enums.OrderSide;
import com.tradingplatform.domain.enums.OrderStatus;

public interface OrderMapper {
    @Insert("INSERT INTO orders (id, account_id, instrument_id, side, quantity, price, status, idempotency_key) "+
            "VALUES (#{id}, #{accountId}, #{instrumentId}, #{side}, #{quantity}, #{price}, #{status}, #{idempotencyKey})")
    int insertOrder(UUID id,
        long accountId,
        long instrumentId,
        OrderSide side,
        int quantity,
        BigDecimal price,
        OrderStatus status,
        String idempotencyKey
    );

    @Select("SELECT CONCAT('ORD-', o.id::text) AS order_id, "+
            "o.status, "+
            "CASE WHEN o.status = 'NEW' THEN 'Order accepted' "+
            "WHEN o.status = 'FILLED' THEN 'Order executed' "+
            "WHEN o.status = 'REJECTED' THEN 'Order rejected' END AS message, "+
            "i.symbol, "+
            "o.side, "+
            "o.quantity, "+
            "o.price "+
            "FROM orders o "+
            "JOIN instruments i ON i.id = o.instrument_id WHERE o.id = #{id}")
    OrderResponse findOrderResponseById(UUID id);

    @Update("UPDATE orders SET status = 'CANCELLED' WHERE id = #{id} AND status = 'NEW'")
    int cancelOrderIfNew(UUID id);

    @Select("SELECT CONCAT('ORD-', o.id::text) AS order_id, "+
            "o.status, "+
            "'Order cancelled' AS message, "+
            "i.symbol, "+
            "o.side, "+
            "o.quantity, "+
            "o.price "+
            "FROM orders o "+
            "JOIN instruments i ON i.id = o.instrument_id WHERE o.id = #{id}")
    OrderResponse findCancelledOrder(UUID id);
}
