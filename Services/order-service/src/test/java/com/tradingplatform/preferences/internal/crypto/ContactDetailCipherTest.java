package com.tradingplatform.preferences.internal.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.tradingplatform.preferences.internal.crypto.ContactDetailCipher.ContactDetailCryptoException;

class ContactDetailCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private final ContactDetailCipher cipher = new ContactDetailCipher(KEY);

    @Test
    void roundTrips() {
        byte[] blob = cipher.encrypt(2L, "customer@example.com");

        assertEquals("customer@example.com", cipher.decrypt(2L, blob));
    }

    @Test
    void nullStaysNull() {
        assertNull(cipher.encrypt(2L, null));
        assertNull(cipher.decrypt(2L, null));
    }

    @Test
    void ciphertextMovedToAnotherAccountDoesNotDecrypt() {
        byte[] blob = cipher.encrypt(2L, "customer@example.com");

        assertThrows(ContactDetailCryptoException.class, () -> cipher.decrypt(3L, blob));
    }

    @Test
    void adminValuesRoundTripAndAreBoundToTheAdmin() {
        byte[] blob = cipher.encryptForAdmin("admin-1", "ops@example.com");

        assertEquals("ops@example.com", cipher.decryptForAdmin("admin-1", blob));
        assertThrows(ContactDetailCryptoException.class, () -> cipher.decryptForAdmin("admin-2", blob));
    }

    @Test
    void adminAndCustomerCiphertextsAreNotInterchangeable() {
        byte[] customerBlob = cipher.encrypt(2L, "customer@example.com");
        byte[] adminBlob = cipher.encryptForAdmin("2", "ops@example.com");

        assertThrows(ContactDetailCryptoException.class, () -> cipher.decryptForAdmin("2", customerBlob));
        assertThrows(ContactDetailCryptoException.class, () -> cipher.decrypt(2L, adminBlob));
    }

    @Test
    void differentKeyDoesNotDecrypt() {
        byte[] blob = cipher.encrypt(2L, "customer@example.com");
        byte[] otherKey = new byte[32];
        java.util.Arrays.fill(otherKey, (byte) 1);
        ContactDetailCipher other = new ContactDetailCipher(Base64.getEncoder().encodeToString(otherKey));

        assertThrows(ContactDetailCryptoException.class, () -> other.decrypt(2L, blob));
    }

    @Test
    void refusesKeyOfWrongLength() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThrows(IllegalStateException.class, () -> new ContactDetailCipher(shortKey));
        assertThrows(IllegalStateException.class, () -> new ContactDetailCipher("not base64 !!"));
    }

    @Test
    void aMissingKeyStopsStartup() {
        assertThrows(IllegalStateException.class, () -> new ContactDetailCipher(""));
        assertThrows(IllegalStateException.class, () -> new ContactDetailCipher(null));
    }
}
