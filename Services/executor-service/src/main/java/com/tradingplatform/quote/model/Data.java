package com.tradingplatform.quote.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Data {
    private String symbol;
    private String interval;
    private String currency;
    private List<QuoteWrapper> quotes;

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getInterval() { return interval; }
    public void setInterval(String interval) { this.interval = interval; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public List<QuoteWrapper> getQuotes() { return quotes; }
    public void setQuotes(List<QuoteWrapper> quotes) { this.quotes = quotes; }
}
