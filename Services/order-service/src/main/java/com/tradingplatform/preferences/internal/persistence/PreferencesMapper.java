package com.tradingplatform.preferences.internal.persistence;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * MyBatis mapper for the preferences module. Touches customer_preferences only.
 *
 * One row per (account, channel). A save replaces all of an account's rows; the service runs the
 * delete and the inserts in one transaction.
 */
@Mapper
public interface PreferencesMapper {

    @Select("""
            SELECT account_id, alert_channel, contact_detail_enc, updated_at
            FROM customer_preferences
            WHERE account_id = #{accountId}
            """)
    List<PreferenceRecord> findByAccountId(@Param("accountId") long accountId);

    @Insert("""
            INSERT INTO customer_preferences (account_id, alert_channel, contact_detail_enc, created_at, updated_at)
            VALUES (#{accountId}, #{alertChannel}, #{contactDetailEnc,jdbcType=BINARY}, #{updatedAt}, #{updatedAt})
            """)
    int insert(PreferenceRecord preference);

    @Delete("DELETE FROM customer_preferences WHERE account_id = #{accountId}")
    int deleteByAccountId(@Param("accountId") long accountId);
}
