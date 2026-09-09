package com.tradingplatform.api.mappers;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tradingplatform.domain.model.Position;

public interface PositionMapper {

    @Select("SELECT account_id, instrument_id, quantity, average_cost FROM positions WHERE account_id = #{accountId} AND instrument_id = #{instrumentId}")
    Position findByAccountAndInstrument(Long accountId, Long instrumentId);

    @Update("UPDATE positions SET quantity = #{quantity}, average_cost = #{averageCost} WHERE account_id = #{accountId} AND instrument_id = #{instrumentId}")
    int updatePosition(Position position);
}
