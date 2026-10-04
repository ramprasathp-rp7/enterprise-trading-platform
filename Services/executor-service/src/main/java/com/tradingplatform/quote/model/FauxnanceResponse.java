package com.tradingplatform.quote.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FauxnanceResponse {
    private Data data;
    private Object meta;
    public Data getData() { return data; }
    public void setData(Data data) { this.data = data; }
    public Object getMeta() { return meta; }
    public void setMeta(Object meta) { this.meta = meta; }
}
