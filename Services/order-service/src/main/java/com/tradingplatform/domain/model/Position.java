package com.tradingplatform.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class Position {

    private final Long accountId;
    private final Long instrumentId;
    private final int version;

    private int quantity;
    private BigDecimal averageCost;
    /** Average cost per unit in the account's currency, at the fill rates of the buys. */
    private BigDecimal averageCostAccount;

    public Position(Long accountId, Long instrumentId, int quantity, BigDecimal averageCost) {
        this(accountId, instrumentId, quantity, averageCost, 0);
    }

    public Position(Long accountId, Long instrumentId, int quantity, BigDecimal averageCost, int version) {
        this(accountId, instrumentId, quantity, averageCost, averageCost, version);
    }

    /**
     * @param averageCost        average cost per unit in the instrument's currency
     * @param averageCostAccount average cost per unit in the account's currency (null: same as averageCost)
     */
    public Position(Long accountId, Long instrumentId, int quantity, BigDecimal averageCost,
                    BigDecimal averageCostAccount, int version) {
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.instrumentId = Objects.requireNonNull(instrumentId, "instrumentId");
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity must not be negative: " + quantity);
        }
        this.quantity = quantity;
        this.averageCost = normalise(Objects.requireNonNull(averageCost, "averageCost"));
        this.averageCostAccount = normalise(averageCostAccount != null ? averageCostAccount : averageCost);
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative: " + version);
        }
        this.version = version;
    }

    public Long getAccountId() {
        return accountId;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getAverageCost() {
        return averageCost;
    }

    public BigDecimal getAverageCostAccount() {
        return averageCostAccount;
    }

    public int getVersion() {
        return version;
    }

    public static Position empty(Long accountId, Long instrumentId) {
        return new Position(accountId, instrumentId, 0, BigDecimal.ZERO);
    }

    private static BigDecimal normalise(BigDecimal amount) {
        return amount.setScale(6, RoundingMode.HALF_UP);
    }

    /** A buyrecalculates the weighted average across the old holding and the new units bought at {@code price}. */
    public void applyBuy(int boughtQuantity, BigDecimal price) {
        applyBuy(boughtQuantity, price, price);
    }

    /**
     * A buy in a foreign-currency instrument: both average costs are re-weighted, the instrument-currency
     * one at {@code price} and the account-currency one at {@code priceAccount} (price times the fill rate).
     */
    public void applyBuy(int boughtQuantity, BigDecimal price, BigDecimal priceAccount) {
        if (boughtQuantity <= 0) {
            throw new IllegalArgumentException("boughtQuantity must be greater than zero: " + boughtQuantity);
        }
        BigDecimal normalisedPrice = normalise(Objects.requireNonNull(price, "price"));
        BigDecimal normalisedPriceAccount = normalise(Objects.requireNonNull(priceAccount, "priceAccount"));

        int newQuantity = quantity + boughtQuantity;
        BigDecimal existingCostBasis = averageCost.multiply(BigDecimal.valueOf(quantity));
        BigDecimal newCostBasis = normalisedPrice.multiply(BigDecimal.valueOf(boughtQuantity));

        this.averageCost = existingCostBasis.add(newCostBasis)
            .divide(BigDecimal.valueOf(newQuantity), 6, RoundingMode.HALF_UP);
        this.averageCostAccount = averageCostAccount.multiply(BigDecimal.valueOf(quantity))
            .add(normalisedPriceAccount.multiply(BigDecimal.valueOf(boughtQuantity)))
            .divide(BigDecimal.valueOf(newQuantity), 6, RoundingMode.HALF_UP);
        this.quantity = newQuantity;
    }

    /**
     * A sell reduces the quantity, leaves the average cost unchanged.
     *
     * @throws IllegalStateException if {@code soldQuantity} exceeds what is currently held
     */
    public void applySell(int soldQuantity) {
        if (soldQuantity <= 0) {
            throw new IllegalArgumentException("soldQuantity must be greater than zero: " + soldQuantity);
        }
        if (soldQuantity > quantity) {
            throw new IllegalStateException(
                "cannot sell " + soldQuantity + " units, only " + quantity + " held");
        }
        this.quantity -= soldQuantity;
    }

}
