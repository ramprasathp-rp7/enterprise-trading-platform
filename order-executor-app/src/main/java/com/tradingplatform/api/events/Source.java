package com.tradingplatform.api.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Source enumeration identifying the component that produced the event.
 * Names the logical component, not the container it shipped in.
 */
public enum Source {
    TRADE_API("trade-api"),
    TRADE_EXECUTOR("trade-executor"),
    MARKET_POLLER("market-poller");

    private final String value;

    Source(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static Source fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (Source source : Source.values()) {
            if (source.value.equals(value)) {
                return source;
            }
        }
        throw new IllegalArgumentException("Unknown source value: " + value);
    }
}
