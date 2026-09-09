package com.tradingplatform.api.security;

import com.tradingplatform.domain.exception.AccountNotActiveException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountAccessAuthorizerTest {

    private final AccountAccessAuthorizer authorizer = new AccountAccessAuthorizer();

    @Test
    void allowsAccessWhenTokenMatchesRequestedAccount() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AuthenticatedUser.REQUEST_ATTRIBUTE, new AuthenticatedUser("u-1", 5L, List.of("CUSTOMER")));

        authorizer.assertTokenCanAccess(5L, request);
    }

    @Test
    void returnsAccountForbiddenWhenTokenDoesNotReachAccount() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AuthenticatedUser.REQUEST_ATTRIBUTE, new AuthenticatedUser("u-1", 5L, List.of("CUSTOMER")));

        AccountNotActiveException ex = assertThrows(
            AccountNotActiveException.class,
            () -> authorizer.assertTokenCanAccess(6L, request)
        );

        assertEquals("ACC-403", ex.getCode());
        assertEquals("Account not active", ex.getMessage());
    }
}

