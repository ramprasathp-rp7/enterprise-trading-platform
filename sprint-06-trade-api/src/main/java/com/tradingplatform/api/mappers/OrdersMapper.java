package com.tradingplatform.api.mappers;

import java.util.List;

import org.apache.ibatis.annotations.Select;

import com.tradingplatform.domain.dto.OrderHistoryEntry;
import com.tradingplatform.domain.enums.OrderStatus;

public interface OrdersMapper {
    @Select("SELECT order_id, account_id, symbol, side, quantity, price, executed_price, status, idempotency_key, created_on from ORDERS where id=#{id}")
    List<OrderHistoryEntry> findOrdersByAccountId(int id)

    @Select("SELECT order_id, account_id, symbol, side, quantity, price, executed_price, status, idempotency_key, created_on from ORDERS where id=#{id} and status=#{status}")
    List<OrderHistoryEntry> findOrdersByAccountIdAndStatus(int id, OrderStatus status);

    @Select("SELECT order_id, account_id, symbol, side, quantity, price, executed_price, status, idempotency_key, created_on from ORDERS where id=#{id} and status=#{status} and createdOn between #{from} and #{to}")
    List<OrderHistoryEntry> findOrdersByAccountIdAndStatusAndFromAndTo(int id, OrderStatus status);

    @Select("SELECT order_id, account_id, symbol, side, quantity, price, executed_price, status, idempotency_key, created_on from ORDERS where id=#{id} and createdOn between #{from} and #{to}")
    List<OrderHistoryEntry> findOrdersByAccountIdAndFromAndTo(int id)

} {
    
}
