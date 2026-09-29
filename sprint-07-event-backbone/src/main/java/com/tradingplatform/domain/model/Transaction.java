package com.tradingplatform.domain.model;

import com.tradingplatform.domain.enums.TransactionAction;
import com.tradingplatform.domain.enums.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain model representing a financial transaction (deposit, withdrawal, fee, trade settlement).
 * Immutable transaction record with validation of constraints.
 */
public final class Transaction {

    private final UUID transactionId;
    private final Long accountId;
    private final UUID orderId;  // nullable for non-trade transactions
    private final TransactionType transactionType;
    private final TransactionAction actionType;
    private final BigDecimal amount;
    private final Instant createdOn;

    /**
     * Constructs a Transaction with all required fields.
     *
     * @param transactionId unique transaction identifier
     * @param accountId the account ID
     * @param orderId the order ID (nullable for DEPOSIT/WITHDRAW)
     * @param transactionType the type of transaction
     * @param actionType the action (DEBIT or CREDIT)
     * @param amount the transaction amount (must be positive)
     * @param createdOn the creation timestamp
     */
    public Transaction(UUID transactionId, Long accountId, UUID orderId,
                       TransactionType transactionType, TransactionAction actionType,
                       BigDecimal amount, Instant createdOn) {
        this.transactionId = Objects.requireNonNull(transactionId, "transactionId");
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.orderId = orderId;  // nullable
        this.transactionType = Objects.requireNonNull(transactionType, "transactionType");
        this.actionType = Objects.requireNonNull(actionType, "actionType");
        
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        this.amount = normalise(amount);
        this.createdOn = Objects.requireNonNull(createdOn, "createdOn");

        validateTransactionRules();
    }

    /**
     * Constructor for MyBatis result mapping.
     * Parameter order matches @ConstructorArgs in TransactionMapper.
     * This is needed because MyBatis passes constructor parameters in the order they appear in @ConstructorArgs,
     * not the order they appear in the primary constructor.
     *
     * @param accountId the account ID (first parameter from database)
     * @param orderId the order ID (nullable for DEPOSIT/WITHDRAW)
     * @param transactionType the type of transaction
     * @param actionType the action (DEBIT or CREDIT)
     * @param amount the transaction amount (must be positive)
     * @param createdOn the creation timestamp
     * @param transactionId unique transaction identifier (last parameter from database)
     */
    public Transaction(Long accountId, UUID orderId,
                       TransactionType transactionType, TransactionAction actionType,
                       BigDecimal amount, Instant createdOn, UUID transactionId) {
        this.transactionId = Objects.requireNonNull(transactionId, "transactionId");
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.orderId = orderId;  // nullable
        this.transactionType = Objects.requireNonNull(transactionType, "transactionType");
        this.actionType = Objects.requireNonNull(actionType, "actionType");
        
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        this.amount = normalise(amount);
        this.createdOn = Objects.requireNonNull(createdOn, "createdOn");

        validateTransactionRules();
    }

    /**
     * Validates business rules for transactions.
     * - Trade types (BUY, SELL, FEE) must have orderId
     * - Non-trade types (DEPOSIT, WITHDRAW) must not have orderId
     * - Action type must match transaction type (e.g., DEPOSIT must be CREDIT)
     */
    private void validateTransactionRules() {
        boolean isTradeType = transactionType == TransactionType.BUY ||
                              transactionType == TransactionType.SELL ||
                              transactionType == TransactionType.FEE;

        if (isTradeType && orderId == null) {
            throw new IllegalArgumentException(
                "Order ID is required for " + transactionType + " transactions");
        }

        if (!isTradeType && orderId != null) {
            throw new IllegalArgumentException(
                "Order ID must be null for " + transactionType + " transactions");
        }

        // Validate action matches transaction type
        boolean actionValid = (transactionType == TransactionType.DEPOSIT && actionType == TransactionAction.CREDIT) ||
                             (transactionType == TransactionType.WITHDRAW && actionType == TransactionAction.DEBIT) ||
                             (transactionType == TransactionType.FEE && actionType == TransactionAction.DEBIT) ||
                             (transactionType == TransactionType.BUY && actionType == TransactionAction.DEBIT) ||
                             (transactionType == TransactionType.SELL && actionType == TransactionAction.CREDIT);

        if (!actionValid) {
            throw new IllegalArgumentException(
                "Invalid action " + actionType + " for transaction type " + transactionType);
        }
    }

    /**
     * Normalises amount to 2 decimal places.
     */
    private static BigDecimal normalise(BigDecimal value) {
        return value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public TransactionAction getActionType() {
        return actionType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getCreatedOn() {
        return createdOn;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Transaction that = (Transaction) o;
        return Objects.equals(transactionId, that.transactionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transactionId);
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "transactionId=" + transactionId +
                ", accountId=" + accountId +
                ", orderId=" + orderId +
                ", transactionType=" + transactionType +
                ", actionType=" + actionType +
                ", amount=" + amount +
                ", createdOn=" + createdOn +
                '}';
    }
}
