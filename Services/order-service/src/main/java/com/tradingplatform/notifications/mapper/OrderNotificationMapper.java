package com.tradingplatform.notifications.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.tradingplatform.notifications.model.OrderDetails;
import com.tradingplatform.notifications.model.OrderNotification;

/**
 * MyBatis mapper for ORDER_NOTIFICATIONS, plus the order lookup that enriches a trade event.
 */
public interface OrderNotificationMapper {

    String SELECT_COLUMNS = "SELECT notification_id, event_id, account_id, order_id::text AS order_id, event_type, "
        + "status, symbol, side, quantity, limit_price, executed_price, reason, occurred_at, channels, in_app, created_at "
        + "FROM order_notifications ";

    /**
     * Stores a notification. A redelivered event (same event_id) is ignored.
     *
     * @return 1 if stored, 0 if this event was already stored
     */
    @Insert("INSERT INTO order_notifications (event_id, account_id, order_id, event_type, status, symbol, side, "
            + "quantity, limit_price, executed_price, reason, occurred_at, channels, in_app) "
            + "VALUES (#{n.eventId}, #{n.accountId}, #{n.orderId}::uuid, #{n.eventType}, #{n.status}, #{n.symbol}, "
            + "#{n.side}, #{n.quantity}, #{n.limitPrice}, #{n.executedPrice}, #{n.reason}, #{n.occurredAt}, "
            + "#{n.channels}, #{n.inApp}) "
            + "ON CONFLICT (event_id) DO NOTHING")
    int insertIgnoringDuplicate(@Param("n") OrderNotification notification);

    /** Symbol, side, quantity and limit price of an order, or null if the order is unknown. */
    @Select("SELECT i.symbol AS symbol, o.side AS side, o.quantity AS quantity, o.price AS limit_price "
            + "FROM orders o JOIN instruments i ON i.id = o.instrument_id WHERE o.id = #{orderId}::uuid")
    OrderDetails findOrderDetails(@Param("orderId") String orderId);

    /** In-app notifications for the account with an id greater than afterId, oldest first. */
    @Select(SELECT_COLUMNS + "WHERE account_id = #{accountId} AND in_app AND notification_id > #{afterId} "
            + "ORDER BY notification_id ASC LIMIT #{limit}")
    List<OrderNotification> findAfter(@Param("accountId") long accountId,
                                      @Param("afterId") long afterId,
                                      @Param("limit") int limit);

    /** History page: the account's most recent notifications on every channel, newest first. */
    @Select(SELECT_COLUMNS + "WHERE account_id = #{accountId} ORDER BY notification_id DESC LIMIT #{limit}")
    List<OrderNotification> findHistory(@Param("accountId") long accountId, @Param("limit") int limit);

    /** The account's most recent in-app notifications, newest first. */
    @Select(SELECT_COLUMNS + "WHERE account_id = #{accountId} AND in_app ORDER BY notification_id DESC LIMIT #{limit}")
    List<OrderNotification> findLatest(@Param("accountId") long accountId, @Param("limit") int limit);
}
