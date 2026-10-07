package com.tradingplatform.preferences.internal.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts contact details at rest with AES-256-GCM.
 *
 * Each value gets a fresh 12-byte IV, stored in front of the ciphertext. The account id is bound
 * as additional authenticated data, so a ciphertext copied onto another account's row fails to
 * decrypt rather than leaking one customer's address to another's notifications.
 *
 * The key is PREFERENCES_CONTACT_KEY (base64 of 32 bytes). When it is unset a published dev key is
 * used and a warning is logged at start-up, the same convention as the JWT dev secret.
 */
@Component
public class ContactDetailCipher {

    private static final Logger logger = LoggerFactory.getLogger(ContactDetailCipher.class);

    /** Published in the source, so only fit for local development. Exactly 32 bytes. */
    static final byte[] DEV_KEY = "dev-only-preferences-contact-key".getBytes(StandardCharsets.US_ASCII);

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public ContactDetailCipher(@Value("${PREFERENCES_CONTACT_KEY:}") String base64Key) {
        byte[] keyBytes;
        if (base64Key == null || base64Key.isBlank()) {
            logger.warn("PREFERENCES_CONTACT_KEY is not set; using the published development key. "
                    + "Set it before storing real contact details.");
            keyBytes = DEV_KEY;
        } else {
            try {
                keyBytes = Base64.getDecoder().decode(base64Key.trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("PREFERENCES_CONTACT_KEY must be base64", e);
            }
        }
        if (keyBytes.length != 32) {
            throw new IllegalStateException("PREFERENCES_CONTACT_KEY must decode to 32 bytes (AES-256)");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    /** Returns iv || ciphertext+tag, or null for a null value. */
    public byte[] encrypt(long accountId, String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(accountId));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(IV_BYTES + ciphertext.length).put(iv).put(ciphertext).array();
        } catch (GeneralSecurityException e) {
            throw new ContactDetailCryptoException("Could not encrypt contact detail", e);
        }
    }

    /** Reverses {@link #encrypt}; null in, null out. */
    public String decrypt(long accountId, byte[] blob) {
        if (blob == null) {
            return null;
        }
        if (blob.length <= IV_BYTES) {
            throw new ContactDetailCryptoException("Stored contact detail is truncated", null);
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, blob, 0, IV_BYTES));
            cipher.updateAAD(aad(accountId));
            byte[] plaintext = cipher.doFinal(blob, IV_BYTES, blob.length - IV_BYTES);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new ContactDetailCryptoException("Could not decrypt contact detail", e);
        }
    }

    private static byte[] aad(long accountId) {
        return ("customer_preferences:" + accountId).getBytes(StandardCharsets.UTF_8);
    }

    /** Wrong key, tampered row, or a row moved between accounts. Never carries the value. */
    public static class ContactDetailCryptoException extends RuntimeException {
        public ContactDetailCryptoException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
