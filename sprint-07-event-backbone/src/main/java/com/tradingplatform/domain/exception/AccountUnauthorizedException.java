package com.tradingplatform.domain.exception;

import com.tradingplatform.domain.enums.ErrorCode;

/**
 * Thrown when an authenticated user attempts to access an account they are not authorized to access.
 * Maps to ACC-403 (Forbidden).
 */
public final class AccountUnauthorizedException extends DomainException {

    private final Long accountId;
    private final Long authenticatedAccountId;

    public AccountUnauthorizedException(Long accountId, Long authenticatedAccountId) {
        super(ErrorCode.ACC_403, "Access to this account is forbidden");
        this.accountId = accountId;
        this.authenticatedAccountId = authenticatedAccountId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public Long getAuthenticatedAccountId() {
        return authenticatedAccountId;
    }
}
