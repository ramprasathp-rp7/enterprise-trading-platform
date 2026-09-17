package com.tradingplatform.api.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Source {
    TRADE_API("trade-api"),
    TRADE_EXECUTOR("trade-executor"),
    MARKET_POLLER("market-poller");

    private final String value;
    Source(String value) { this.value = value; }
    @JsonValue public String getValue() { return value; }
    @JsonCreator public static Source fromValue(String value) {
        if (value == null) return null;
        for (Source s : values()) if (s.value.equals(value)) return s;
        throw new IllegalArgumentException("Unknown source: " + value);
    }
}
