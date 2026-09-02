package com.tradingplatform.domain;

import com.tradingplatform.domain.enums.AccountStatus;
import com.tradingplatform.domain.model.Account;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountTest {

    private static Account account(AccountStatus status, String balance) {
        return new Account(1L, "ACC-000001", "Priya Menon", new BigDecimal(balance), "USD", status, 0);
    }

    // --- status -------------------------------------------------------

    @Test
    void activeAccountIsActive() {
        assertTrue(account(AccountStatus.ACTIVE, "1000.00").isActive());
    }

    @Test
    void suspendedAccountIsNotActive() {
        assertFalse(account(AccountStatus.SUSPENDED, "1000.00").isActive());
    }

    @Test
    void closedAccountIsNotActive() {
        assertFalse(account(AccountStatus.CLOSED, "1000.00").isActive());
    }

    // --- identity -------------------------------------------------------

    @Test
    void accountCarriesBothIdentifiersDistinctly() {
        Account acc = account(AccountStatus.ACTIVE, "1000.00");
        assertEquals(1L, acc.getId());
        assertEquals("ACC-000001", acc.getAccountId());
        assertNotEquals(acc.getId().toString(), acc.getAccountId());
    }

    @Test
    void accountReportsTheVersionItWasLoadedAt() {
        Account acc = new Account(1L, "ACC-000001", "Priya Menon", new BigDecimal("1000.00"), "USD",
            AccountStatus.ACTIVE, 7);
        assertEquals(7, acc.getVersion());
    }

    @Test
    void negativeVersionIsRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class, () ->
            new Account(1L, "ACC-000001", "Priya Menon", new BigDecimal("1000.00"), "USD",
                AccountStatus.ACTIVE, -1));
    }

    // --- affordability -------------------------------------------------------

    @Test
    void canAffordReturnsTrueWhenBalanceCoversAmountExactly() {
        Account acc = account(AccountStatus.ACTIVE, "500.00");
        assertTrue(acc.canAfford(new BigDecimal("500.00")));
    }

    @Test
    void canAffordReturnsTrueWhenBalanceComfortablyCoversAmount() {
        Account acc = account(AccountStatus.ACTIVE, "500.00");
        assertTrue(acc.canAfford(new BigDecimal("100.00")));
    }

    @Test
    void canAffordReturnsFalseWhenBalanceIsShort() {
        Account acc = account(AccountStatus.ACTIVE, "500.00");
        assertFalse(acc.canAfford(new BigDecimal("500.01")));
    }

    // --- debit / credit -------------------------------------------------------

    @Test
    void debitReducesBalanceByExactlyTheAmount() {
        Account acc = account(AccountStatus.ACTIVE, "500.00");
        acc.debit(new BigDecimal("199.99"));
        assertEquals(new BigDecimal("300.01"), acc.getCashBalance());
    }

    @Test
    void creditIncreasesBalanceByExactlyTheAmount() {
        Account acc = account(AccountStatus.ACTIVE, "500.00");
        acc.credit(new BigDecimal("0.01"));
        assertEquals(new BigDecimal("500.01"), acc.getCashBalance());
    }

    @Test
    void debitRefusesToLeaveTheBalanceNegative() {
        Account acc = account(AccountStatus.ACTIVE, "100.00");
        assertThrows(IllegalStateException.class, () -> acc.debit(new BigDecimal("100.01")));
    }

    @Test
    void debitRefusedBeforeAnythingIsSubtracted() {
        Account acc = account(AccountStatus.ACTIVE, "100.00");
        assertThrows(IllegalStateException.class, () -> acc.debit(new BigDecimal("100.01")));
        // The balance is exactly what it was before the refused debit was attempted.
        assertEquals(new BigDecimal("100.00"), acc.getCashBalance());
    }

    @Test
    void debitOfExactBalanceLeavesZero() {
        Account acc = account(AccountStatus.ACTIVE, "100.00");
        acc.debit(new BigDecimal("100.00"));
        assertEquals(new BigDecimal("0.00"), acc.getCashBalance());
    }

    @Test
    void debitOfZeroIsRejected() {
        Account acc = account(AccountStatus.ACTIVE, "100.00");
        assertThrows(IllegalArgumentException.class, () -> acc.debit(BigDecimal.ZERO));
    }

    @Test
    void debitOfNegativeAmountIsRejected() {
        Account acc = account(AccountStatus.ACTIVE, "100.00");
        assertThrows(IllegalArgumentException.class, () -> acc.debit(new BigDecimal("-1.00")));
    }

    @Test
    void creditOfZeroIsRejected() {
        Account acc = account(AccountStatus.ACTIVE, "100.00");
        assertThrows(IllegalArgumentException.class, () -> acc.credit(BigDecimal.ZERO));
    }

    @Test
    void creditOfNegativeAmountIsRejected() {
        Account acc = account(AccountStatus.ACTIVE, "100.00");
        assertThrows(IllegalArgumentException.class, () -> acc.credit(new BigDecimal("-1.00")));
    }

    // --- money precision -------------------------------------------------------

    @Test
    void balanceDoesNotDriftOverManySmallOperations() {
        Account acc = account(AccountStatus.ACTIVE, "1000.00");
        for (int i = 0; i < 5_000; i++) {
            acc.debit(new BigDecimal("0.10"));
            acc.credit(new BigDecimal("0.10"));
        }
        assertEquals(new BigDecimal("1000.00"), acc.getCashBalance());
    }

    @Test
    void balanceIsNeverRepresentedAsADouble() {
        Account acc = account(AccountStatus.ACTIVE, "10.10");
        assertEquals("10.10", acc.getCashBalance().toString());
    }
}
