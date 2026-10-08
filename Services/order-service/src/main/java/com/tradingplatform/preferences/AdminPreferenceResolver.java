package com.tradingplatform.preferences;

import java.util.List;

/**
 * The one way another module learns how to reach an admin. The admin counterpart of
 * {@link PreferenceResolver}, and like it an in-process interface with no HTTP route.
 *
 * Callers pass the admin user id taken from a verified admin token (the JWT sub claim), never one
 * supplied in a request body.
 */
public interface AdminPreferenceResolver {

    /**
     * Resolve every channel to notify the admin on; deliver to each target.
     *
     * @param adminUserId the admin's user id (auth.users.id, the JWT sub claim)
     * @return never null and never empty. A single {@code IN_APP} target when nothing is stored,
     *         or when the store cannot be read (admin notifications are not queued for retry, so
     *         the in-app bell, which needs no stored data, is the safe answer)
     */
    List<AlertTarget> resolveAdminAlertTargets(String adminUserId);
}
