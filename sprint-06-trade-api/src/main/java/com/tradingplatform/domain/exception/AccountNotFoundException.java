package com.tradingplatform.domain.exception;
// Rule 1 : Account is not Found

public final class AccountNotFoundException extends DomainException {

    private final Long accountId;

    public AccountNotFoundException(Long accountId) {
        super(com.tradingplatform.domain.enums.ErrorCode.ACC_404, "Account not found");
        this.accountId = accountId;
    }
    
    public Long getAccountId() {
        return accountId;
    }
}
