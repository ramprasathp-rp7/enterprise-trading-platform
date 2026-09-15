package com.tradingplatform.domain.model;

import com.tradingplatform.domain.enums.AccountStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

/**
 Account of the client with AccountStatus enum
 */
public final class Account {

    private final Long id;
    private final String accountId;
    private final String holderName;
    private final String currency;
    private final AccountStatus status;
    private final int version;
    private Instant lastUpdated;
    private Instant createdOn;

    private BigDecimal cashBalance;

    public Account(Long id, String accountId, String holderName, BigDecimal cashBalance,
                    String currency, AccountStatus status, int version, Instant createdOn, Instant lastUpdated) {
        this.id = id;
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.holderName = Objects.requireNonNull(holderName, "holderName");
        this.cashBalance = normalise(Objects.requireNonNull(cashBalance, "cashBalance"));
        this.currency = Objects.requireNonNull(currency, "currency");
        this.status = Objects.requireNonNull(status, "status");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative: " + version);
        }
        this.version = version;
        this.lastUpdated = lastUpdated;
        this.createdOn = createdOn;
    }

    public Account(Long id, String accountId, String holderName, BigDecimal cashBalance,
                    String currency, AccountStatus status, int version) {
        this.id = id;
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.holderName = Objects.requireNonNull(holderName, "holderName");
        this.cashBalance = normalise(Objects.requireNonNull(cashBalance, "cashBalance"));
        this.currency = Objects.requireNonNull(currency, "currency");
        this.status = Objects.requireNonNull(status, "status");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative: " + version);
        }
        this.version = version;
    }

    public Long getId() {
        return id;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getHolderName() {
        return holderName;
    }

    public BigDecimal getCashBalance() {
        return cashBalance;
    }

    public String getCurrency() {
        return currency;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public int getVersion() {
        return version;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public Instant getCreatedOn(){
        return createdOn;
    }
    
    private static BigDecimal normalise(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    /** Whether a debit of exactly this amount would leave the balance at or above zero. */
    public boolean canAfford(BigDecimal amount) {
        return cashBalance.compareTo(normalise(Objects.requireNonNull(amount, "amount"))) >= 0;
    }

    /**
     * moves cash from the account
     *
     * @throws IllegalStateException if the debit would leave the balance negative
     */
    public void debit(BigDecimal amount) {
        BigDecimal normalised = requirePositive(amount);
        if (!canAfford(normalised)) {
            throw new IllegalStateException(
                "debit of " + normalised + " would leave account " + accountId + " balance negative "
                    + "(current balance " + cashBalance + ")");
        }
        cashBalance = cashBalance.subtract(normalised);
    }

    /* moves cash to account. amount credited cannot be negative */
    public void credit(BigDecimal amount) {
        cashBalance = cashBalance.add(requirePositive(amount));
    }

    /*checks if amount is positive */
    private static BigDecimal requirePositive(BigDecimal amount) {
        BigDecimal normalised = normalise(Objects.requireNonNull(amount, "amount"));
        if (normalised.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero: " + normalised);
        }
        return normalised;
    }

}
