package com.tradingplatform.preferences.internal.web;

import com.tradingplatform.preferences.internal.service.AlertChannelEntry;

import java.util.List;

/**
 * Body of PUT /api/v1/accounts/{accountId}/preferences (contract: preferences-api.yaml).
 *
 * Deliberately carries no bean-validation annotations: every rule (one to three entries, each
 * channel once, a channel on every entry, contact detail fitting its channel) is enforced in one
 * place, ContactDetailRules, and answered with one code, PREF-422. Splitting the list rules into
 * annotations would answer some of them VAL-422 and the rest PREF-422.
 */
public record PreferencesRequest(List<AlertChannelEntry> alertChannels) {
}
