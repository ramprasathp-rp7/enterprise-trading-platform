package com.tradingplatform.api.mappers;

import java.util.List;

import org.apache.ibatis.annotations.Select;

import com.tradingplatform.domain.dto.AccountResponse;
import com.tradingplatform.domain.dto.BalanceResponse;
import com.tradingplatform.domain.dto.OrderHistoryEntry;
import com.tradingplatform.domain.dto.PositionResponse;
import com.tradingplatform.domain.enums.OrderStatus;


interface AccountMapper {
    @Select("SELECT id, account_id, holder_name, cash_balance, status, version, last_updated FROM accounts where id=#{id}")
    AccountResponse findByAccountId(int id);

    @Select("SELECT id, cash_balance, currency, last_updated FROM accounts where id=#{id}")
    BalanceResponse findBalanceByAccountId(int id);

    @Select("SELECT order_id, account_id, symbol, side, quantity, price, executed_price, status, idempotency_key, created_on from ORDERS where id=#{id}")
    List<OrderHistoryEntry> findOrdersByAccountId(int id);

    @Select("SELECT order_id, account_id, symbol, side, quantity, price, executed_price, status, idempotency_key, created_on from ORDERS where id=#{id} and status=#{status}")
    List<OrderHistoryEntry> findOrdersByAccountIdAndStatus(int id, OrderStatus status);

    @Select("SELECT order_id, account_id, symbol, side, quantity, price, executed_price, status, idempotency_key, created_on from ORDERS where id=#{id} and status=#{status} and createdOn between #{from} and #{to}")
    List<OrderHistoryEntry> findOrdersByAccountIdAndStatusAndFromAndTo(int id, OrderStatus status);

    @Select("SELECT order_id, account_id, symbol, side, quantity, price, executed_price, status, idempotency_key, created_on from ORDERS where id=#{id} and createdOn between #{from} and #{to}")
    List<OrderHistoryEntry> findOrdersByAccountIdAndFromAndTo(int id);

    @Select("SELECT id, symbol, quantity, average_cost FROM positions where id=#{id}")
    PositionResponse findPositionByAccountId(int id);
}




