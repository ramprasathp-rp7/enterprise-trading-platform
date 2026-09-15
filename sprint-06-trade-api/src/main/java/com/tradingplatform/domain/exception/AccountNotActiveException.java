package com.tradingplatform.domain.exception;

import com.tradingplatform.domain.enums.AccountStatus;

// Rule 2 : Account exists but is not ACTIVE

public final class AccountNotActiveException extends DomainException {

    private final Long accountId;
    private final AccountStatus actualStatus;

    public AccountNotActiveException(Long accountId, AccountStatus actualStatus) {
        super(com.tradingplatform.domain.enums.ErrorCode.ACC_403, "Account not active");
        this.accountId = accountId;
        this.actualStatus = actualStatus;
    }

    public Long getAccountId() {
        return accountId;
    }

    public AccountStatus getActualStatus() {
        return actualStatus;
    }
}
