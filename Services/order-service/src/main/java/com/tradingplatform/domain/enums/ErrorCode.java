package com.tradingplatform.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ErrorCode {
    AUTH_401("AUTH-401"),
    ACC_404("ACC-404"),
    ACC_403("ACC-403"),
    INS_404("INS-404"),
    ORD_400("ORD-400"),
    ORD_409("ORD-409"),
    VAL_422("VAL-422"),
    INVALID_PREFERENCE("PREF-422"),
    TRANS_404("TRANS-404"),
    TRANS_400("TRANS-400"),
    WL_404("WL-404"),
    WL_409("WL-409"),
    CDL_404("CDL-404"),
    ALR_404("ALR-404"),
    ALR_409("ALR-409"),
    STR_404("STR-404"),
    STR_409("STR-409"),
    MKT_404("MKT-404"),
    MKT_503("MKT-503"),
    ASS_404("ASS-404");

    private final String code;

    ErrorCode(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

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


