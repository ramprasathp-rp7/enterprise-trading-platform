package com.tradingplatform.api.services;

import com.tradingplatform.api.mappers.AccountMapper;
import com.tradingplatform.api.mappers.TransactionMapper;
import com.tradingplatform.domain.dto.TransactionHistoryEntry;
import com.tradingplatform.domain.dto.TransactionResponse;
import com.tradingplatform.domain.enums.AccountStatus;
import com.tradingplatform.domain.enums.TransactionType;
import com.tradingplatform.domain.exception.AccountNotFoundException;
import com.tradingplatform.domain.exception.InsufficientFundsForWithdrawalException;
import com.tradingplatform.domain.exception.InvalidTransactionException;
import com.tradingplatform.domain.exception.TransactionNotFoundException;
import com.tradingplatform.domain.model.Account;
import com.tradingplatform.domain.model.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TransactionService.
 * Verifies deposit, withdrawal, and history retrieval operations with proper exception handling.
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionMapper transactionMapper;

    @Mock
    private AccountMapper accountMapper;

    private TransactionService transactionService;

    private Account testAccount;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(transactionMapper, accountMapper);
        testAccount = new Account(
            1L, "ACC-001", "John Doe", new BigDecimal("5000.00"),
            "USD", AccountStatus.ACTIVE, 1
        );
    }

    /**
     * Test successful deposit transaction.
     */
    @Test
    void depositSuccessfullyCreatesTransaction() {
        when(accountMapper.findByAccountId(1L)).thenReturn(testAccount);

        BigDecimal depositAmount = new BigDecimal("500.00");
        TransactionResponse response = transactionService.deposit(1L, depositAmount);

        assertAll(
            () -> assertNotNull(response),
            () -> assertEquals(1L, response.accountId()),
            () -> assertEquals(depositAmount, response.amount()),
            () -> assertEquals(TransactionType.DEPOSIT, response.transactionType())
        );

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionMapper).insertTransaction(captor.capture());
        Transaction inserted = captor.getValue();

        assertAll(
            () -> assertEquals(1L, inserted.getAccountId()),
            () -> assertEquals(depositAmount, inserted.getAmount())
        );
    }

    /**
     * Test deposit with non-existent account throws exception.
     */
    @Test
    void depositWithNonExistentAccountThrowsException() {
        when(accountMapper.findByAccountId(999L)).thenReturn(null);

        assertThrows(AccountNotFoundException.class, () ->
            transactionService.deposit(999L, new BigDecimal("100.00"))
        );

        verify(transactionMapper, never()).insertTransaction(any());
    }

    /**
     * Test deposit with negative amount throws exception.
     */
    @Test
    void depositWithNegativeAmountThrowsException() {
        when(accountMapper.findByAccountId(1L)).thenReturn(testAccount);

        assertThrows(InvalidTransactionException.class, () ->
            transactionService.deposit(1L, new BigDecimal("-100.00"))
        );

        verify(transactionMapper, never()).insertTransaction(any());
    }

    /**
     * Test deposit with zero amount throws exception.
     */
    @Test
    void depositWithZeroAmountThrowsException() {
        when(accountMapper.findByAccountId(1L)).thenReturn(testAccount);

        assertThrows(InvalidTransactionException.class, () ->
            transactionService.deposit(1L, BigDecimal.ZERO)
        );

        verify(transactionMapper, never()).insertTransaction(any());
    }

    /**
     * Test deposit with null amount throws exception.
     */
    @Test
    void depositWithNullAmountThrowsException() {
        when(accountMapper.findByAccountId(1L)).thenReturn(testAccount);

        assertThrows(InvalidTransactionException.class, () ->
            transactionService.deposit(1L, null)
        );

        verify(transactionMapper, never()).insertTransaction(any());
    }

    /**
     * Test successful withdrawal transaction.
     */
    @Test
    void withdrawalSuccessfullyCreatesTransaction() {
        when(accountMapper.findByAccountId(1L)).thenReturn(testAccount);

        BigDecimal withdrawAmount = new BigDecimal("500.00");
        TransactionResponse response = transactionService.withdraw(1L, withdrawAmount);

        assertAll(
            () -> assertNotNull(response),
            () -> assertEquals(1L, response.accountId()),
            () -> assertEquals(withdrawAmount, response.amount()),
            () -> assertEquals(TransactionType.WITHDRAW, response.transactionType())
        );

        verify(transactionMapper).insertTransaction(any());
    }

    /**
     * Test withdrawal with insufficient funds throws exception.
     */
    @Test
    void withdrawalWithInsufficientFundsThrowsException() {
        when(accountMapper.findByAccountId(1L)).thenReturn(testAccount);

        BigDecimal withdrawAmount = new BigDecimal("10000.00");  // More than balance
        assertThrows(InsufficientFundsForWithdrawalException.class, () ->
            transactionService.withdraw(1L, withdrawAmount)
        );

        verify(transactionMapper, never()).insertTransaction(any());
    }

    /**
     * Test withdrawal with exact account balance succeeds.
     */
    @Test
    void withdrawalWithExactBalanceSucceeds() {
        when(accountMapper.findByAccountId(1L)).thenReturn(testAccount);

        BigDecimal withdrawAmount = new BigDecimal("5000.00");  // Exact balance
        TransactionResponse response = transactionService.withdraw(1L, withdrawAmount);

        assertNotNull(response);
        verify(transactionMapper).insertTransaction(any());
    }

    /**
     * Test withdrawal with non-existent account throws exception.
     */
    @Test
    void withdrawalWithNonExistentAccountThrowsException() {
        when(accountMapper.findByAccountId(999L)).thenReturn(null);

        assertThrows(AccountNotFoundException.class, () ->
            transactionService.withdraw(999L, new BigDecimal("100.00"))
        );

        verify(transactionMapper, never()).insertTransaction(any());
    }

    /**
     * Test retrieval of a specific transaction.
     */
    @Test
    void getTransactionReturnsTransaction() {
        UUID transactionId = UUID.randomUUID();
        Transaction mockTransaction = new Transaction(
            transactionId, 1L, null,
            TransactionType.DEPOSIT, com.tradingplatform.domain.enums.TransactionAction.CREDIT,
            new BigDecimal("500.00"), Instant.now()
        );

        when(transactionMapper.findByTransactionId(transactionId)).thenReturn(mockTransaction);

        TransactionResponse response = transactionService.getTransaction(transactionId);

        assertAll(
            () -> assertNotNull(response),
            () -> assertEquals(transactionId, response.transactionId()),
            () -> assertEquals(1L, response.accountId())
        );
    }

    /**
     * Test retrieval of non-existent transaction throws exception.
     */
    @Test
    void getTransactionWithInvalidIdThrowsException() {
        UUID invalidId = UUID.randomUUID();
        when(transactionMapper.findByTransactionId(invalidId)).thenReturn(null);

        assertThrows(TransactionNotFoundException.class, () ->
            transactionService.getTransaction(invalidId)
        );
    }

    /**
     * Test retrieval of transaction history for account.
     */
    @Test
    void getTransactionHistoryReturnsAllTransactions() {
        when(accountMapper.findByAccountId(1L)).thenReturn(testAccount);

        List<Transaction> mockTransactions = List.of(
            new Transaction(UUID.randomUUID(), 1L, null, TransactionType.DEPOSIT,
                com.tradingplatform.domain.enums.TransactionAction.CREDIT, new BigDecimal("500.00"), Instant.now()),
            new Transaction(UUID.randomUUID(), 1L, null, TransactionType.WITHDRAW,
                com.tradingplatform.domain.enums.TransactionAction.DEBIT, new BigDecimal("100.00"), Instant.now())
        );

        when(transactionMapper.findByAccountId(1L)).thenReturn(mockTransactions);

        List<TransactionHistoryEntry> history = transactionService.getTransactionHistory(
            1L, Optional.empty(), Optional.empty(), Optional.empty());

        assertEquals(2, history.size());
    }

    /**
     * Test retrieval of transaction history with date range filter.
     */
    @Test
    void getTransactionHistoryWithDateRangeFilters() {
        when(accountMapper.findByAccountId(1L)).thenReturn(testAccount);

        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-12-31T23:59:59Z");

        List<Transaction> mockTransactions = List.of(
            new Transaction(UUID.randomUUID(), 1L, null, TransactionType.DEPOSIT,
                com.tradingplatform.domain.enums.TransactionAction.CREDIT, new BigDecimal("500.00"), Instant.now())
        );

        when(transactionMapper.findByAccountIdAndDateRange(1L, from, to)).thenReturn(mockTransactions);

        List<TransactionHistoryEntry> history = transactionService.getTransactionHistory(
            1L, Optional.of(from), Optional.of(to), Optional.empty());

        assertEquals(1, history.size());
        verify(transactionMapper).findByAccountIdAndDateRange(eq(1L), eq(from), eq(to));
    }

    /**
     * Test retrieval of transaction history with transaction type filter.
     */
    @Test
    void getTransactionHistoryWithTransactionTypeFilter() {
        when(accountMapper.findByAccountId(1L)).thenReturn(testAccount);

        List<Transaction> mockTransactions = List.of(
            new Transaction(UUID.randomUUID(), 1L, null, TransactionType.DEPOSIT,
                com.tradingplatform.domain.enums.TransactionAction.CREDIT, new BigDecimal("500.00"), Instant.now())
        );

        when(transactionMapper.findByAccountIdAndTransactionType(1L, TransactionType.DEPOSIT))
            .thenReturn(mockTransactions);

        List<TransactionHistoryEntry> history = transactionService.getTransactionHistory(
            1L, Optional.empty(), Optional.empty(), Optional.of(TransactionType.DEPOSIT));

        assertEquals(1, history.size());
        verify(transactionMapper).findByAccountIdAndTransactionType(1L, TransactionType.DEPOSIT);
    }

    /**
     * Test retrieval of transaction history for non-existent account throws exception.
     */
    @Test
    void getTransactionHistoryWithNonExistentAccountThrowsException() {
        when(accountMapper.findByAccountId(999L)).thenReturn(null);

        assertThrows(AccountNotFoundException.class, () ->
            transactionService.getTransactionHistory(999L, Optional.empty(), Optional.empty(), Optional.empty())
        );
    }
}
