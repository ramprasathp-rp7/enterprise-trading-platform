package com.tradingplatform.preferences.internal.persistence;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * MyBatis mapper for admin preferences. Touches admin_preferences only.
 *
 * One row per (admin user, channel). A save replaces all of an admin's rows; the service runs the
 * delete and the inserts in one transaction.
 */
@Mapper
public interface AdminPreferencesMapper {

    @Select("""
            SELECT admin_user_id, alert_channel, contact_detail_enc, updated_at
            FROM admin_preferences
            WHERE admin_user_id = #{adminUserId}
            """)
    List<AdminPreferenceRecord> findByAdminUserId(@Param("adminUserId") String adminUserId);

    @Insert("""
            INSERT INTO admin_preferences (admin_user_id, alert_channel, contact_detail_enc, created_at, updated_at)
            VALUES (#{adminUserId}, #{alertChannel}, #{contactDetailEnc,jdbcType=BINARY}, #{updatedAt}, #{updatedAt})
            """)
    int insert(AdminPreferenceRecord preference);

    @Delete("DELETE FROM admin_preferences WHERE admin_user_id = #{adminUserId}")
    int deleteByAdminUserId(@Param("adminUserId") String adminUserId);
}
