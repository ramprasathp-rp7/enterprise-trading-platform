package com.tradingplatform.api.security;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * JWT Authentication Converter
 * 
 * Converts JWT claims to Spring Security authorities.
 * Maps the `roles` claim (array of strings) to GrantedAuthority objects.
 * 
 * Example JWT claim:
 *   "roles": ["CUSTOMER"]
 * 
 * Converted to:
 *   GrantedAuthority: "ROLE_CUSTOMER"
 * 
 * This allows Spring Security to use .hasAuthority("ROLE_ADMIN") checks
 * on JWT tokens from the auth service.
 */
@Component
public class JwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String ROLES_CLAIM = "roles";
    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    public AbstractAuthenticationToken convert(@NonNull Jwt jwt) {
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
        return new UsernamePasswordAuthenticationToken(jwt, "N/A", authorities);
    }

    /**
     * Extract roles from JWT "roles" claim and convert to GrantedAuthority objects.
     * 
     * @param jwt the JWT token
     * @return collection of authorities with "ROLE_" prefix
     */
    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Object rolesObj = jwt.getClaims().get(ROLES_CLAIM);
        
        if (rolesObj == null) {
            return List.of();
        }

        if (rolesObj instanceof Collection<?> rolesList) {
            return rolesList.stream()
                .filter(role -> role instanceof String)
                .map(role -> (String) role)
                .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role))
                .collect(Collectors.toList());
        }

        return List.of();
    }
}
