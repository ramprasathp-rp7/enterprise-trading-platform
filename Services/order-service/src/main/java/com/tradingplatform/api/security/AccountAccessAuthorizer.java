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

        // Admin users can access any account - check for admin role first
        if (isAdminUser(jwt)) {
            return; // Allow admin access without further checks
        }

        // For non-admin users, validate that token's accountId matches requested account
        Object tokenAccountId = jwt.getClaims().get("accountId");
        if (!(tokenAccountId instanceof Number number)) {
            // Either null or not a Number type - reject the token
            throw new AccountUnauthorizedException(requestedAccountId, null);
        }
        if (number.longValue() != requestedAccountId) {
            throw new AccountUnauthorizedException(requestedAccountId, number.longValue());
        }
    }

    /**
     * Check if the JWT token belongs to an admin user.
     * Supports both "role" and "roles" claims for flexibility.
     */
    private boolean isAdminUser(Jwt jwt) {
        // Check for "role" claim (single role)
        Object role = jwt.getClaims().get("role");
        if (role instanceof String roleStr) {
            return "ADMIN".equalsIgnoreCase(roleStr) || "SUPER_ADMIN".equalsIgnoreCase(roleStr);
        }

        // Check for "roles" claim (array of roles)
        Object roles = jwt.getClaims().get("roles");
        if (roles instanceof java.util.List<?> rolesList) {
            return rolesList.stream()
                .anyMatch(r -> {
                    if (r instanceof String roleStr) {
                        return "ADMIN".equalsIgnoreCase(roleStr) || "SUPER_ADMIN".equalsIgnoreCase(roleStr);
                    }
                    return false;
                });
        }

        return false;
    }
}

