package com.tradingplatform.preferences.internal.service;

import com.tradingplatform.preferences.PreferenceResolver;
import com.tradingplatform.preferences.PreferencesUnavailableException;
import com.tradingplatform.preferences.ResolvedAlertChannels;
import com.tradingplatform.preferences.internal.crypto.ContactDetailCipher.ContactDetailCryptoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

/**
 * The implementation behind {@link PreferenceResolver}. Reads the store on every call; falls back
 * to the last known answer only when the store cannot be read; raises only when there is neither.
 */
@Component
public class DefaultPreferenceResolver implements PreferenceResolver {

    private static final Logger logger = LoggerFactory.getLogger(DefaultPreferenceResolver.class);

    private final PreferencesService preferences;
    private final LastKnownChannels lastKnown;

    public DefaultPreferenceResolver(PreferencesService preferences, LastKnownChannels lastKnown) {
        this.preferences = Objects.requireNonNull(preferences, "preferences cannot be null");
        this.lastKnown = Objects.requireNonNull(lastKnown, "lastKnown cannot be null");
    }

    @Override
    public ResolvedAlertChannels resolveAlertChannels(long accountId) {
        ResolvedAlertChannels resolved;
        try {
            resolved = preferences.findStoredChannels(accountId)
                    .orElseGet(() -> ResolvedAlertChannels.platformDefault(accountId));
        } catch (DataAccessException | ContactDetailCryptoException e) {
            Optional<ResolvedAlertChannels> fallback = lastKnown.lastKnown(accountId);
            logger.warn("Preference store unreadable for accountId={} ({}); last known answer {}",
                    accountId, e.getClass().getSimpleName(), fallback.isPresent() ? "served" : "absent");
            return fallback.map(ResolvedAlertChannels::asStale)
                    .orElseThrow(() -> new PreferencesUnavailableException(accountId, e));
        }
        lastKnown.remember(resolved);
        return resolved;
    }
}
