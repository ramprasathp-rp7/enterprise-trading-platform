package com.tradingplatform.watchlist.alerts.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tradingplatform.watchlist.alerts.model.AlertDirection;
import com.tradingplatform.watchlist.alerts.model.AlertMode;
import com.tradingplatform.watchlist.alerts.model.PriceAlert;

/**
 * MyBatis mapper for PRICE_ALERTS.
 * Customer-facing queries are scoped by account_id. findActiveSymbols, findActiveBySymbol and
 * recordFired serve the evaluator, which works across accounts; the per-symbol lookups use the
 * partial index ix_price_alerts_active_symbol.
 */
public interface PriceAlertMapper {

    String COLUMNS = "alert_id, account_id, symbol, direction, threshold, mode, active, last_quote_at, "
        + "last_triggered_at, last_triggered_price, trigger_count, deactivated_reason, created_at, updated_at";

    @Select("<script>SELECT " + COLUMNS + " FROM price_alerts WHERE account_id = #{accountId}"
            + "<if test='active != null'> AND active = #{active}</if>"
            + "<if test='symbol != null'> AND symbol = #{symbol}</if>"
            + " ORDER BY created_at DESC, alert_id DESC</script>")
    List<PriceAlert> findByAccountId(@Param("accountId") Long accountId,
                                     @Param("active") Boolean active,
                                     @Param("symbol") String symbol);

    @Select("SELECT " + COLUMNS + " FROM price_alerts WHERE alert_id = #{alertId} AND account_id = #{accountId}")
    PriceAlert findById(@Param("alertId") Long alertId, @Param("accountId") Long accountId);

    @Select("SELECT count(*) FROM price_alerts WHERE account_id = #{accountId}")
    int countByAccountId(@Param("accountId") Long accountId);

    @Select("SELECT count(*) FROM price_alerts WHERE account_id = #{accountId} AND active")
    int countActiveByAccountId(@Param("accountId") Long accountId);

    @Select("SELECT EXISTS(SELECT 1 FROM price_alerts WHERE account_id = #{accountId} AND active "
            + "AND symbol = #{symbol} AND direction = #{direction} AND threshold = #{threshold} AND mode = #{mode})")
    boolean existsActiveRule(@Param("accountId") Long accountId,
                             @Param("symbol") String symbol,
                             @Param("direction") AlertDirection direction,
                             @Param("threshold") BigDecimal threshold,
                             @Param("mode") AlertMode mode);

    @Insert("INSERT INTO price_alerts (account_id, symbol, direction, threshold, mode) "
            + "VALUES (#{alert.accountId}, #{alert.symbol}, #{alert.direction}, #{alert.threshold}, #{alert.mode})")
    @Options(useGeneratedKeys = true, keyProperty = "alert.alertId", keyColumn = "alert_id")
    int insert(@Param("alert") PriceAlert alert);

    /** Pause (active=false, reason PAUSED) or resume (active=true, reason cleared) */
    @Update("UPDATE price_alerts SET active = #{active}, deactivated_reason = #{deactivatedReason} "
            + "WHERE alert_id = #{alertId} AND account_id = #{accountId}")
    int updateActive(@Param("alertId") Long alertId,
                     @Param("accountId") Long accountId,
                     @Param("active") boolean active,
                     @Param("deactivatedReason") String deactivatedReason);

    @Delete("DELETE FROM price_alerts WHERE alert_id = #{alertId} AND account_id = #{accountId}")
    int delete(@Param("alertId") Long alertId, @Param("accountId") Long accountId);

    /** Symbols at least one customer has an active alert on */
    @Select("SELECT DISTINCT symbol FROM price_alerts WHERE active")
    List<String> findActiveSymbols();

    @Select("SELECT " + COLUMNS + " FROM price_alerts WHERE symbol = #{symbol} AND active ORDER BY alert_id")
    List<PriceAlert> findActiveBySymbol(@Param("symbol") String symbol);

    /**
     * Records a delivered firing. Idempotent per quote: an alert already updated for this quote
     * (or one paused meanwhile) is left alone. A ONCE alert is deactivated in the same statement.
     */
    @Update("UPDATE price_alerts SET last_quote_at = #{quoteAt}, last_triggered_at = now(), "
            + "last_triggered_price = #{price}, trigger_count = trigger_count + 1, "
            + "active = CASE WHEN #{deactivate} THEN false ELSE active END, "
            + "deactivated_reason = CASE WHEN #{deactivate} THEN 'TRIGGERED_ONCE' ELSE deactivated_reason END "
            + "WHERE alert_id = #{alertId} AND active "
            + "AND (last_quote_at IS NULL OR last_quote_at < #{quoteAt})")
    int recordFired(@Param("alertId") Long alertId,
                    @Param("price") BigDecimal price,
                    @Param("quoteAt") Instant quoteAt,
                    @Param("deactivate") boolean deactivate);
}
