package com.tradingplatform.preferences.internal.service;

import com.tradingplatform.preferences.AlertChannel;
import com.tradingplatform.preferences.AlertTarget;
import com.tradingplatform.preferences.internal.crypto.ContactDetailCipher;
import com.tradingplatform.preferences.internal.persistence.AdminPreferenceRecord;
import com.tradingplatform.preferences.internal.persistence.AdminPreferencesMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads and writes an admin's preferences, keyed by the admin's user id (the token's subject).
 * The same channel rules and encryption as customer preferences apply. Authorisation is the
 * caller's job: the admin routes take the user id from the verified token, never from the request.
 */
@Service
public class AdminPreferencesService {

    private final AdminPreferencesMapper mapper;
    private final ContactDetailCipher cipher;

    public AdminPreferencesService(AdminPreferencesMapper mapper, ContactDetailCipher cipher) {
        this.mapper = Objects.requireNonNull(mapper, "mapper cannot be null");
        this.cipher = Objects.requireNonNull(cipher, "cipher cannot be null");
    }

    /** The stored channels, or the platform default when none are stored. */
    public AdminPreferencesView get(String adminUserId) {
        List<AdminPreferenceRecord> rows = mapper.findByAdminUserId(adminUserId);
        if (rows.isEmpty()) {
            return AdminPreferencesView.platformDefault();
        }
        Instant updatedAt = rows.stream().map(AdminPreferenceRecord::getUpdatedAt)
                .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        return new AdminPreferencesView(decrypt(adminUserId, rows), true, updatedAt);
    }

    /** Full replacement of the admin's channels, in one transaction. */
    @Transactional
    public AdminPreferencesView replace(String adminUserId, List<AlertChannelEntry> requested) {
        List<AlertTarget> targets = ContactDetailRules.normalise(requested).stream()
                .sorted(Comparator.comparing(AlertTarget::channel)).toList();

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        mapper.deleteByAdminUserId(adminUserId);
        for (AlertTarget target : targets) {
            mapper.insert(new AdminPreferenceRecord(adminUserId, target.channel().name(),
                    cipher.encryptForAdmin(adminUserId, target.destination()), now));
        }
        return new AdminPreferencesView(targets, true, now);
    }

    /** Removes the stored channels; the admin resolves to the platform default again. */
    public void reset(String adminUserId) {
        mapper.deleteByAdminUserId(adminUserId);
    }

    /** The admin's stored channels for resolution, or empty when none are stored. */
    Optional<List<AlertTarget>> findStoredTargets(String adminUserId) {
        List<AdminPreferenceRecord> rows = mapper.findByAdminUserId(adminUserId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(decrypt(adminUserId, rows));
    }

    private List<AlertTarget> decrypt(String adminUserId, List<AdminPreferenceRecord> rows) {
        return rows.stream()
                .map(row -> new AlertTarget(AlertChannel.valueOf(row.getAlertChannel()),
                        cipher.decryptForAdmin(adminUserId, row.getContactDetailEnc())))
                .sorted(Comparator.comparing(AlertTarget::channel))
                .toList();
    }
}
