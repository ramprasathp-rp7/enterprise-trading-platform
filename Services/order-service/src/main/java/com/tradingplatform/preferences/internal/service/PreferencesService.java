package com.tradingplatform.preferences.internal.service;

import com.tradingplatform.api.services.AccountService;
import com.tradingplatform.preferences.AlertChannel;
import com.tradingplatform.preferences.AlertTarget;
import com.tradingplatform.preferences.ResolvedAlertChannels;
import com.tradingplatform.preferences.internal.crypto.ContactDetailCipher;
import com.tradingplatform.preferences.internal.persistence.PreferenceRecord;
import com.tradingplatform.preferences.internal.persistence.PreferencesMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads and writes a customer's preferences. Authorisation is the caller's job: the controller
 * checks the token's accountId claim before calling in, and the resolver is only reached from
 * trusted platform events.
 *
 * Account existence is checked through AccountService, the layer that owns accounts, rather than
 * by querying the accounts table from here.
 */
@Service
public class PreferencesService {

    private final PreferencesMapper mapper;
    private final ContactDetailCipher cipher;
    private final AccountService accountService;
    private final LastKnownChannels lastKnown;

    public PreferencesService(PreferencesMapper mapper, ContactDetailCipher cipher,
                              AccountService accountService, LastKnownChannels lastKnown) {
        this.mapper = Objects.requireNonNull(mapper, "mapper cannot be null");
        this.cipher = Objects.requireNonNull(cipher, "cipher cannot be null");
        this.accountService = Objects.requireNonNull(accountService, "accountService cannot be null");
        this.lastKnown = Objects.requireNonNull(lastKnown, "lastKnown cannot be null");
    }

    /** The stored channels, or the platform default when none are stored. */
    public PreferencesView get(long accountId) {
        accountService.getAccount(accountId);
        List<PreferenceRecord> rows = mapper.findByAccountId(accountId);
        if (rows.isEmpty()) {
            return PreferencesView.platformDefault(accountId);
        }
        Instant updatedAt = rows.stream().map(PreferenceRecord::getUpdatedAt)
                .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        return new PreferencesView(accountId, decrypt(accountId, rows), true, updatedAt);
    }

    /**
     * Full replacement of the account's channels. The delete and the inserts run in one
     * transaction, so a failure part-way leaves the previous choice in place rather than none.
     */
    @Transactional
    public PreferencesView replace(long accountId, List<AlertChannelEntry> requested) {
        accountService.getAccount(accountId);
        List<AlertTarget> targets = ContactDetailRules.normalise(requested).stream()
                .sorted(Comparator.comparing(AlertTarget::channel)).toList();

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        mapper.deleteByAccountId(accountId);
        for (AlertTarget target : targets) {
            mapper.insert(new PreferenceRecord(accountId, target.channel().name(),
                    cipher.encrypt(accountId, target.destination()), now));
        }

        lastKnown.remember(new ResolvedAlertChannels(accountId, targets, ResolvedAlertChannels.Source.CUSTOMER, false));
        return new PreferencesView(accountId, targets, true, now);
    }

    /** Removes the stored channels; the account resolves to the platform default again. */
    public void reset(long accountId) {
        accountService.getAccount(accountId);
        mapper.deleteByAccountId(accountId);
        lastKnown.forget(accountId);
    }

    /**
     * The customer's stored channels for resolution, without the account existence check: the
     * resolver answers the platform default for any account with nothing stored.
     */
    Optional<ResolvedAlertChannels> findStoredChannels(long accountId) {
        List<PreferenceRecord> rows = mapper.findByAccountId(accountId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ResolvedAlertChannels(
                accountId, decrypt(accountId, rows), ResolvedAlertChannels.Source.CUSTOMER, false));
    }

    private List<AlertTarget> decrypt(long accountId, List<PreferenceRecord> rows) {
        return rows.stream()
                .map(row -> new AlertTarget(AlertChannel.valueOf(row.getAlertChannel()),
                        cipher.decrypt(accountId, row.getContactDetailEnc())))
                .sorted(Comparator.comparing(AlertTarget::channel))
                .toList();
    }
}
