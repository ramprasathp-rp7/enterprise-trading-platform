package com.tradingplatform.mappers;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tradingplatform.domain.Position;

public interface PositionMapper {

    @ConstructorArgs({
        @Arg(column = "account_id", javaType = Long.class),
        @Arg(column = "instrument_id", javaType = Long.class),
        @Arg(column = "quantity", javaType = int.class),
        @Arg(column = "average_cost", javaType = java.math.BigDecimal.class),
        @Arg(column = "version", javaType = int.class)
    })
    @Select("SELECT account_id, instrument_id, quantity, average_cost, version FROM positions WHERE account_id = #{accountId} AND instrument_id = #{instrumentId}")
    Position findByAccountAndInstrument(@Param("accountId") Long accountId, @Param("instrumentId") Long instrumentId);

    @Update("UPDATE positions SET quantity = #{position.quantity}, average_cost = #{position.averageCost}, version = #{newVersion} WHERE account_id = #{position.accountId} AND instrument_id = #{position.instrumentId} AND version = #{position.version}")
    int updatePosition(@Param("position") Position position, @Param("newVersion") int newVersion);
}
