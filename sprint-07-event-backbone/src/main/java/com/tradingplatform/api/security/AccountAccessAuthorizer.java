package com.tradingplatform.api.security;

import com.tradingplatform.domain.exception.AccountUnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class AccountAccessAuthorizer {

    public void assertTokenCanAccess(long requestedAccountId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AccountUnauthorizedException(requestedAccountId, null);
        }

        Object tokenAccountId = jwt.getClaims().get("accountId");
        if (!(tokenAccountId instanceof Number number) || number.longValue() != requestedAccountId) {
            long authenticatedAccountId = ((Number) tokenAccountId).longValue();
            throw new AccountUnauthorizedException(requestedAccountId, authenticatedAccountId);
        }
    }
}

