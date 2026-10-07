package com.tradingplatform.api.mappers;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

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
    /** Every instrument symbol, delisted included, so each one has a cached quote. */
    @Select("SELECT symbol FROM instruments ORDER BY symbol")
    List<String> findAllSymbols();
}
