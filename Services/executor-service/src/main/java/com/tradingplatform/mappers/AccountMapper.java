package com.tradingplatform.mappers;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tradingplatform.domain.Account;
import com.tradingplatform.domain.Order;
import com.tradingplatform.domain.Position;
import com.tradingplatform.enums.AccountStatus;
import com.tradingplatform.enums.OrderSide;
import com.tradingplatform.enums.OrderStatus;

public interface AccountMapper {
    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class),
        @Arg(column = "account_id", javaType = String.class),
        @Arg(column = "holder_name", javaType = String.class),
        @Arg(column = "cash_balance", javaType = java.math.BigDecimal.class),
        @Arg(column = "currency", javaType = String.class),
        @Arg(column = "status", javaType = AccountStatus.class),
        @Arg(column = "version", javaType = int.class),
        @Arg(column = "created_on", javaType = java.time.Instant.class),
        @Arg(column = "last_updated", javaType = java.time.Instant.class)
    })
    @Select("SELECT id, account_id, holder_name, cash_balance, currency, status, version, created_on, last_updated FROM accounts WHERE id=#{id}")
    Account findByAccountId(@Param("id") Long id);

    @ConstructorArgs({
        @Arg(column = "id", javaType = java.util.UUID.class, id = true),
        @Arg(column = "account_id", javaType = Long.class),
        @Arg(column = "instrument_id", javaType = Long.class),
        @Arg(column = "side", javaType = OrderSide.class),
        @Arg(column = "quantity", javaType = int.class),
        @Arg(column = "price", javaType = java.math.BigDecimal.class),
        @Arg(column = "idempotency_key", javaType = String.class),
        @Arg(column = "created_on", javaType = java.time.Instant.class),
        @Arg(column = "status", javaType = OrderStatus.class),
        @Arg(column = "executed_price", javaType = java.math.BigDecimal.class),
        @Arg(column = "fee", javaType = java.math.BigDecimal.class)
    })
    @Select("SELECT id, account_id, instrument_id, side, quantity, price, idempotency_key, created_on, status, executed_price, fee FROM orders WHERE account_id=#{id} ORDER BY created_on DESC")
    List<Order> findOrdersByAccountId(@Param("id") Long id);

    @ConstructorArgs({
        @Arg(column = "id", javaType = java.util.UUID.class, id = true),
        @Arg(column = "account_id", javaType = Long.class),
        @Arg(column = "instrument_id", javaType = Long.class),
        @Arg(column = "side", javaType = OrderSide.class),
        @Arg(column = "quantity", javaType = int.class),
        @Arg(column = "price", javaType = java.math.BigDecimal.class),
        @Arg(column = "idempotency_key", javaType = String.class),
        @Arg(column = "created_on", javaType = java.time.Instant.class),
        @Arg(column = "status", javaType = OrderStatus.class),
        @Arg(column = "executed_price", javaType = java.math.BigDecimal.class),
        @Arg(column = "fee", javaType = java.math.BigDecimal.class)
    })
    @Select("SELECT id, account_id, instrument_id, side, quantity, price, idempotency_key, created_on, status, executed_price, fee FROM orders WHERE account_id=#{id} AND status=#{status} ORDER BY created_on DESC")
    List<Order> findOrdersByAccountIdAndStatus(@Param("id") Long id, @Param("status") OrderStatus status);

    @ConstructorArgs({
        @Arg(column = "id", javaType = java.util.UUID.class, id = true),
        @Arg(column = "account_id", javaType = Long.class),
        @Arg(column = "instrument_id", javaType = Long.class),
        @Arg(column = "side", javaType = OrderSide.class),
        @Arg(column = "quantity", javaType = int.class),
        @Arg(column = "price", javaType = java.math.BigDecimal.class),
        @Arg(column = "idempotency_key", javaType = String.class),
        @Arg(column = "created_on", javaType = java.time.Instant.class),
        @Arg(column = "status", javaType = OrderStatus.class),
        @Arg(column = "executed_price", javaType = java.math.BigDecimal.class),
        @Arg(column = "fee", javaType = java.math.BigDecimal.class)
    })
    @Select("SELECT id, account_id, instrument_id, side, quantity, price, idempotency_key, created_on, status, executed_price, fee FROM orders WHERE account_id=#{id} AND status=#{status} AND created_on BETWEEN #{from} AND #{to} ORDER BY created_on DESC")
    List<Order> findOrdersByAccountIdAndStatusAndFromAndTo(@Param("id") Long id, @Param("status") OrderStatus status, @Param("from") Instant from, @Param("to") Instant to);

    @ConstructorArgs({
        @Arg(column = "id", javaType = java.util.UUID.class, id = true),
        @Arg(column = "account_id", javaType = Long.class),
        @Arg(column = "instrument_id", javaType = Long.class),
        @Arg(column = "side", javaType = OrderSide.class),
        @Arg(column = "quantity", javaType = int.class),
        @Arg(column = "price", javaType = java.math.BigDecimal.class),
        @Arg(column = "idempotency_key", javaType = String.class),
        @Arg(column = "created_on", javaType = java.time.Instant.class),
        @Arg(column = "status", javaType = OrderStatus.class),
        @Arg(column = "executed_price", javaType = java.math.BigDecimal.class),
        @Arg(column = "fee", javaType = java.math.BigDecimal.class)
    })
    @Select("SELECT id, account_id, instrument_id, side, quantity, price, idempotency_key, created_on, status, executed_price, fee FROM orders WHERE account_id=#{id} AND created_on BETWEEN #{from} AND #{to} ORDER BY created_on DESC")
    List<Order> findOrdersByAccountIdAndFromAndTo(@Param("id") Long id, @Param("from") Instant from, @Param("to") Instant to);

    @ConstructorArgs({
        @Arg(column = "account_id", javaType = Long.class),
        @Arg(column = "instrument_id", javaType = Long.class),
        @Arg(column = "quantity", javaType = int.class),
        @Arg(column = "average_cost", javaType = java.math.BigDecimal.class),
        @Arg(column = "average_cost_account", javaType = java.math.BigDecimal.class),
        @Arg(column = "version", javaType = int.class)
    })
    @Select("SELECT account_id, instrument_id, quantity, average_cost, average_cost_account, version FROM positions WHERE account_id=#{id}")
    List<Position> findPositionByAccountId(@Param("id") Long id);

    @Update("UPDATE accounts SET cash_balance = #{account.cashBalance}, version = #{newVersion}, last_updated = CURRENT_TIMESTAMP WHERE id = #{account.id} AND version = #{account.version}")
    int updateAccount(@Param("account") Account account, @Param("newVersion") int newVersion);
}