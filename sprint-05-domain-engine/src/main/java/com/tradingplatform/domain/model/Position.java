package com.tradingplatform.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class Position {

    private final Long accountId;
    private final Long instrumentId;

    private int quantity;
    private BigDecimal averageCost;

    public Position(Long accountId, Long instrumentId, int quantity, BigDecimal averageCost) {
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.instrumentId = Objects.requireNonNull(instrumentId, "instrumentId");
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity must not be negative: " + quantity);
        }
        this.quantity = quantity;
        this.averageCost = normalise(Objects.requireNonNull(averageCost, "averageCost"));
    }

    /** the default function a caller calls when it found no row for this account/instrument pair. */
    public static Position empty(Long accountId, Long instrumentId) {
        return new Position(accountId, instrumentId, 0, BigDecimal.ZERO);
    }

    private static BigDecimal normalise(BigDecimal amount) {
        return amount.setScale(6, RoundingMode.HALF_UP);
    }

    /** A buyrecalculates the weighted average across the old holding and the new units bought at {@code price}. */
    public void applyBuy(int boughtQuantity, BigDecimal price) {
        if (boughtQuantity <= 0) {
            throw new IllegalArgumentException("boughtQuantity must be greater than zero: " + boughtQuantity);
        }
        BigDecimal normalisedPrice = normalise(Objects.requireNonNull(price, "price"));

        int newQuantity = quantity + boughtQuantity;
        BigDecimal existingCostBasis = averageCost.multiply(BigDecimal.valueOf(quantity));
        BigDecimal newCostBasis = normalisedPrice.multiply(BigDecimal.valueOf(boughtQuantity));

        this.averageCost = existingCostBasis.add(newCostBasis)
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
}
