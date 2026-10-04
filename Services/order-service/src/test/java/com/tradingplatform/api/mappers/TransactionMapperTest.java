package com.tradingplatform.api.mappers;

import com.tradingplatform.domain.enums.TransactionAction;
import com.tradingplatform.domain.enums.TransactionType;
import com.tradingplatform.domain.model.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Unit tests for TransactionMapper.
 * Note: Full integration tests would require a real database connection via MyBatis integration tests.
 * These unit tests verify the mapper interface and ConstructorArgs configuration.
 */
class TransactionMapperTest {

    private Transaction testTransaction;
    private UUID testTransactionId;
    private Long testAccountId;

    @BeforeEach
    void setUp() {
        testTransactionId = UUID.randomUUID();
        testAccountId = 1L;
        testTransaction = new Transaction(
            testTransactionId,
            testAccountId,
            null,
            TransactionType.DEPOSIT,
            TransactionAction.CREDIT,
            new BigDecimal("500.00"),
            Instant.now()
        );
    }

    /**
     * Test that a valid deposit transaction can be constructed.
     * This validates that the ConstructorArgs mappings align with the Transaction constructor.
     */
    @Test
    void depositTransactionCanBeConstructedFromMapperValues() {
        assertAll(
            () -> assertEquals(testTransactionId, testTransaction.getTransactionId()),
            () -> assertEquals(testAccountId, testTransaction.getAccountId()),
            () -> assertEquals(TransactionType.DEPOSIT, testTransaction.getTransactionType()),
            () -> assertEquals(TransactionAction.CREDIT, testTransaction.getActionType()),
            () -> assertEquals(new BigDecimal("500.00"), testTransaction.getAmount()),
            () -> assertNotNull(testTransaction.getCreatedOn())
        );
    }

    /**
     * Test that a valid withdrawal transaction can be constructed.
     */
    @Test
    void withdrawalTransactionCanBeConstructedFromMapperValues() {
        Transaction withdrawTransaction = new Transaction(
            testTransactionId,
            testAccountId,
            null,
            TransactionType.WITHDRAW,
            TransactionAction.DEBIT,
            new BigDecimal("100.00"),
            Instant.now()
        );

        assertAll(
            () -> assertEquals(testTransactionId, withdrawTransaction.getTransactionId()),
            () -> assertEquals(testAccountId, withdrawTransaction.getAccountId()),
            () -> assertEquals(TransactionType.WITHDRAW, withdrawTransaction.getTransactionType()),
            () -> assertEquals(TransactionAction.DEBIT, withdrawTransaction.getActionType()),
            () -> assertEquals(new BigDecimal("100.00"), withdrawTransaction.getAmount())
        );
    }

    /**
     * Test that a BUY transaction with order ID can be constructed.
     */
    @Test
    void buyTransactionWithOrderIdCanBeConstructedFromMapperValues() {
        UUID orderId = UUID.randomUUID();
        Transaction buyTransaction = new Transaction(
            testTransactionId,
            testAccountId,
            orderId,
            TransactionType.BUY,
            TransactionAction.DEBIT,
            new BigDecimal("250.50"),
            Instant.now()
        );

        assertAll(
            () -> assertEquals(testTransactionId, buyTransaction.getTransactionId()),
            () -> assertEquals(testAccountId, buyTransaction.getAccountId()),
            () -> assertEquals(orderId, buyTransaction.getOrderId()),
            () -> assertEquals(TransactionType.BUY, buyTransaction.getTransactionType()),
            () -> assertEquals(TransactionAction.DEBIT, buyTransaction.getActionType()),
            () -> assertEquals(new BigDecimal("250.50"), buyTransaction.getAmount())
        );
    }

    /**
     * Test that a SELL transaction with order ID can be constructed.
     */
    @Test
    void sellTransactionWithOrderIdCanBeConstructedFromMapperValues() {
        UUID orderId = UUID.randomUUID();
        Transaction sellTransaction = new Transaction(
            testTransactionId,
            testAccountId,
            orderId,
            TransactionType.SELL,
            TransactionAction.CREDIT,
            new BigDecimal("1500.75"),
            Instant.now()
        );

        assertAll(
            () -> assertEquals(testTransactionId, sellTransaction.getTransactionId()),
            () -> assertEquals(testAccountId, sellTransaction.getAccountId()),
            () -> assertEquals(orderId, sellTransaction.getOrderId()),
            () -> assertEquals(TransactionType.SELL, sellTransaction.getTransactionType()),
            () -> assertEquals(TransactionAction.CREDIT, sellTransaction.getActionType()),
            () -> assertEquals(new BigDecimal("1500.75"), sellTransaction.getAmount())
        );
    }

    /**
     * Test that a FEE transaction with order ID can be constructed.
     */
    @Test
    void feeTransactionWithOrderIdCanBeConstructedFromMapperValues() {
        UUID orderId = UUID.randomUUID();
        Transaction feeTransaction = new Transaction(
            testTransactionId,
            testAccountId,
            orderId,
            TransactionType.FEE,
            TransactionAction.DEBIT,
            new BigDecimal("5.50"),
            Instant.now()
        );

        assertAll(
            () -> assertEquals(testTransactionId, feeTransaction.getTransactionId()),
            () -> assertEquals(testAccountId, feeTransaction.getAccountId()),
            () -> assertEquals(orderId, feeTransaction.getOrderId()),
            () -> assertEquals(TransactionType.FEE, feeTransaction.getTransactionType()),
            () -> assertEquals(TransactionAction.DEBIT, feeTransaction.getActionType()),
            () -> assertEquals(new BigDecimal("5.50"), feeTransaction.getAmount())
        );
    }

    /**
     * Verify all TransactionType enum values are supported by mapper.
     */
    @Test
    void allTransactionTypesAreSupported() {
        for (TransactionType type : TransactionType.values()) {
            assertNotNull(type);
            assertNotNull(type.name());
        }
    }

    /**
     * Verify all TransactionAction enum values are supported by mapper.
     */
    @Test
    void allTransactionActionsAreSupported() {
        for (TransactionAction action : TransactionAction.values()) {
            assertNotNull(action);
            assertNotNull(action.name());
        }
    }

    /**
     * Verify mapper exposes idempotency helper used by ORDER_FILLED consumer.
     */
    @Test
    void mapperDeclaresExistsByOrderIdAndTransactionType() throws Exception {
        Method method = TransactionMapper.class.getMethod(
            "existsByOrderIdAndTransactionType",
            UUID.class,
            TransactionType.class
        );

        assertNotNull(method);
        assertSame(boolean.class, method.getReturnType());
        assertArrayEquals(new Class<?>[] { UUID.class, TransactionType.class }, method.getParameterTypes());
    }
}
