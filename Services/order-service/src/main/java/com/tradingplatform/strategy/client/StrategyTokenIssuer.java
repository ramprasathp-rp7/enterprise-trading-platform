package com.tradingplatform.strategy.client;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import com.tradingplatform.strategy.model.TradingStrategy;

/**
 * Identity a strategy uses to place orders while its owner is logged out.
 *
 * The customer authorised the strategy when they created or enabled it with their own token. At
 * run time this mints a token for one order call: signed like auth-service tokens (shared HS256
 * secret and issuer, so the API's JwtDecoder accepts it), carrying the owner's accountId (so
 * AccountAccessAuthorizer allows exactly that account), role STRATEGY and the strategy id, and
 * expiring after a minute. It is never stored or reused.
 */
@Component
public class StrategyTokenIssuer {

    static final String ROLE = "STRATEGY";

    private final JwtEncoder encoder;
    private final String issuer;
    private final Duration ttl;
    private final Clock clock;

    @Autowired
    public StrategyTokenIssuer(@Value("${JWT_SECRET}") String secret,
                               @Value("${JWT_ISSUER}") String issuer,
                               @Value("${trading.strategy.token-ttl-seconds:60}") long ttlSeconds) {
        this(secret, issuer, Duration.ofSeconds(ttlSeconds), Clock.systemUTC());
    }

    StrategyTokenIssuer(String secret, String issuer, Duration ttl, Clock clock) {
        SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(key));
        this.issuer = issuer;
        this.ttl = ttl;
        this.clock = clock;
    }

    /** A short-lived bearer token that can place orders for the strategy's account only */
    public String issue(TradingStrategy strategy) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(issuer)
            .subject("strategy:" + strategy.getStrategyId())
            .issuedAt(now)
            .expiresAt(now.plus(ttl))
            .claim("accountId", strategy.getAccountId())
            .claim("strategyId", strategy.getStrategyId())
            .claim("roles", List.of(ROLE))
            .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
