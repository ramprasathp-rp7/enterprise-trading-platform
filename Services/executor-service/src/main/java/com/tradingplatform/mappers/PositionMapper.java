package com.tradingplatform.mappers;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tradingplatform.domain.Position;
import java.util.List;

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
     * Get all distinct symbols with non-zero quantity held across all positions.
     * Used by the market-data poller to determine which symbols to poll for quotes.
     * 
     * @return List of distinct symbol strings where quantity > 0
     */
    @Select("SELECT DISTINCT i.symbol FROM positions p JOIN instruments i ON p.instrument_id = i.id WHERE p.quantity > 0 AND i.tradable = true ORDER BY i.symbol")
    List<String> getDistinctSymbolsWithQuantity();
}
