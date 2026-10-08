package com.tradingplatform.watchlist.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tradingplatform.watchlist.model.Watchlist;
import com.tradingplatform.watchlist.model.WatchlistItem;

/**
 * MyBatis mapper for WATCHLISTS and WATCHLIST_ITEMS.
 * Every query is scoped by account_id so one account can never read or change the lists of another.
 */
public interface WatchlistMapper {

    @Select("SELECT watchlist_id, name, account_id, created_at, updated_at FROM watchlists "
            + "WHERE account_id = #{accountId} ORDER BY created_at, watchlist_id")
    List<Watchlist> findByAccountId(@Param("accountId") Long accountId);

    @Select("SELECT watchlist_id, name, account_id, created_at, updated_at FROM watchlists "
            + "WHERE watchlist_id = #{watchlistId} AND account_id = #{accountId}")
    Watchlist findById(@Param("watchlistId") Long watchlistId, @Param("accountId") Long accountId);

    @Select("SELECT EXISTS(SELECT 1 FROM watchlists WHERE account_id = #{accountId} AND lower(name) = lower(#{name}))")
    boolean existsByName(@Param("accountId") Long accountId, @Param("name") String name);

    @Insert("INSERT INTO watchlists (name, account_id) VALUES (#{watchlist.name}, #{watchlist.accountId})")
    @Options(useGeneratedKeys = true, keyProperty = "watchlist.watchlistId", keyColumn = "watchlist_id")
    int insertWatchlist(@Param("watchlist") Watchlist watchlist);

    @Delete("DELETE FROM watchlists WHERE watchlist_id = #{watchlistId} AND account_id = #{accountId}")
    int deleteWatchlist(@Param("watchlistId") Long watchlistId, @Param("accountId") Long accountId);

    /** Bumps updated_at (via trigger) after the list's contents change */
    @Update("UPDATE watchlists SET name = name WHERE watchlist_id = #{watchlistId}")
    int touch(@Param("watchlistId") Long watchlistId);

    @Select("SELECT watchlist_item_id, watchlist_id, account_id, symbol, created_at FROM watchlist_items "
            + "WHERE account_id = #{accountId} ORDER BY created_at, watchlist_item_id")
    List<WatchlistItem> findItemsByAccountId(@Param("accountId") Long accountId);

    @Select("SELECT watchlist_item_id, watchlist_id, account_id, symbol, created_at FROM watchlist_items "
            + "WHERE watchlist_id = #{watchlistId} ORDER BY created_at, watchlist_item_id")
    List<WatchlistItem> findItemsByWatchlistId(@Param("watchlistId") Long watchlistId);

    @Select("SELECT watchlist_item_id, watchlist_id, account_id, symbol, created_at FROM watchlist_items "
            + "WHERE watchlist_item_id = #{watchlistItemId}")
    WatchlistItem findItemById(@Param("watchlistItemId") Long watchlistItemId);

    @Select("SELECT EXISTS(SELECT 1 FROM watchlist_items WHERE watchlist_id = #{watchlistId} AND symbol = #{symbol})")
    boolean itemExists(@Param("watchlistId") Long watchlistId, @Param("symbol") String symbol);

    @Insert("INSERT INTO watchlist_items (watchlist_id, account_id, symbol) "
            + "VALUES (#{item.watchlistId}, #{item.accountId}, #{item.symbol})")
    @Options(useGeneratedKeys = true, keyProperty = "item.watchlistItemId", keyColumn = "watchlist_item_id")
    int insertItem(@Param("item") WatchlistItem item);

    @Delete("DELETE FROM watchlist_items WHERE watchlist_item_id = #{watchlistItemId} "
            + "AND watchlist_id = #{watchlistId} AND account_id = #{accountId}")
    int deleteItem(@Param("watchlistItemId") Long watchlistItemId,
                   @Param("watchlistId") Long watchlistId,
                   @Param("accountId") Long accountId);
}
