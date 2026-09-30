package com.tradingplatform.domain.model;

import com.tradingplatform.domain.enums.AccountStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertAll;
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
    void statusDeterminesWhetherTheAccountIsActive() {
        assertAll(
            () -> assertTrue(account(AccountStatus.ACTIVE, "1000.00").isActive(), "ACTIVE should be active"),
            () -> assertFalse(account(AccountStatus.SUSPENDED, "1000.00").isActive(), "SUSPENDED should not be active"),
            () -> assertFalse(account(AccountStatus.CLOSED, "1000.00").isActive(), "CLOSED should not be active")
        );
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
    void canAffordReflectsWhetherBalanceCoversTheAmount() {
        Account acc = account(AccountStatus.ACTIVE, "500.00");
        assertAll(
            () -> assertTrue(acc.canAfford(new BigDecimal("500.00")), "balance exactly covers amount"),
            () -> assertTrue(acc.canAfford(new BigDecimal("100.00")), "balance comfortably covers amount"),
            () -> assertFalse(acc.canAfford(new BigDecimal("500.01")), "balance is one cent short")
        );
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
    void debitRefusesToLeaveBalanceNegativeAndLeavesBalanceUnchanged() {
        Account acc = account(AccountStatus.ACTIVE, "100.00");
        assertThrows(IllegalStateException.class, () -> acc.debit(new BigDecimal("100.01")));
        assertEquals(new BigDecimal("100.00"), acc.getCashBalance());
    }

    @Test
    void debitOfExactBalanceLeavesZero() {
        Account acc = account(AccountStatus.ACTIVE, "100.00");
        acc.debit(new BigDecimal("100.00"));
        assertEquals(new BigDecimal("0.00"), acc.getCashBalance());
    }

    @Test
    void debitAndCreditRejectNonPositiveAmounts() {
        Account acc = account(AccountStatus.ACTIVE, "100.00");
        assertAll(
            () -> assertThrows(IllegalArgumentException.class, () -> acc.debit(BigDecimal.ZERO)),
            () -> assertThrows(IllegalArgumentException.class, () -> acc.debit(new BigDecimal("-1.00"))),
            () -> assertThrows(IllegalArgumentException.class, () -> acc.credit(BigDecimal.ZERO)),
            () -> assertThrows(IllegalArgumentException.class, () -> acc.credit(new BigDecimal("-1.00")))
        );
    }

    // --- money precision -------------------------------------------------------

    @Test
    void balanceDoesNotDriftOverManySmallOperationsAndIsNeverADouble() {
        Account acc = account(AccountStatus.ACTIVE, "1000.00");
        for (int i = 0; i < 5_000; i++) {
            acc.debit(new BigDecimal("0.10"));
            acc.credit(new BigDecimal("0.10"));
        }
        assertEquals(new BigDecimal("1000.00"), acc.getCashBalance());
        assertEquals("1000.00", acc.getCashBalance().toString());
    }
}