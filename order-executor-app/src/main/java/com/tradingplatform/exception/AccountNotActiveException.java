package com.tradingplatform.domain.exception;

import com.tradingplatform.enums.AccountStatus;
import com.tradingplatform.enums.ErrorCode;

// Rule 2 : Account exists but is not ACTIVE

public final class AccountNotActiveException extends DomainException {

    private final Long accountId;
    private final AccountStatus actualStatus;

    public AccountNotActiveException(Long accountId, AccountStatus actualStatus) {
        super(ErrorCode.ACC_403, "Account not active");
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
