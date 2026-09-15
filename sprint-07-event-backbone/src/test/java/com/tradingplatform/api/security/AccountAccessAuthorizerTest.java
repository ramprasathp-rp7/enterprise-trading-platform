package com.tradingplatform.api.security;

import com.tradingplatform.domain.exception.AccountUnauthorizedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountAccessAuthorizerTest {

    private final AccountAccessAuthorizer authorizer = new AccountAccessAuthorizer();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void allowsAccessWhenTokenMatchesRequestedAccount() {
        setJwtAuthentication(5L);

        authorizer.assertTokenCanAccess(5L);
    }

    @Test
    void returnsAccountForbiddenWhenTokenDoesNotReachAccount() {
        setJwtAuthentication(5L);

        AccountUnauthorizedException ex = assertThrows(
            AccountUnauthorizedException.class,
            () -> authorizer.assertTokenCanAccess(6L)
        );

        assertEquals("ACC-403", ex.getCode());
        assertEquals(6L, ex.getAccountId());
        assertEquals(5L, ex.getAuthenticatedAccountId());
    }

    private void setJwtAuthentication(long accountId) {
        Jwt jwt = new Jwt(
            "token-value",
            Instant.now(),
            Instant.now().plusSeconds(900),
            Map.of("alg", "HS256"),
            Map.of("sub", "u-1", "accountId", accountId, "roles", java.util.List.of("CUSTOMER"), "iss", "auth-service")
        );
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(jwt, null, java.util.List.of())
        );
    }
}

