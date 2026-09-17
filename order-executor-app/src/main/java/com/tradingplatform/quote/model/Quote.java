package com.tradingplatform.quote.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Quote {
    private String symbol;
    private BigDecimal price;
    private BigDecimal bid;
    private BigDecimal ask;
    private BigDecimal spreadBps;
    private String currency;
    private BigDecimal change;
    private BigDecimal changePercent;
    private BigDecimal previousClose;
    private String asOf;
    private String marketState;

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getBid() { return bid; }
    public void setBid(BigDecimal bid) { this.bid = bid; }
    public BigDecimal getAsk() { return ask; }
    public void setAsk(BigDecimal ask) { this.ask = ask; }
    public BigDecimal getSpreadBps() { return spreadBps; }
    public void setSpreadBps(BigDecimal spreadBps) { this.spreadBps = spreadBps; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getChange() { return change; }
    public void setChange(BigDecimal change) { this.change = change; }
    public BigDecimal getChangePercent() { return changePercent; }
    public void setChangePercent(BigDecimal changePercent) { this.changePercent = changePercent; }
    public BigDecimal getPreviousClose() { return previousClose; }
    public void setPreviousClose(BigDecimal previousClose) { this.previousClose = previousClose; }
    public String getAsOf() { return asOf; }
    public void setAsOf(String asOf) { this.asOf = asOf; }
    public String getMarketState() { return marketState; }
    public void setMarketState(String marketState) { this.marketState = marketState; }
}
