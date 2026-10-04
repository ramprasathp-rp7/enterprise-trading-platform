package com.tradingplatform.api.services;

import com.tradingplatform.api.mappers.AccountMapper;
import com.tradingplatform.api.mappers.TransactionMapper;
import com.tradingplatform.domain.dto.TransactionHistoryEntry;
import com.tradingplatform.domain.dto.TransactionResponse;
import com.tradingplatform.domain.enums.TransactionAction;
import com.tradingplatform.domain.enums.TransactionType;
import com.tradingplatform.domain.exception.AccountNotFoundException;
import com.tradingplatform.domain.exception.InsufficientFundsForWithdrawalException;
import com.tradingplatform.domain.exception.InvalidTransactionException;
import com.tradingplatform.domain.exception.TransactionNotFoundException;
import com.tradingplatform.domain.model.Account;
import com.tradingplatform.domain.model.Transaction;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service layer for transaction operations.
 * Orchestrates between controllers and mappers for data access and business logic.
 * Handles deposits, withdrawals, and transaction history retrieval.
 */
@Service
public class TransactionService {

    private final TransactionMapper transactionMapper;
    private final AccountMapper accountMapper;

    public TransactionService(TransactionMapper transactionMapper, AccountMapper accountMapper) {
        this.transactionMapper = transactionMapper;
        this.accountMapper = accountMapper;
    }

    /**
     * Process a deposit transaction for an account.
     *
     * @param accountId the account ID
     * @param amount the deposit amount (must be positive)
     * @return the created transaction
     * @throws AccountNotFoundException if account does not exist
     * @throws InvalidTransactionException if amount is invalid
     */
    public TransactionResponse deposit(Long accountId, BigDecimal amount) {
        // Verify account exists
        Account account = accountMapper.findByAccountId(accountId);
        if (account == null) {
            throw new AccountNotFoundException(accountId);
        }

        // Validate amount
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidTransactionException("Deposit amount must be positive");
        }

        // Create and persist transaction
        UUID transactionId = UUID.randomUUID();
        Transaction transaction = new Transaction(
            transactionId,
            accountId,
            null,  // no order for deposit
            TransactionType.DEPOSIT,
            TransactionAction.CREDIT,
            amount,
            Instant.now()
        );

        transactionMapper.insertTransaction(transaction);
        
        // Update account balance: add the deposited amount
        Account updatedAccount = new Account(
            account.getId(),
            account.getAccountId(),
            account.getHolderName(),
            account.getCashBalance().add(amount),
            account.getCurrency(),
            account.getStatus(),
            account.getVersion()
        );
        accountMapper.updateAccount(updatedAccount, account.getVersion() + 1);
        
        return mapToTransactionResponse(transaction);
    }

    /**
     * Process a withdrawal transaction for an account.
     * Verifies account has sufficient funds before allowing withdrawal.
     *
     * @param accountId the account ID
     * @param amount the withdrawal amount (must be positive)
     * @return the created transaction
     * @throws AccountNotFoundException if account does not exist
     * @throws InvalidTransactionException if amount is invalid
     * @throws InsufficientFundsForWithdrawalException if account lacks funds
     */
    public TransactionResponse withdraw(Long accountId, BigDecimal amount) {
        // Verify account exists
        Account account = accountMapper.findByAccountId(accountId);
        if (account == null) {
            throw new AccountNotFoundException(accountId);
        }

        // Validate amount
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidTransactionException("Withdrawal amount must be positive");
        }

        // Check sufficient funds
        if (account.getCashBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsForWithdrawalException(amount, account.getCashBalance());
        }

        // Create and persist transaction
        UUID transactionId = UUID.randomUUID();
        Transaction transaction = new Transaction(
            transactionId,
            accountId,
            null,  // no order for withdrawal
            TransactionType.WITHDRAW,
            TransactionAction.DEBIT,
            amount,
            Instant.now()
        );

        transactionMapper.insertTransaction(transaction);
        
        // Update account balance: subtract the withdrawn amount
        Account updatedAccount = new Account(
            account.getId(),
            account.getAccountId(),
            account.getHolderName(),
            account.getCashBalance().subtract(amount),
            account.getCurrency(),
            account.getStatus(),
            account.getVersion()
        );
        accountMapper.updateAccount(updatedAccount, account.getVersion() + 1);
        
        return mapToTransactionResponse(transaction);
    }

    /**
     * Retrieve a specific transaction by ID.
     *
     * @param transactionId the transaction ID
     * @return the transaction details
     * @throws TransactionNotFoundException if transaction does not exist
     */
    public TransactionResponse getTransaction(UUID transactionId) {
        Transaction transaction = transactionMapper.findByTransactionId(transactionId);
        if (transaction == null) {
            throw new TransactionNotFoundException(transactionId.toString());
        }
        return mapToTransactionResponse(transaction);
    }

    /**
     * Retrieve transaction history for an account.
     * Supports optional filtering by date range and transaction type.
     *
     * @param accountId the account ID
     * @param from optional start date (inclusive)
     * @param to optional end date (inclusive)
     * @param transactionType optional transaction type filter
     * @return list of transaction history entries (most recent first)
     * @throws AccountNotFoundException if account does not exist
     */
    public List<TransactionHistoryEntry> getTransactionHistory(Long accountId,
                                                               Optional<Instant> from,
                                                               Optional<Instant> to,
                                                               Optional<TransactionType> transactionType) {
        // Verify account exists
        Account account = accountMapper.findByAccountId(accountId);
        if (account == null) {
            throw new AccountNotFoundException(accountId);
        }

        List<Transaction> transactions;

        if (from.isPresent() && to.isPresent()) {
            transactions = transactionMapper.findByAccountIdAndDateRange(accountId, from.get(), to.get());
        } else if (transactionType.isPresent()) {
            transactions = transactionMapper.findByAccountIdAndTransactionType(accountId, transactionType.get());
        } else {
            transactions = transactionMapper.findByAccountId(accountId);
        }

        return transactions.stream()
                .map(this::mapToTransactionHistoryEntry)
                .toList();
    }

    /**
     * Get all transactions across all accounts (admin only).
     * Used for system-wide audit trails and compliance reporting.
     * 
     * @return list of all transactions (newest first)
     */
    public List<TransactionHistoryEntry> getAllTransactions() {
        return transactionMapper.findAllTransactions()
                .stream()
                .map(this::mapToTransactionHistoryEntry)
                .toList();
    }
    
    /**
     * Get all transactions across all accounts within a date range (admin only).
     * Used for system-wide audit trails and compliance reporting.
     * 
     * @param from start timestamp (inclusive)
     * @param to end timestamp (inclusive)
     * @return list of transactions within range (newest first)
     */
    public List<TransactionHistoryEntry> getAllTransactions(Instant from, Instant to) {
        // Validate date range
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("fromDate must be before toDate");
        }
        
        return transactionMapper.findAllTransactionsByDateRange(from, to)
                .stream()
                .map(this::mapToTransactionHistoryEntry)
                .toList();
    }

    /**
     * Maps Transaction domain model to TransactionResponse DTO.
     */
    private TransactionResponse mapToTransactionResponse(Transaction transaction) {
        return new TransactionResponse(
            transaction.getTransactionId(),
            transaction.getAccountId(),
            transaction.getOrderId(),
            transaction.getTransactionType(),
            transaction.getActionType(),
            transaction.getAmount(),
            transaction.getCreatedOn()
        );
    }

    /**
     * Maps Transaction domain model to TransactionHistoryEntry DTO.
     */
    private TransactionHistoryEntry mapToTransactionHistoryEntry(Transaction transaction) {
        return new TransactionHistoryEntry(
            transaction.getTransactionId(),
            transaction.getTransactionType(),
            transaction.getActionType(),
            transaction.getAmount(),
            transaction.getCreatedOn()
        );
    }
}
