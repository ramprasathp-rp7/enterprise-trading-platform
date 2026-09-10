package com.tradingplatform.api.mappers;

import java.math.BigDecimal;
import java.util.UUID;

import org.apache.ibatis.annotations.Insert;
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

    @Select("SELECT id, account_id, instrument_id, side, quantity, price, status, executed_price, idempotency_key, created_on " +
            "FROM orders WHERE id = #{id}")
    Order findOrderById(UUID id);

    @Update("UPDATE orders SET status = 'CANCELLED' WHERE id = #{id} AND status = 'NEW'")
    int cancelOrderIfNew(UUID id);

    @Select("SELECT EXISTS(SELECT 1 FROM orders WHERE idempotency_key = #{idempotencyKey})")
    boolean existsByIdempotencyKey(String idempotencyKey);

    @Update("UPDATE orders SET status = #{status}, executed_price = #{executedPrice} WHERE id = #{id}")
    int updateOrderStatus(UUID id, OrderStatus status, BigDecimal executedPrice);
}
