package com.tradingplatform.preferences.internal.persistence;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory customer_preferences, with a switch to simulate the database being unreachable. */
public class FakePreferencesMapper implements PreferencesMapper {

    /** Keyed on "accountId/channel", the table's primary key. */
    private final Map<String, PreferenceRecord> rows = new LinkedHashMap<>();
    private boolean unavailable;
    private int calls;

    public void setUnavailable(boolean unavailable) {
        this.unavailable = unavailable;
    }

    /** Number of calls made against the table, to prove a refused request never reached it. */
    public int calls() {
        return calls;
    }

    /** The stored row itself (not a copy), so a test can tamper with it. */
    public PreferenceRecord rawRow(long accountId, String channel) {
        return rows.get(key(accountId, channel));
    }

    public List<PreferenceRecord> rawRows(long accountId) {
        return rows.values().stream().filter(r -> r.getAccountId() == accountId).toList();
    }

    @Override
    public List<PreferenceRecord> findByAccountId(long accountId) {
        touch();
        List<PreferenceRecord> found = new ArrayList<>();
        for (PreferenceRecord row : rows.values()) {
            if (row.getAccountId() == accountId) {
                found.add(copy(row));
            }
        }
        return found;
    }

    @Override
    public int insert(PreferenceRecord preference) {
        touch();
        String key = key(preference.getAccountId(), preference.getAlertChannel());
        if (rows.containsKey(key)) {
            throw new DuplicateKeyException("duplicate " + key);
        }
        rows.put(key, copy(preference));
        return 1;
    }

    @Override
    public int deleteByAccountId(long accountId) {
        touch();
        int before = rows.size();
        rows.values().removeIf(r -> r.getAccountId() == accountId);
        return before - rows.size();
    }

    private void touch() {
        calls++;
        if (unavailable) {
            throw new DataAccessResourceFailureException("simulated: database unreachable");
        }
    }

    private static String key(long accountId, String channel) {
        return accountId + "/" + channel;
    }

    private static PreferenceRecord copy(PreferenceRecord r) {
        return new PreferenceRecord(r.getAccountId(), r.getAlertChannel(),
                r.getContactDetailEnc() == null ? null : r.getContactDetailEnc().clone(), r.getUpdatedAt());
    }
}
