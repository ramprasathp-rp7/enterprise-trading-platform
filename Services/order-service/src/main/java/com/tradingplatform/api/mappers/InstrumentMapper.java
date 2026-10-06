package com.tradingplatform.api.mappers;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.tradingplatform.domain.model.Instrument;

import java.util.List;

public interface InstrumentMapper {

    @Select("SELECT id, symbol, name, asset_class, currency, tradable FROM instruments WHERE symbol = #{symbol}")
    Instrument findBySymbol(@Param("symbol") String symbol);

    @Select("SELECT id, symbol, name, asset_class, currency, tradable FROM instruments WHERE id = #{id}")
    Instrument findById(@Param("id") Long id);

    @Select("SELECT id, symbol, name, asset_class, currency, tradable FROM instruments ORDER BY id")
    List<Instrument> findAllInstruments();

    @Select("SELECT id, symbol, name, asset_class, currency, tradable FROM instruments WHERE tradable = TRUE ORDER BY id")
    List<Instrument> findTradableInstruments();
}
