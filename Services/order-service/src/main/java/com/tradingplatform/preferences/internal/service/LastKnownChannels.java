package com.tradingplatform.preferences.internal.service;

import com.tradingplatform.preferences.ResolvedAlertChannels;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The last answer seen for each account, kept so resolution can still answer while the database
 * is unreachable. It is never read while the store answers: every resolution goes to the store
 * first, so a change the customer makes is used on the very next send.
 *
 * Refreshed on every successful resolve and write, and dropped on reset. In memory only: it
 * starts empty after a restart, which is the point at which the store is the only source.
 */
@Component
public class LastKnownChannels {

    private final Map<Long, ResolvedAlertChannels> byAccount = new ConcurrentHashMap<>();

    void remember(ResolvedAlertChannels resolved) {
        byAccount.put(resolved.accountId(), resolved);
    }

    void forget(long accountId) {
        byAccount.remove(accountId);
    }

    Optional<ResolvedAlertChannels> lastKnown(long accountId) {
        return Optional.ofNullable(byAccount.get(accountId));
    }
}
