package com.tradingplatform.quote.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class QuoteWrapper {
    private String symbol;
    private String source;
    private Boolean stale;
    private Quote quote;

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Boolean getStale() { return stale; }
    public void setStale(Boolean stale) { this.stale = stale; }
    public Quote getQuote() { return quote; }
    public void setQuote(Quote quote) { this.quote = quote; }
}
