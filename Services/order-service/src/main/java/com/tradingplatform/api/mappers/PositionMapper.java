package com.tradingplatform.api.mappers;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import org.apache.ibatis.annotations.Update;

import com.tradingplatform.domain.model.Position;

public interface PositionMapper {

    @ConstructorArgs({
        @Arg(column = "account_id", javaType = Long.class),
        @Arg(column = "instrument_id", javaType = Long.class),
        @Arg(column = "quantity", javaType = int.class),
        @Arg(column = "average_cost", javaType = java.math.BigDecimal.class),
        @Arg(column = "average_cost_account", javaType = java.math.BigDecimal.class),
        @Arg(column = "version", javaType = int.class)
    })
    @Select("SELECT account_id, instrument_id, quantity, average_cost, average_cost_account, version FROM positions WHERE account_id = #{accountId} AND instrument_id = #{instrumentId}")
    Position findByAccountAndInstrument(@Param("accountId") Long accountId, @Param("instrumentId") Long instrumentId);

    @Update("UPDATE positions SET quantity = #{position.quantity}, average_cost = #{position.averageCost}, average_cost_account = #{position.averageCostAccount}, version = #{newVersion} WHERE account_id = #{position.accountId} AND instrument_id = #{position.instrumentId} AND version = #{position.version}")
    int updatePosition(@Param("position") Position position, @Param("newVersion") int newVersion);

    /**
     * Insert a new position into the database.
     * Used when settling an order for the first time on a new (accountId, instrumentId) pair.
     * Version is set to 1 (new position just created in settlement).
     *
     * @param position Position object with quantity and average_cost already calculated
     * @return Number of rows inserted (1 on success)
     */
    @Insert("INSERT INTO positions (account_id, instrument_id, quantity, average_cost, average_cost_account, version) VALUES (#{position.accountId}, #{position.instrumentId}, #{position.quantity}, #{position.averageCost}, #{position.averageCostAccount}, 1)")
    int insertPosition(@Param("position") Position position);

        /**
     * Find all positions for an account (including zero-quantity positions).
     * Used by AccountHistoryScheduler for daily snapshots.
     * 
     * @param accountId Account ID
     * @return List of all positions for the account
     */
    @ConstructorArgs({
        @Arg(column = "account_id", javaType = Long.class),
        @Arg(column = "instrument_id", javaType = Long.class),
        @Arg(column = "quantity", javaType = int.class),
        @Arg(column = "average_cost", javaType = java.math.BigDecimal.class),
        @Arg(column = "average_cost_account", javaType = java.math.BigDecimal.class),
        @Arg(column = "version", javaType = int.class)
    })
    @Select("SELECT account_id, instrument_id, quantity, average_cost, average_cost_account, version FROM positions WHERE account_id = #{accountId}")
    List<Position> findByAccountId(@Param("accountId") Long accountId);
}
