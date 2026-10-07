package com.tradingplatform.preferences;

/**
 * The one way another module learns how to reach a customer.
 *
 * This is an in-process interface and deliberately has no HTTP route: a resolution route
 * reachable with a customer token would hand any customer another customer's contact details.
 * Callers act on an accountId taken from a trusted platform event (a {@code trade-events}
 * message), not from a customer request, so no token is checked here. Do not call this with an
 * accountId a customer supplied.
 *
 * Called by: the notifications module, on every send. Do not cache the answer on the caller's
 * side; this module already serves the last known answer when its store is down.
 */
public interface PreferenceResolver {

    /**
     * Resolve every channel to notify the account on. A customer may choose more than one;
     * deliver to each target, and record and de-duplicate per (eventId, channel).
     *
     * @param accountId the account (ACCOUNTS.id) the notification is for
     * @return never null and never empty. When nothing is stored,
     *         {@link ResolvedAlertChannels#platformDefault} (a single {@code IN_APP} target, source
     *         {@code PLATFORM_DEFAULT}). When the store cannot be read but an answer was seen
     *         earlier, that answer with {@code stale = true}
     * @throws PreferencesUnavailableException only when the store cannot be read and no earlier
     *         answer exists. Hold the message and retry; do not drop it
     */
    ResolvedAlertChannels resolveAlertChannels(long accountId);
}
