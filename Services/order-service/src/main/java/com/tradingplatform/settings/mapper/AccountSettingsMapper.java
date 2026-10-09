package com.tradingplatform.settings.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.tradingplatform.settings.model.AccountSettings;

/**
 * MyBatis mapper for ACCOUNT_SETTINGS: at most one row per account.
 */
public interface AccountSettingsMapper {

    @Select("SELECT account_id, order_budget, market, default_quantity, updated_at "
        + "FROM account_settings WHERE account_id = #{accountId}")
    AccountSettings findByAccountId(@Param("accountId") long accountId);

    /** Insert or full replace of the account's settings */
    @Insert("INSERT INTO account_settings (account_id, order_budget, market, default_quantity, updated_at) "
        + "VALUES (#{s.accountId}, #{s.orderBudget}, #{s.market}, #{s.defaultQuantity}, #{s.updatedAt}) "
        + "ON CONFLICT (account_id) DO UPDATE SET order_budget = EXCLUDED.order_budget, market = EXCLUDED.market, "
        + "default_quantity = EXCLUDED.default_quantity, updated_at = EXCLUDED.updated_at")
    int upsert(@Param("s") AccountSettings settings);

    @Delete("DELETE FROM account_settings WHERE account_id = #{accountId}")
    int deleteByAccountId(@Param("accountId") long accountId);
}
