package com.tradingplatform.exception;
// Rule 1 : Account is not Found

import com.tradingplatform.enums.ErrorCode;

public final class AccountNotFoundException extends DomainException {

    private final Long accountId;

    public AccountNotFoundException(Long accountId) {
        super(ErrorCode.ACC_404, "Account not found");
        this.accountId = accountId;
    }
    
    public Long getAccountId() {
        return accountId;
    }
}
