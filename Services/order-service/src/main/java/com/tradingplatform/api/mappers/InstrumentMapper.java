package com.tradingplatform.api.mappers;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

import com.tradingplatform.domain.model.Instrument;


public interface InstrumentMapper {

    @Select("SELECT id, symbol, name, asset_class, currency, tradable FROM instruments WHERE symbol = #{symbol}")
    Instrument findBySymbol(@Param("symbol") String symbol);

    @Select("SELECT id, symbol, name, asset_class, currency, tradable FROM instruments WHERE id = #{id}")
    Instrument findById(@Param("id") Long id);

    @Select("SELECT id, symbol, name, asset_class, currency, tradable FROM instruments ORDER BY id")
    List<Instrument> findAllInstruments();

    @Select("SELECT id, symbol, name, asset_class, currency, tradable FROM instruments WHERE tradable = TRUE ORDER BY id")
    List<Instrument> findTradableInstruments();
    /** The catalogue for the instruments API, with each instrument's exchange. */
    @Select("SELECT id, symbol, name, asset_class, currency, tradable, exchange FROM instruments ORDER BY id")
    List<com.tradingplatform.instruments.model.InstrumentListing> findAllListings();

    @Select("SELECT id, symbol, name, asset_class, currency, tradable, exchange FROM instruments WHERE symbol = #{symbol}")
    com.tradingplatform.instruments.model.InstrumentListing findListingBySymbol(@Param("symbol") String symbol);

    /**
     * Adds an instrument found by symbol discovery. The symbol is UNIQUE, so a concurrent discovery of
     * the same symbol fails with DuplicateKeyException and the caller reads the winner back.
     */
    @org.apache.ibatis.annotations.Insert("INSERT INTO instruments (symbol, name, asset_class, currency, tradable, exchange) "
        + "VALUES (#{i.symbol}, #{i.name}, #{i.assetClass}, #{i.currency}, #{i.tradable}, #{i.exchange})")
    @org.apache.ibatis.annotations.Options(useGeneratedKeys = true, keyProperty = "i.id", keyColumn = "id")
    int insertDiscovered(@Param("i") com.tradingplatform.instruments.model.InstrumentListing instrument);

    /** Every instrument symbol, delisted included, so each one has a cached quote. */
    @Select("SELECT symbol FROM instruments ORDER BY symbol")
    List<String> findAllSymbols();

    /**
     * FX pairs needed to convert instrument currencies into account currencies, quoted as
     * FX:{account}{instrument} (e.g. FX:USDINR), so the quote cache holds a rate for every
     * currency an instrument trades in, even one with no tradable FX instrument.
     */
    @Select("SELECT DISTINCT 'FX:' || a.currency || i.currency FROM instruments i "
        + "CROSS JOIN (SELECT DISTINCT currency FROM accounts) a WHERE i.currency <> a.currency")
    List<String> findConversionFxSymbols();
}
