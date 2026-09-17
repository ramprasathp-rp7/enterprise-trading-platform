package com.tradingplatform.domain.model;

import com.tradingplatform.domain.enums.AssetClass;

import java.util.Objects;

/* tradable and delisted instruments with AssetClass enum (Equity, FX, Crypto) */
public final class Instrument {

    private final Long id;
    private final String symbol;
    private final String name;
    private final AssetClass assetClass;
    private final String currency;
    private final boolean tradable;

    public Instrument(Long id, String symbol, String name, AssetClass assetClass,
                       String currency, boolean tradable) {
        this.id = id;
        this.symbol = Objects.requireNonNull(symbol, "symbol");
        this.name = Objects.requireNonNull(name, "name");
        this.assetClass = Objects.requireNonNull(assetClass, "assetClass");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.tradable = tradable;
    }

    public boolean isTradable() {
        return tradable;
    }

    public Long getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }

    public AssetClass getAssetClass() {
        return assetClass;
    }

    public String getCurrency() {
        return currency;
    }
    
    /** A tradable instrument, for the common case of constructing reference data by hand. */
    public static Instrument tradable(Long id, String symbol, String name, AssetClass assetClass, String currency) {
        return new Instrument(id, symbol, name, assetClass, currency, true);
    }

    /** A delisted instrument: still resolvable, refused for new orders (business rule 3). */
    public static Instrument delisted(Long id, String symbol, String name, AssetClass assetClass, String currency) {
        return new Instrument(id, symbol, name, assetClass, currency, false);
    }

}
