package com.tradingplatform.domain.rules;
public interface IdempotencyKeyChecker {

    boolean isAlreadyUsed(String idempotencyKey);
}
