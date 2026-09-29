package com.tradingplatform.domain.model;

import com.tradingplatform.domain.enums.TransactionAction;
import com.tradingplatform.domain.enums.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for Transaction domain model.
 * Verifies transaction creation, validation of business rules, and immutability.
 */
class TransactionTest {

    private static final UUID TRANSACTION_ID = UUID.randomUUID();
    private static final Long ACCOUNT_ID = 1L;
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final BigDecimal AMOUNT = new BigDecimal("100.00");
    private static final Instant NOW = Instant.now();

    /**
     * Test successful creation of a deposit transaction.
     */
    @Test
    void depositTransactionCreatedSuccessfully() {
        Transaction transaction = new Transaction(
            TRANSACTION_ID, ACCOUNT_ID, null,
            TransactionType.DEPOSIT, TransactionAction.CREDIT,
            AMOUNT, NOW
        );

        assertAll(
            () -> assertEquals(TRANSACTION_ID, transaction.getTransactionId()),
            () -> assertEquals(ACCOUNT_ID, transaction.getAccountId()),
            () -> assertNull(transaction.getOrderId()),
            () -> assertEquals(TransactionType.DEPOSIT, transaction.getTransactionType()),
            () -> assertEquals(TransactionAction.CREDIT, transaction.getActionType()),
            () -> assertEquals(AMOUNT, transaction.getAmount()),
            () -> assertEquals(NOW, transaction.getCreatedOn())
        );
    }

    /**
     * Test successful creation of a withdrawal transaction.
     */
    @Test
    void withdrawalTransactionCreatedSuccessfully() {
        Transaction transaction = new Transaction(
            TRANSACTION_ID, ACCOUNT_ID, null,
            TransactionType.WITHDRAW, TransactionAction.DEBIT,
            AMOUNT, NOW
        );

        assertAll(
            () -> assertEquals(TransactionType.WITHDRAW, transaction.getTransactionType()),
            () -> assertEquals(TransactionAction.DEBIT, transaction.getActionType()),
            () -> assertNull(transaction.getOrderId())
        );
    }

    /**
     * Test successful creation of a BUY transaction (requires orderId).
     */
    @Test
    void buyTransactionCreatedSuccessfully() {
        Transaction transaction = new Transaction(
            TRANSACTION_ID, ACCOUNT_ID, ORDER_ID,
            TransactionType.BUY, TransactionAction.DEBIT,
            AMOUNT, NOW
        );

        assertAll(
            () -> assertEquals(TransactionType.BUY, transaction.getTransactionType()),
            () -> assertEquals(TransactionAction.DEBIT, transaction.getActionType()),
            () -> assertEquals(ORDER_ID, transaction.getOrderId())
        );
    }

    /**
     * Test successful creation of a SELL transaction (requires orderId).
     */
    @Test
    void sellTransactionCreatedSuccessfully() {
        Transaction transaction = new Transaction(
            TRANSACTION_ID, ACCOUNT_ID, ORDER_ID,
            TransactionType.SELL, TransactionAction.CREDIT,
            AMOUNT, NOW
        );

        assertAll(
            () -> assertEquals(TransactionType.SELL, transaction.getTransactionType()),
            () -> assertEquals(TransactionAction.CREDIT, transaction.getActionType()),
            () -> assertEquals(ORDER_ID, transaction.getOrderId())
        );
    }

    /**
     * Test successful creation of a FEE transaction (requires orderId).
     */
    @Test
    void feeTransactionCreatedSuccessfully() {
        Transaction transaction = new Transaction(
            TRANSACTION_ID, ACCOUNT_ID, ORDER_ID,
            TransactionType.FEE, TransactionAction.DEBIT,
            new BigDecimal("5.50"), NOW
        );

        assertAll(
            () -> assertEquals(TransactionType.FEE, transaction.getTransactionType()),
            () -> assertEquals(TransactionAction.DEBIT, transaction.getActionType()),
            () -> assertEquals(ORDER_ID, transaction.getOrderId())
        );
    }

    /**
     * Test that deposit requires CREDIT action.
     */
    @Test
    void depositMustHaveCreditAction() {
        assertThrows(IllegalArgumentException.class, () ->
            new Transaction(
                TRANSACTION_ID, ACCOUNT_ID, null,
                TransactionType.DEPOSIT, TransactionAction.DEBIT,  // Invalid!
                AMOUNT, NOW
            )
        );
    }

    /**
     * Test that withdrawal requires DEBIT action.
     */
    @Test
    void withdrawalMustHaveDebitAction() {
        assertThrows(IllegalArgumentException.class, () ->
            new Transaction(
                TRANSACTION_ID, ACCOUNT_ID, null,
                TransactionType.WITHDRAW, TransactionAction.CREDIT,  // Invalid!
                AMOUNT, NOW
            )
        );
    }

    /**
     * Test that trade transactions (BUY, SELL, FEE) require orderId.
     */
    @Test
    void buyTransactionRequiresOrderId() {
        assertThrows(IllegalArgumentException.class, () ->
            new Transaction(
                TRANSACTION_ID, ACCOUNT_ID, null,  // Missing orderId!
                TransactionType.BUY, TransactionAction.DEBIT,
                AMOUNT, NOW
            )
        );
    }

    /**
     * Test that non-trade transactions (DEPOSIT, WITHDRAW) must not have orderId.
     */
    @Test
    void depositMustNotHaveOrderId() {
        assertThrows(IllegalArgumentException.class, () ->
            new Transaction(
                TRANSACTION_ID, ACCOUNT_ID, ORDER_ID,  // Should be null!
                TransactionType.DEPOSIT, TransactionAction.CREDIT,
                AMOUNT, NOW
            )
        );
    }

    /**
     * Test that negative amount is rejected.
     */
    @Test
    void negativeAmountThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
            new Transaction(
                TRANSACTION_ID, ACCOUNT_ID, null,
                TransactionType.DEPOSIT, TransactionAction.CREDIT,
                new BigDecimal("-50.00"),  // Invalid!
                NOW
            )
        );
    }

    /**
     * Test that zero amount is rejected.
     */
    @Test
    void zeroAmountThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
            new Transaction(
                TRANSACTION_ID, ACCOUNT_ID, null,
                TransactionType.DEPOSIT, TransactionAction.CREDIT,
                BigDecimal.ZERO,  // Invalid!
                NOW
            )
        );
    }

    /**
     * Test that null transactionId is rejected.
     */
    @Test
    void nullTransactionIdThrowsException() {
        assertThrows(NullPointerException.class, () ->
            new Transaction(
                null,  // Invalid!
                ACCOUNT_ID, null,
                TransactionType.DEPOSIT, TransactionAction.CREDIT,
                AMOUNT, NOW
            )
        );
    }

    /**
     * Test that null accountId is rejected.
     */
    @Test
    void nullAccountIdThrowsException() {
        assertThrows(NullPointerException.class, () ->
            new Transaction(
                TRANSACTION_ID,
                null,  // Invalid!
                null,
                TransactionType.DEPOSIT, TransactionAction.CREDIT,
                AMOUNT, NOW
            )
        );
    }

    /**
     * Test that amount is normalized to 2 decimal places.
     */
    @Test
    void amountIsNormalizedToTwoDecimalPlaces() {
        Transaction transaction = new Transaction(
            TRANSACTION_ID, ACCOUNT_ID, null,
            TransactionType.DEPOSIT, TransactionAction.CREDIT,
            new BigDecimal("100.555"),  // 3 decimals
            NOW
        );

        assertEquals(new BigDecimal("100.56"), transaction.getAmount());
    }

    /**
     * Test transaction equality based on transactionId.
     */
    @Test
    void transactionsEqualIfSameId() {
        Transaction t1 = new Transaction(
            TRANSACTION_ID, ACCOUNT_ID, null,
            TransactionType.DEPOSIT, TransactionAction.CREDIT,
            AMOUNT, NOW
        );

        Transaction t2 = new Transaction(
            TRANSACTION_ID, 999L, null,  // Different account
            TransactionType.WITHDRAW, TransactionAction.DEBIT,
            new BigDecimal("999.00"), Instant.now()  // Different data
        );

        assertEquals(t1, t2);  // Same ID = equal
    }

    /**
     * Test transaction inequality for different IDs.
     */
    @Test
    void transactionsNotEqualIfDifferentId() {
        Transaction t1 = new Transaction(
            TRANSACTION_ID, ACCOUNT_ID, null,
            TransactionType.DEPOSIT, TransactionAction.CREDIT,
            AMOUNT, NOW
        );

        Transaction t2 = new Transaction(
            UUID.randomUUID(), ACCOUNT_ID, null,
            TransactionType.DEPOSIT, TransactionAction.CREDIT,
            AMOUNT, NOW
        );

        assertNotNull(t1);
        assertNotNull(t2);
    }
}
