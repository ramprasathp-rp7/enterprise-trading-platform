package com.tradingplatform.api.mappers;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tradingplatform.domain.dto.AccountResponse;
import com.tradingplatform.domain.dto.BalanceResponse;
import com.tradingplatform.domain.dto.OrderHistoryEntry;
import com.tradingplatform.domain.dto.PositionResponse;
import com.tradingplatform.domain.enums.OrderStatus;
import com.tradingplatform.domain.model.Account;

public interface AccountMapper {
    @Select("SELECT id, account_id, holder_name, cash_balance, currency, status, version FROM accounts WHERE id=#{id}")
    Account findByAccountId(Long id);

    @Select("SELECT id AS account_id, cash_balance, currency, last_updated as as_of FROM accounts WHERE id=#{id}")
    BalanceResponse findBalanceByAccountId(Long id);

    @Select("SELECT order_id, account_id, symbol, side, quantity, price, executed_price, status, idempotency_key, created_on FROM ORDERS WHERE account_id=#{id} ORDER BY created_on DESC")
    List<OrderHistoryEntry> findOrdersByAccountId(Long id);

    @Select("SELECT order_id, account_id, symbol, side, quantity, price, executed_price, status, idempotency_key, created_on FROM ORDERS WHERE account_id=#{id} AND status=#{status} ORDER BY created_on DESC")
    List<OrderHistoryEntry> findOrdersByAccountIdAndStatus(Long id, OrderStatus status);

    @Select("SELECT order_id, account_id, symbol, side, quantity, price, executed_price, status, idempotency_key, created_on FROM ORDERS WHERE account_id=#{id} AND status=#{status} AND created_on BETWEEN #{from} AND #{to} ORDER BY created_on DESC")
    List<OrderHistoryEntry> findOrdersByAccountIdAndStatusAndFromAndTo(Long id, OrderStatus status, Instant from, Instant to);

    @Select("SELECT order_id, account_id, symbol, side, quantity, price, executed_price, status, idempotency_key, created_on FROM ORDERS WHERE account_id=#{id} AND created_on BETWEEN #{from} AND #{to} ORDER BY created_on DESC")
    List<OrderHistoryEntry> findOrdersByAccountIdAndFromAndTo(Long id, Instant from, Instant to);

    @Select("SELECT account_id, symbol, quantity, average_cost FROM positions WHERE account_id=#{id}")
    List<PositionResponse> findPositionByAccountId(Long id);

    @Update("UPDATE accounts SET cash_balance = #{cashBalance}, version = #{version}, last_updated = CURRENT_TIMESTAMP WHERE id = #{id}")
    int updateAccount(Account account);
}