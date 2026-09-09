package com.tradingplatform.domain.rules;

import com.tradingplatform.domain.exception.DuplicateOrderException;

public interface IdempotencyKeyChecker {

    // Claims the key or throws — the insert is the check, so a UNIQUE violation (23505) is the seam.
    void claim(String idempotencyKey) throws DuplicateOrderException;
}
