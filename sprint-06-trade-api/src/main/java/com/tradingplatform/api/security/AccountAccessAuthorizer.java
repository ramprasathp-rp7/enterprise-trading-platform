package com.tradingplatform.api.security;

import com.tradingplatform.domain.enums.AccountStatus;
import com.tradingplatform.domain.exception.AccountNotActiveException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class AccountAccessAuthorizer {

    public void assertTokenCanAccess(long requestedAccountId, HttpServletRequest request) {
        Object principal = request.getAttribute(AuthenticatedUser.REQUEST_ATTRIBUTE);
        if (!(principal instanceof AuthenticatedUser authenticatedUser)) {
            throw new AccountNotActiveException(requestedAccountId, AccountStatus.SUSPENDED);
        }

        if (authenticatedUser.accountId() != requestedAccountId) {
            throw new AccountNotActiveException(requestedAccountId, AccountStatus.SUSPENDED);
        }
    }
}

