package com.tradingplatform.preferences.internal.crypto;

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
 * Each value gets a fresh 12-byte IV, stored in front of the ciphertext. The account id (or, for an
 * admin, the admin's user id) is bound as additional authenticated data, so a ciphertext copied
 * onto another row fails to decrypt rather than leaking one person's address to another's
 * notifications.
 *
 * The key is PREFERENCES_CONTACT_KEY (base64 of 32 bytes), from .env; the service does not start
 * without it.
 */
@Component
public class ContactDetailCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public ContactDetailCipher(@Value("${PREFERENCES_CONTACT_KEY:}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(
                "PREFERENCES_CONTACT_KEY is required: base64 of 32 random bytes (openssl rand -base64 32)");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("PREFERENCES_CONTACT_KEY must be base64", e);
        }
        if (keyBytes.length != 32) {
            throw new IllegalStateException("PREFERENCES_CONTACT_KEY must decode to 32 bytes (AES-256)");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    /** Returns iv || ciphertext+tag, or null for a null value. */
    public byte[] encrypt(long accountId, String plaintext) {
        return encrypt(aad(accountId), plaintext);
    }

    /** Reverses {@link #encrypt(long, String)}; null in, null out. */
    public String decrypt(long accountId, byte[] blob) {
        return decrypt(aad(accountId), blob);
    }

    /** As {@link #encrypt(long, String)}, for an admin's row in admin_preferences. */
    public byte[] encryptForAdmin(String adminUserId, String plaintext) {
        return encrypt(adminAad(adminUserId), plaintext);
    }

    /** Reverses {@link #encryptForAdmin}; null in, null out. */
    public String decryptForAdmin(String adminUserId, byte[] blob) {
        return decrypt(adminAad(adminUserId), blob);
    }

    private byte[] encrypt(byte[] aad, String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad);
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(IV_BYTES + ciphertext.length).put(iv).put(ciphertext).array();
        } catch (GeneralSecurityException e) {
            throw new ContactDetailCryptoException("Could not encrypt contact detail", e);
        }
    }

    private String decrypt(byte[] aad, byte[] blob) {
        if (blob == null) {
            return null;
        }
        if (blob.length <= IV_BYTES) {
            throw new ContactDetailCryptoException("Stored contact detail is truncated", null);
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, blob, 0, IV_BYTES));
            cipher.updateAAD(aad);
            byte[] plaintext = cipher.doFinal(blob, IV_BYTES, blob.length - IV_BYTES);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new ContactDetailCryptoException("Could not decrypt contact detail", e);
        }
    }

    private static byte[] aad(long accountId) {
        return ("customer_preferences:" + accountId).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] adminAad(String adminUserId) {
        return ("admin_preferences:" + adminUserId).getBytes(StandardCharsets.UTF_8);
    }

    /** Wrong key, tampered row, or a row moved between accounts. Never carries the value. */
    public static class ContactDetailCryptoException extends RuntimeException {
        public ContactDetailCryptoException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
