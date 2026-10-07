package com.tradingplatform.strategy.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tradingplatform.strategy.model.IndicatorState;
import com.tradingplatform.strategy.model.StrategyType;
import com.tradingplatform.strategy.model.TradingStrategy;

/**
 * MyBatis mapper for TRADING_STRATEGIES.
 * Customer-facing queries are scoped by account_id. findActiveSymbols, findActiveBySymbol,
 * lockById and the record* updates serve the evaluator, which works across accounts.
 */
public interface TradingStrategyMapper {

    String COLUMNS = "strategy_id, account_id, symbol, strategy_type, short_window, long_window, band_window, band_k, "
        + "trade_side, quantity, max_position, max_daily_notional, max_consecutive_failures, active, disabled_reason, "
        + "last_state, last_evaluated_quote_at, consecutive_failures, created_at, updated_at";

    @Select("SELECT " + COLUMNS + " FROM trading_strategies WHERE account_id = #{accountId} "
            + "ORDER BY created_at DESC, strategy_id DESC")
    List<TradingStrategy> findByAccountId(@Param("accountId") Long accountId);

    @Select("SELECT " + COLUMNS + " FROM trading_strategies WHERE strategy_id = #{strategyId} AND account_id = #{accountId}")
    TradingStrategy findById(@Param("strategyId") Long strategyId, @Param("accountId") Long accountId);

    @Select("SELECT count(*) FROM trading_strategies WHERE account_id = #{accountId} AND active")
    int countActiveByAccountId(@Param("accountId") Long accountId);

    @Select("SELECT EXISTS(SELECT 1 FROM trading_strategies WHERE account_id = #{accountId} AND active "
            + "AND symbol = #{symbol} AND strategy_type = #{type})")
    boolean existsActiveRule(@Param("accountId") Long accountId,
                             @Param("symbol") String symbol,
                             @Param("type") StrategyType type);

    @Insert("INSERT INTO trading_strategies (account_id, symbol, strategy_type, short_window, long_window, band_window, "
            + "band_k, trade_side, quantity, max_position, max_daily_notional, max_consecutive_failures) VALUES ("
            + "#{s.accountId}, #{s.symbol}, #{s.strategyType}, #{s.shortWindow}, #{s.longWindow}, #{s.bandWindow}, "
            + "#{s.bandK}, #{s.tradeSide}, #{s.quantity}, #{s.maxPosition}, #{s.maxDailyNotional}, #{s.maxConsecutiveFailures})")
    @Options(useGeneratedKeys = true, keyProperty = "s.strategyId", keyColumn = "strategy_id")
    int insert(@Param("s") TradingStrategy strategy);

    /**
     * Turns a strategy off (reason USER) or on. Turning on clears the failure count and the
     * stored state, so the strategy cannot fire on a crossing that happened while it was off.
     * Blocks while the evaluator holds the row (see lockById), so a disable is never lost mid-order.
     */
    @Update("UPDATE trading_strategies SET active = #{active}, "
            + "disabled_reason = CASE WHEN #{active} THEN NULL ELSE 'USER' END, "
            + "consecutive_failures = CASE WHEN #{active} THEN 0 ELSE consecutive_failures END, "
            + "last_state = CASE WHEN #{active} AND NOT active THEN NULL ELSE last_state END "
            + "WHERE strategy_id = #{strategyId} AND account_id = #{accountId}")
    int updateActive(@Param("strategyId") Long strategyId,
                     @Param("accountId") Long accountId,
                     @Param("active") boolean active);

    @Delete("DELETE FROM trading_strategies WHERE strategy_id = #{strategyId} AND account_id = #{accountId}")
    int delete(@Param("strategyId") Long strategyId, @Param("accountId") Long accountId);

    /** Symbols at least one active strategy trades */
    @Select("SELECT DISTINCT symbol FROM trading_strategies WHERE active")
    List<String> findActiveSymbols();

    @Select("SELECT " + COLUMNS + " FROM trading_strategies WHERE symbol = #{symbol} AND active ORDER BY strategy_id")
    List<TradingStrategy> findActiveBySymbol(@Param("symbol") String symbol);

    /**
     * Locks the strategy row until the calling transaction ends. Taken before an order is sent,
     * so a concurrent disable either lands first (and is seen here) or waits for the order to finish.
     */
    @Select("SELECT " + COLUMNS + " FROM trading_strategies WHERE strategy_id = #{strategyId} FOR UPDATE")
    TradingStrategy lockById(@Param("strategyId") Long strategyId);

    /** Stores the state after evaluating a quote; ignored for inactive strategies and already-evaluated quotes */
    @Update("UPDATE trading_strategies SET last_state = #{state}, last_evaluated_quote_at = #{quoteAt} "
            + "WHERE strategy_id = #{strategyId} AND active "
            + "AND (last_evaluated_quote_at IS NULL OR last_evaluated_quote_at < #{quoteAt})")
    int recordEvaluation(@Param("strategyId") Long strategyId,
                         @Param("state") IndicatorState state,
                         @Param("quoteAt") Instant quoteAt);

    @Update("UPDATE trading_strategies SET consecutive_failures = 0 WHERE strategy_id = #{strategyId}")
    int resetFailures(@Param("strategyId") Long strategyId);

    /** Counts a failed order; the strategy turns itself off (reason FAILURES) when it reaches its limit */
    @Update("UPDATE trading_strategies SET consecutive_failures = consecutive_failures + 1, "
            + "active = CASE WHEN consecutive_failures + 1 >= max_consecutive_failures THEN false ELSE active END, "
            + "disabled_reason = CASE WHEN consecutive_failures + 1 >= max_consecutive_failures THEN 'FAILURES' "
            + "ELSE disabled_reason END "
            + "WHERE strategy_id = #{strategyId}")
    int recordFailure(@Param("strategyId") Long strategyId);
}
