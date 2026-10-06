package com.tradingplatform.mappers;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

import com.tradingplatform.domain.Instrument;

public interface InstrumentMapper {

    @Select("SELECT id, symbol, name, asset_class, currency, tradable FROM instruments WHERE symbol = #{symbol}")
    Instrument findBySymbol(@Param("symbol") String symbol);

    @Select("SELECT id, symbol, name, asset_class, currency, tradable FROM instruments WHERE id = #{id}")
    Instrument findById(@Param("id") Long id);

    /**
     * Every instrument symbol, delisted included.
     * Used by the market-data poller so every instrument has a quote on the market-data topic.
     */
    @Select("SELECT symbol FROM instruments ORDER BY symbol")
    List<String> findAllSymbols();
}
