package com.tradingplatform.preferences;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The answer to "how do I reach this customer?", as returned by {@link PreferenceResolver}.
 * A customer may choose several channels, so there may be several targets; deliver to each.
 *
 * @param accountId the account the answer is for
 * @param targets   one or more targets, at most one per channel, in {@link AlertChannel} order
 * @param source    whether the customer chose these or the platform default applied
 * @param stale     true when the store could not be read and the last answer seen for this
 *                  account is being served instead
 */
public record ResolvedAlertChannels(
        long accountId,
        List<AlertTarget> targets,
        Source source,
        boolean stale) {

    public enum Source {
        /** The customer stored these channels. */
        CUSTOMER,
        /** Nothing is stored for the account; the documented platform default applied. */
        PLATFORM_DEFAULT
    }

    public ResolvedAlertChannels {
        Objects.requireNonNull(targets, "targets cannot be null");
        Objects.requireNonNull(source, "source cannot be null");
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("at least one target is required");
        }
        Set<AlertChannel> seen = EnumSet.noneOf(AlertChannel.class);
        for (AlertTarget target : targets) {
            if (!seen.add(Objects.requireNonNull(target, "target cannot be null").channel())) {
                throw new IllegalArgumentException("at most one target per channel");
            }
        }
        targets = targets.stream().sorted(Comparator.comparing(AlertTarget::channel)).toList();
    }

    /** The platform default: deliver to the in-app inbox, which needs no personal data. */
    public static ResolvedAlertChannels platformDefault(long accountId) {
        return new ResolvedAlertChannels(accountId, List.of(AlertTarget.inApp()), Source.PLATFORM_DEFAULT, false);
    }

    /** The same answer, marked as served from the last known value rather than the store. */
    public ResolvedAlertChannels asStale() {
        return new ResolvedAlertChannels(accountId, targets, source, true);
    }

    /** The channels only, for recording on a notification without the personal data. */
    public List<AlertChannel> channels() {
        return targets.stream().map(AlertTarget::channel).toList();
    }
}
