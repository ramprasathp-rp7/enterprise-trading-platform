package com.tradingplatform.preferences.internal.persistence;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory admin_preferences, with a switch to simulate the database being unreachable. */
public class FakeAdminPreferencesMapper implements AdminPreferencesMapper {

    /** Keyed on "adminUserId/channel", the table's primary key. */
    private final Map<String, AdminPreferenceRecord> rows = new LinkedHashMap<>();
    private boolean unavailable;

    public void setUnavailable(boolean unavailable) {
        this.unavailable = unavailable;
    }

    /** The stored row itself (not a copy), so a test can move or tamper with it. */
    public AdminPreferenceRecord rawRow(String adminUserId, String channel) {
        return rows.get(adminUserId + "/" + channel);
    }

    public List<AdminPreferenceRecord> rawRows(String adminUserId) {
        return rows.values().stream().filter(r -> r.getAdminUserId().equals(adminUserId)).toList();
    }

    @Override
    public List<AdminPreferenceRecord> findByAdminUserId(String adminUserId) {
        touch();
        List<AdminPreferenceRecord> found = new ArrayList<>();
        for (AdminPreferenceRecord row : rows.values()) {
            if (row.getAdminUserId().equals(adminUserId)) {
                found.add(copy(row));
            }
        }
        return found;
    }

    @Override
    public int insert(AdminPreferenceRecord preference) {
        touch();
        String key = preference.getAdminUserId() + "/" + preference.getAlertChannel();
        if (rows.containsKey(key)) {
            throw new DuplicateKeyException("duplicate " + key);
        }
        rows.put(key, copy(preference));
        return 1;
    }

    @Override
    public int deleteByAdminUserId(String adminUserId) {
        touch();
        int before = rows.size();
        rows.values().removeIf(r -> r.getAdminUserId().equals(adminUserId));
        return before - rows.size();
    }

    private void touch() {
        if (unavailable) {
            throw new DataAccessResourceFailureException("simulated: database unreachable");
        }
    }

    private static AdminPreferenceRecord copy(AdminPreferenceRecord r) {
        return new AdminPreferenceRecord(r.getAdminUserId(), r.getAlertChannel(),
                r.getContactDetailEnc() == null ? null : r.getContactDetailEnc().clone(), r.getUpdatedAt());
    }
}
