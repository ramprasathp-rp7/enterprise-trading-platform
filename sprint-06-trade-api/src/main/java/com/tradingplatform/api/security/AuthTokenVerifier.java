package com.tradingplatform.api.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AuthTokenVerifier {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final String EXPECTED_ALGORITHM = "HS256";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final String issuer;
    private final Clock clock;

    public AuthTokenVerifier(String secret, String issuer) {
        this(secret, issuer, Clock.systemUTC());
    }

    AuthTokenVerifier(String secret, String issuer, Clock clock) {
        this.objectMapper = new ObjectMapper();
        this.secret = Objects.requireNonNull(secret, "secret").getBytes(StandardCharsets.UTF_8);
        this.issuer = Objects.requireNonNull(issuer, "issuer");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public AuthenticatedUser verify(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new InvalidTokenException();
        }

        Map<String, Object> header = parseJson(parts[0]);
        Map<String, Object> claims = parseJson(parts[1]);

        // Always validate the signature before trusting any payload value.
        verifySignature(parts[0], parts[1], parts[2]);
        verifyExpiry(claims);
        verifyAlgorithm(header);
        verifyIssuer(claims);

        return new AuthenticatedUser(
            getStringClaim(claims, "sub"),
            getLongClaim(claims, "accountId"),
            getRolesClaim(claims)
        );
    }

    private Map<String, Object> parseJson(String part) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(part);
            return objectMapper.readValue(decoded, MAP_TYPE);
        } catch (Exception ex) {
            throw new InvalidTokenException();
        }
    }

    private void verifySignature(String header, String payload, String signaturePart) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));

            String content = header + "." + payload;
            byte[] expected = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            byte[] provided = Base64.getUrlDecoder().decode(signaturePart);

            if (!java.security.MessageDigest.isEqual(expected, provided)) {
                throw new InvalidTokenException();
            }
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new InvalidTokenException();
        }
    }

    private void verifyExpiry(Map<String, Object> claims) {
        long exp = getLongClaim(claims, "exp");
        long now = clock.instant().getEpochSecond();
        if (now >= exp) {
            throw new InvalidTokenException();
        }
    }

    private void verifyAlgorithm(Map<String, Object> header) {
        String alg = getStringClaim(header, "alg");
        if (!EXPECTED_ALGORITHM.equals(alg)) {
            throw new InvalidTokenException();
        }
    }

    private void verifyIssuer(Map<String, Object> claims) {
        String tokenIssuer = getStringClaim(claims, "iss");
        if (!issuer.equals(tokenIssuer)) {
            throw new InvalidTokenException();
        }
    }

    private long getLongClaim(Map<String, Object> claims, String claimName) {
        Object value = claims.get(claimName);
        if (value instanceof Integer integer) {
            return integer.longValue();
        }
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value instanceof Double doubleValue) {
            return doubleValue.longValue();
        }
        throw new InvalidTokenException();
    }

    private String getStringClaim(Map<String, Object> claims, String claimName) {
        Object value = claims.get(claimName);
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return stringValue;
        }
        throw new InvalidTokenException();
    }

    @SuppressWarnings("unchecked")
    private List<String> getRolesClaim(Map<String, Object> claims) {
        Object value = claims.get("roles");
        if (!(value instanceof List<?> roles) || roles.isEmpty()) {
            throw new InvalidTokenException();
        }
        if (roles.stream().anyMatch(role -> !(role instanceof String))) {
            throw new InvalidTokenException();
        }
        return (List<String>) roles;
    }
}

