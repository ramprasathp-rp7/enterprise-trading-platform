package com.tradingplatform.api.mappers;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tradingplatform.domain.model.Position;

public interface PositionMapper {

    @Select("SELECT account_id, instrument_id, quantity, average_cost, version FROM positions WHERE account_id = #{accountId} AND instrument_id = #{instrumentId}")
    Position findByAccountAndInstrument(Long accountId, Long instrumentId);

    @Update("UPDATE positions SET quantity = #{quantity}, average_cost = #{averageCost}, version = #{newVersion} WHERE account_id = #{accountId} AND instrument_id = #{instrumentId} AND version = #{version}")
    int updatePosition(Position position, int newVersion);
}
