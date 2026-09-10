package com.tradingplatform.api.mappers;

import org.apache.ibatis.annotations.Select;

import com.tradingplatform.domain.model.Instrument;

public interface InstrumentMapper {

    @Select("SELECT id, symbol, name, asset_class, currency, tradable FROM instruments WHERE symbol = #{symbol}")
    Instrument findBySymbol(String symbol);

    @Select("SELECT id, symbol, name, asset_class, currency, tradable FROM instruments WHERE id = #{id}")
    Instrument findById(Long id);
}
