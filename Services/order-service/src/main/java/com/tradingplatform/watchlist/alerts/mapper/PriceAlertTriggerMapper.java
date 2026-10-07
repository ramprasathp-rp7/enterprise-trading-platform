package com.tradingplatform.watchlist.alerts.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tradingplatform.watchlist.alerts.model.DeliveryStatus;
import com.tradingplatform.watchlist.alerts.model.PriceAlertTrigger;

/**
 * MyBatis mapper for PRICE_ALERT_TRIGGERS. Reads join the parent alert for symbol, direction,
 * threshold and mode, and are scoped by account_id.
 */
public interface PriceAlertTriggerMapper {

    String SELECT_JOINED = "SELECT t.trigger_id, t.alert_id, t.account_id, t.price, t.quote_at, t.triggered_at, "
        + "t.reason, t.delivery_status, a.symbol, a.direction, a.threshold, a.mode "
        + "FROM price_alert_triggers t JOIN price_alerts a ON a.alert_id = t.alert_id ";

    /**
     * Records a firing as PENDING. A retry of the same (alert, quote) after a FAILED delivery reuses
     * the existing row. If that row is already DELIVERED nothing changes and triggerId stays null.
     */
    @Insert("INSERT INTO price_alert_triggers (alert_id, account_id, price, quote_at, reason, delivery_status) "
            + "VALUES (#{t.alertId}, #{t.accountId}, #{t.price}, #{t.quoteAt}, #{t.reason}, 'PENDING') "
            + "ON CONFLICT (alert_id, quote_at) DO UPDATE SET delivery_status = 'PENDING', "
            + "reason = EXCLUDED.reason, triggered_at = now() "
            + "WHERE price_alert_triggers.delivery_status <> 'DELIVERED'")
    @Options(useGeneratedKeys = true, keyProperty = "t.triggerId", keyColumn = "trigger_id")
    int upsertPending(@Param("t") PriceAlertTrigger trigger);

    @Update("UPDATE price_alert_triggers SET delivery_status = #{status} WHERE trigger_id = #{triggerId}")
    int updateDeliveryStatus(@Param("triggerId") Long triggerId, @Param("status") DeliveryStatus status);

    @Select(SELECT_JOINED + "WHERE t.alert_id = #{alertId} AND t.account_id = #{accountId} "
            + "ORDER BY t.triggered_at DESC, t.trigger_id DESC")
    List<PriceAlertTrigger> findByAlertId(@Param("alertId") Long alertId, @Param("accountId") Long accountId);

    @Select("<script>" + SELECT_JOINED + "WHERE t.account_id = #{accountId}"
            + "<if test='since != null'> AND t.triggered_at &gt; #{since}</if>"
            + " ORDER BY t.triggered_at DESC, t.trigger_id DESC LIMIT #{limit}</script>")
    List<PriceAlertTrigger> findRecentByAccountId(@Param("accountId") Long accountId,
                                                  @Param("since") Instant since,
                                                  @Param("limit") int limit);
}
