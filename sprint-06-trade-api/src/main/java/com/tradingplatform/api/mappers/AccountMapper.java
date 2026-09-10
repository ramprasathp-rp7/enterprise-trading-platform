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
import com.tradingplatform.domain.model.Order;
import com.tradingplatform.domain.model.Position;

public interface AccountMapper {
    @Select("SELECT id, account_id, holder_name, cash_balance, currency, status, version, last_updated FROM accounts WHERE id=#{id}")
    Account findByAccountId(Long id);

    
`
    @Select("SELECT id, account_id, instrument_id, symbol, side, quantity, price, idempotency_key, created_on, status, executed_price FROM ORDERS WHERE account_id=#{id} ORDER BY created_on DESC")
    List<Order> findOrdersByAccountId(Long id);

    @Select("SELECT id, account_id, instrument_id, symbol, side, quantity, price, idempotency_key, created_on, status, executed_price FROM ORDERS WHERE account_id=#{id} AND status=#{status} ORDER BY created_on DESC")
    List<Order> findOrdersByAccountIdAndStatus(Long id, OrderStatus status);

    @Select("SELECT id, account_id, instrument_id, symbol, side, quantity, price, idempotency_key, created_on, status, executed_price FROM ORDERS WHERE account_id=#{id} AND status=#{status} AND created_on BETWEEN #{from} AND #{to} ORDER BY created_on DESC")
    List<Order> findOrdersByAccountIdAndStatusAndFromAndTo(Long id, OrderStatus status, Instant from, Instant to);

    @Select("SELECT id, account_id, instrument_id, symbol, side, quantity, price, idempotency_key, created_on, status, executed_price FROM ORDERS WHERE account_id=#{id} AND created_on BETWEEN #{from} AND #{to} ORDER BY created_on DESC")
    List<Order> findOrdersByAccountIdAndFromAndTo(Long id, Instant from, Instant to);

    @Select("SELECT account_id, symbol, quantity, average_cost FROM positions WHERE account_id=#{id}")
    List<Position> findPositionByAccountId(Long id);

    @Update("UPDATE accounts SET cash_balance = #{cashBalance}, version = #{newVersion}, last_updated = CURRENT_TIMESTAMP WHERE id = #{id} AND version = #{version}")
    int updateAccount(Account account, int newVersion);
}