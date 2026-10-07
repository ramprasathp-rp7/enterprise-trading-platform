package com.tradingplatform.strategy.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.tradingplatform.strategy.model.StrategyExecution;

/** MyBatis mapper for STRATEGY_EXECUTIONS */
public interface StrategyExecutionMapper {

    String COLUMNS = "execution_id, strategy_id, account_id, symbol, quote_received_at, signal, price, quantity, notional, "
        + "indicators::text AS indicators_json, reason, outcome, order_id, error_code, error_message, created_at";

    /** Records a trigger. A second trigger of the same strategy by the same quote is ignored. */
    @Insert("INSERT INTO strategy_executions (strategy_id, account_id, symbol, quote_received_at, signal, price, quantity, "
            + "notional, indicators, reason, outcome, order_id, error_code, error_message) VALUES ("
            + "#{e.strategyId}, #{e.accountId}, #{e.symbol}, #{e.quoteReceivedAt}, #{e.signal}, #{e.price}, #{e.quantity}, "
            + "#{e.notional}, CAST(#{e.indicatorsJson} AS jsonb), #{e.reason}, #{e.outcome}, #{e.orderId}, "
            + "#{e.errorCode}, #{e.errorMessage}) "
            + "ON CONFLICT (strategy_id, quote_received_at) DO NOTHING")
    @Options(useGeneratedKeys = true, keyProperty = "e.executionId", keyColumn = "execution_id")
    int insert(@Param("e") StrategyExecution execution);

    @Select("SELECT " + COLUMNS + " FROM strategy_executions WHERE strategy_id = #{strategyId} "
            + "ORDER BY created_at DESC, execution_id DESC LIMIT #{limit}")
    List<StrategyExecution> findByStrategyId(@Param("strategyId") Long strategyId, @Param("limit") int limit);

    /** Notional of the orders a strategy placed since an instant (the daily spend cap) */
    @Select("SELECT COALESCE(SUM(notional), 0) FROM strategy_executions "
            + "WHERE strategy_id = #{strategyId} AND outcome = 'PLACED' AND created_at >= #{since}")
    BigDecimal sumPlacedNotionalSince(@Param("strategyId") Long strategyId, @Param("since") Instant since);
}
