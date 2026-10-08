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

    /**
     * FX pairs needed to convert instrument currencies into account currencies, quoted as
     * FX:{account}{instrument} (e.g. FX:USDINR). Polled with the instruments so the market-data cache
     * holds a rate even for currencies that have no tradable FX instrument.
     */
    @Select("SELECT DISTINCT 'FX:' || a.currency || i.currency FROM instruments i "
        + "CROSS JOIN (SELECT DISTINCT currency FROM accounts) a WHERE i.currency <> a.currency")
    List<String> findConversionFxSymbols();
}
