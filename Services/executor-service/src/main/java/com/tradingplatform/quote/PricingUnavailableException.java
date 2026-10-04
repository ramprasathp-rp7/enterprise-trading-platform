package com.tradingplatform.quote;

public class PricingUnavailableException extends Exception {
    public PricingUnavailableException(String message) { super(message); }
    public PricingUnavailableException(String message, Throwable cause) { super(message, cause); }
}
