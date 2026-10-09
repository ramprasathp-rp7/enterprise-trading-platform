package com.tradingplatform.instruments.model;

/**
 * One row of the instrument catalogue as the instruments API lists it, including the exchange.
 * A plain bean so MyBatis maps it by property name.
 */
public class InstrumentListing {

    private Long id;
    private String symbol;
    private String name;
    private String assetClass;
    private String currency;
    private boolean tradable;
    /** NYSE, NASDAQ, NSE, BSE, FX or CRYPTO; null for seed rows the migration did not backfill */
    private String exchange;

    public InstrumentListing() {
    }

    public InstrumentListing(Long id, String symbol, String name, String assetClass, String currency,
                             boolean tradable, String exchange) {
        this.id = id;
        this.symbol = symbol;
        this.name = name;
        this.assetClass = assetClass;
        this.currency = currency;
        this.tradable = tradable;
        this.exchange = exchange;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAssetClass() {
        return assetClass;
    }

    public void setAssetClass(String assetClass) {
        this.assetClass = assetClass;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public boolean isTradable() {
        return tradable;
    }

    public void setTradable(boolean tradable) {
        this.tradable = tradable;
    }

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }
}
