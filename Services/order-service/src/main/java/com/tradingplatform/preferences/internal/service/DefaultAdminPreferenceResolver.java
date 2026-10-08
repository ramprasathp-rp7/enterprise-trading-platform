package com.tradingplatform.preferences.internal.service;

import com.tradingplatform.preferences.AdminPreferenceResolver;
import com.tradingplatform.preferences.AlertTarget;
import com.tradingplatform.preferences.internal.crypto.ContactDetailCipher.ContactDetailCryptoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * The implementation behind {@link AdminPreferenceResolver}. Reads the store on every call and
 * answers in-app only when nothing is stored or the store cannot be read.
 */
@Component
public class DefaultAdminPreferenceResolver implements AdminPreferenceResolver {

    private static final Logger logger = LoggerFactory.getLogger(DefaultAdminPreferenceResolver.class);

    private final AdminPreferencesService preferences;

    public DefaultAdminPreferenceResolver(AdminPreferencesService preferences) {
        this.preferences = Objects.requireNonNull(preferences, "preferences cannot be null");
    }

    @Override
    public List<AlertTarget> resolveAdminAlertTargets(String adminUserId) {
        try {
            return preferences.findStoredTargets(adminUserId).orElseGet(() -> List.of(AlertTarget.inApp()));
        } catch (DataAccessException | ContactDetailCryptoException e) {
            logger.warn("Admin preference store unreadable ({}); notifying in-app only", e.getClass().getSimpleName());
            return List.of(AlertTarget.inApp());
        }
    }
}
