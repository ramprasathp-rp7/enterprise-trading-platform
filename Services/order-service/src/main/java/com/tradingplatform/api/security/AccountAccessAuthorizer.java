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

        // All users must validate that token's accountId matches requested account
        // Even admins should not cross-read accounts without explicit authorization.
        Object tokenAccountId = jwt.getClaims().get("accountId");
        if (!(tokenAccountId instanceof Number number)) {
            // Either null or not a Number type - reject the token
            throw new AccountUnauthorizedException(requestedAccountId, null);
        }
        if (number.longValue() != requestedAccountId) {
            throw new AccountUnauthorizedException(requestedAccountId, number.longValue());
        }
    }
}

