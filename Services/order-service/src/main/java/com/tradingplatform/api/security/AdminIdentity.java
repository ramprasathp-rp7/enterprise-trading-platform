package com.tradingplatform.api.security;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Who the signed-in admin is: the verified token's subject (auth.users.id).
 *
 * Admins have no trading account of their own, so admin-scoped data (preferences, notifications)
 * is keyed on this id. ROLE_ADMIN itself is enforced by the security chain on /api/v1/admin/**.
 */
@Component
public class AdminIdentity {

    /** Maximum length of the stored id (admin_preferences.admin_user_id). */
    static final int MAX_ID_LENGTH = 64;

    /**
     * @return the token's sub claim
     * @throws BadCredentialsException (AUTH-401) when there is no verified token or it has no usable subject
     */
    public String currentAdminUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new BadCredentialsException("No verified token");
        }
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank() || subject.length() > MAX_ID_LENGTH) {
            throw new BadCredentialsException("Token has no usable subject");
        }
        return subject;
    }
}
