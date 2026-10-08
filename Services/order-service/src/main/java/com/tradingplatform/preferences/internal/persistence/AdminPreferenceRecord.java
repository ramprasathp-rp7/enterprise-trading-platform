package com.tradingplatform.preferences.internal.persistence;

import java.time.Instant;

/**
 * One row of admin_preferences: one chosen channel of one admin user. The contact detail is held
 * only as ciphertext here; the service decrypts it on the way out.
 */
public class AdminPreferenceRecord {

    private String adminUserId;
    private String alertChannel;
    private byte[] contactDetailEnc;
    private Instant updatedAt;

    public AdminPreferenceRecord() {
    }

    public AdminPreferenceRecord(String adminUserId, String alertChannel, byte[] contactDetailEnc, Instant updatedAt) {
        this.adminUserId = adminUserId;
        this.alertChannel = alertChannel;
        this.contactDetailEnc = contactDetailEnc;
        this.updatedAt = updatedAt;
    }

    public String getAdminUserId() {
        return adminUserId;
    }

    public void setAdminUserId(String adminUserId) {
        this.adminUserId = adminUserId;
    }

    public String getAlertChannel() {
        return alertChannel;
    }

    public void setAlertChannel(String alertChannel) {
        this.alertChannel = alertChannel;
    }

    public byte[] getContactDetailEnc() {
        return contactDetailEnc;
    }

    public void setContactDetailEnc(byte[] contactDetailEnc) {
        this.contactDetailEnc = contactDetailEnc;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
