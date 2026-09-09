package com.tradingplatform.api.security;

final class InvalidTokenException extends RuntimeException {
    InvalidTokenException() {
        super("Invalid token");
    }
}

