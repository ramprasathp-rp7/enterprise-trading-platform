package com.tradingplatform.domain.exception;

public enum ErrorCode {
    AUTH_401("AUTH-401"),
    ACC_404("ACC-404"),
    ACC_403("ACC-403"),
    INS_404("INS-404"),
    ORD_400("ORD-400"),
    ORD_409("ORD-409"),
    VAL_422("VAL-422");

    private final String code;

    ErrorCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    @Override
    public String toString() {
        return code;
    }
}


