package com.tradingplatform.strategy.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import com.tradingplatform.api.security.ApiSecurityConfiguration;
import com.tradingplatform.strategy.model.TradingStrategy;

class StrategyTokenIssuerTest {

    private static final String SECRET = "test-shared-secret-32-bytes-minimum-key";

    private final JwtDecoder decoder = new ApiSecurityConfiguration().jwtDecoder(SECRET, "auth-service");

    private static TradingStrategy strategy() {
        TradingStrategy s = new TradingStrategy();
        s.setStrategyId(7L);
        s.setAccountId(101L);
        return s;
    }

    @Test
    void mintsATokenTheApiAcceptsForTheOwnersAccountOnly() {
        String token = new StrategyTokenIssuer(SECRET, "auth-service", Duration.ofSeconds(60), Clock.systemUTC()).issue(strategy());

        Jwt jwt = decoder.decode(token);

        assertEquals(101L, ((Number) jwt.getClaims().get("accountId")).longValue());
        assertEquals(7L, ((Number) jwt.getClaims().get("strategyId")).longValue());
        assertEquals(List.of("STRATEGY"), jwt.getClaimAsStringList("roles"));
        assertEquals("strategy:7", jwt.getSubject());
        assertEquals(Duration.ofSeconds(60), Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()));
    }

    @Test
    void expiresQuickly() {
        Clock past = Clock.fixed(Instant.now().minus(Duration.ofMinutes(10)), ZoneOffset.UTC);
        String token = new StrategyTokenIssuer(SECRET, "auth-service", Duration.ofSeconds(60), past).issue(strategy());

        assertThrows(JwtException.class, () -> decoder.decode(token));
    }

    @Test
    void isRejectedWhenSignedWithAnotherSecret() {
        String token = new StrategyTokenIssuer("another-secret-of-at-least-32-bytes!!", "auth-service",
            Duration.ofSeconds(60), Clock.systemUTC()).issue(strategy());

        assertThrows(JwtException.class, () -> decoder.decode(token));
    }
}
