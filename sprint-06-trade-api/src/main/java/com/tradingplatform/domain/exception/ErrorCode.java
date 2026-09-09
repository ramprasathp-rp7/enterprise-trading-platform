package com.tradingplatform.domain.exception;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ErrorCode {
    ACC_404("ACC-404"),
    ACC_403("ACC-403"),
    INS_404("INS-404"),
    ORD_400("ORD-400"),
    ORD_409("ORD-409"),
    VAL_422("VAL-422"),
    AUTH_401("AUTH-401");

    private final String code;

    ErrorCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    @JsonValue
    public String jsonValue() {
        return code;
    }

    @JsonCreator
    public static ErrorCode fromCode(String code) {
        for (ErrorCode value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown error code: " + code);
    }

    @Override
    public String toString() {
        return code;
    }
}


