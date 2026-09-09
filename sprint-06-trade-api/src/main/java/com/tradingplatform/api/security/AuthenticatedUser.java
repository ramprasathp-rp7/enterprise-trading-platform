package com.tradingplatform.api.security;

import java.util.List;

public record AuthenticatedUser(String userId, long accountId, List<String> roles) {
    public static final String REQUEST_ATTRIBUTE = "authenticatedUser";
}

