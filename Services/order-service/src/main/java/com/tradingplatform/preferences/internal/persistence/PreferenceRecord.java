package com.tradingplatform.preferences.internal.persistence;

import java.time.Instant;

/**
 * One row of customer_preferences: one chosen channel of one account. The contact detail is held
 * only as ciphertext here; the service decrypts it on the way out.
 */
public class PreferenceRecord {

    private Long accountId;
    private String alertChannel;
    private byte[] contactDetailEnc;
    private Instant updatedAt;

    public PreferenceRecord() {
    }

    public PreferenceRecord(Long accountId, String alertChannel, byte[] contactDetailEnc, Instant updatedAt) {
        this.accountId = accountId;
        this.alertChannel = alertChannel;
        this.contactDetailEnc = contactDetailEnc;
        this.updatedAt = updatedAt;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
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
