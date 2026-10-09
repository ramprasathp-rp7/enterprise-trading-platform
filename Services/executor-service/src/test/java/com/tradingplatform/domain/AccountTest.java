package com.tradingplatform.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.tradingplatform.enums.AccountStatus;

class AccountTest {

    private Account account(String balance, AccountStatus status) {
        return new Account(1L, "ACC-001", "John", new BigDecimal(balance), "USD", status, 0);
    }

    @Test
    void fullConstructorExposesAllFieldsAndNormalisesBalance() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        Instant updated = Instant.parse("2026-01-02T00:00:00Z");
        Account a = new Account(7L, "ACC-007", "Jane", new BigDecimal("10.005"), "INR",
            AccountStatus.ACTIVE, 3, created, updated);

        assertEquals(7L, a.getId());
        assertEquals("ACC-007", a.getAccountId());
        assertEquals("Jane", a.getHolderName());
        assertEquals(new BigDecimal("10.01"), a.getCashBalance());
        assertEquals("INR", a.getCurrency());
        assertEquals(AccountStatus.ACTIVE, a.getStatus());
        assertEquals(3, a.getVersion());
        assertEquals(created, a.getCreatedOn());
        assertEquals(updated, a.getLastUpdated());
    }

    @Test
    void shortConstructorLeavesTimestampsNull() {
        Account a = account("100", AccountStatus.ACTIVE);
        assertNull(a.getCreatedOn());
        assertNull(a.getLastUpdated());
    }

    @Test
    void negativeVersionIsRejectedByBothConstructors() {
        assertThrows(IllegalArgumentException.class, () -> new Account(1L, "A", "H",
            BigDecimal.ONE, "USD", AccountStatus.ACTIVE, -1));
        assertThrows(IllegalArgumentException.class, () -> new Account(1L, "A", "H",
            BigDecimal.ONE, "USD", AccountStatus.ACTIVE, -1, null, null));
    }

    @Test
    void nullRequiredFieldsAreRejected() {
        assertThrows(NullPointerException.class, () -> new Account(1L, null, "H",
            BigDecimal.ONE, "USD", AccountStatus.ACTIVE, 0));
        assertThrows(NullPointerException.class, () -> new Account(1L, "A", "H",
            null, "USD", AccountStatus.ACTIVE, 0));
        assertThrows(NullPointerException.class, () -> new Account(1L, "A", "H",
            BigDecimal.ONE, "USD", null, 0, null, null));
    }

    @Test
    void isActiveReflectsStatus() {
        assertTrue(account("1", AccountStatus.ACTIVE).isActive());
        for (AccountStatus s : AccountStatus.values()) {
            if (s != AccountStatus.ACTIVE) {
                assertFalse(account("1", s).isActive());
            }
        }
    }

    @Test
    void canAffordComparesAgainstBalance() {
        Account a = account("100.00", AccountStatus.ACTIVE);
        assertTrue(a.canAfford(new BigDecimal("100.00")));
        assertTrue(a.canAfford(new BigDecimal("99.99")));
        assertFalse(a.canAfford(new BigDecimal("100.01")));
        assertThrows(NullPointerException.class, () -> a.canAfford(null));
    }

    @Test
    void debitReducesBalance() {
        Account a = account("100.00", AccountStatus.ACTIVE);
        a.debit(new BigDecimal("40.00"));
        assertEquals(new BigDecimal("60.00"), a.getCashBalance());
    }

    @Test
    void debitBeyondBalanceIsRejectedAndBalanceUnchanged() {
        Account a = account("100.00", AccountStatus.ACTIVE);
        BigDecimal overdraft = new BigDecimal("100.01");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> a.debit(overdraft));
        assertTrue(ex.getMessage().contains("ACC-001"));
        assertEquals(new BigDecimal("100.00"), a.getCashBalance());
    }

    @Test
    void creditIncreasesBalance() {
        Account a = account("100.00", AccountStatus.ACTIVE);
        a.credit(new BigDecimal("0.50"));
        assertEquals(new BigDecimal("100.50"), a.getCashBalance());
    }

    @Test
    void nonPositiveAmountsAreRejected() {
        Account a = account("100.00", AccountStatus.ACTIVE);
        assertThrows(IllegalArgumentException.class, () -> a.credit(BigDecimal.ZERO));
        BigDecimal negative = new BigDecimal("-1");
        assertThrows(IllegalArgumentException.class, () -> a.debit(negative));
        // rounds to zero at 2dp
        BigDecimal belowOneCent = new BigDecimal("0.001");
        assertThrows(IllegalArgumentException.class, () -> a.credit(belowOneCent));
        assertThrows(NullPointerException.class, () -> a.credit(null));
    }
}
