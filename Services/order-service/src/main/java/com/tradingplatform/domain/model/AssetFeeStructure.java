package com.tradingplatform.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * AssetFeeStructure implements Feeable to calculate fees based on flat + percentage model.
 * Fee = flatFee + (tradeVolume * percentageFee)
 * 
 * percentageFee is stored in decimal form:
 * - 0.0001 = 0.01%
 * - 0.001 = 0.1%
 * - 0.01 = 1%
 * 
 * If one component is 0, the other is the sole contributor.
 */
public final class AssetFeeStructure implements Feeable {

    private final String assetType;
    private final BigDecimal flatFee;
    private final BigDecimal percentageFee;

    public AssetFeeStructure(String assetType, BigDecimal flatFee, BigDecimal percentageFee) {
        this.assetType = Objects.requireNonNull(assetType, "assetType");
        this.flatFee = Objects.requireNonNull(flatFee, "flatFee");
        this.percentageFee = Objects.requireNonNull(percentageFee, "percentageFee");

        // Validate: at least one must be greater than 0
        if (flatFee.signum() <= 0 && percentageFee.signum() <= 0) {
            throw new IllegalArgumentException(
                "At least one of flatFee or percentageFee must be greater than zero");
        }

        // Validate: neither can be negative
        if (flatFee.signum() < 0) {
            throw new IllegalArgumentException("flatFee must not be negative: " + flatFee);
        }
        if (percentageFee.signum() < 0) {
            throw new IllegalArgumentException("percentageFee must not be negative: " + percentageFee);
        }
    }

    @Override
    public BigDecimal calculateFee(BigDecimal tradeVolume) {
        Objects.requireNonNull(tradeVolume, "tradeVolume");
        if (tradeVolume.signum() < 0) {
            throw new IllegalArgumentException("tradeVolume must not be negative: " + tradeVolume);
        }

        // Fee = flatFee + (tradeVolume * percentageFee)
        // Note: percentageFee is already in decimal format (e.g., 0.001 = 0.1%)
        BigDecimal percentageFeeComponent = tradeVolume.multiply(percentageFee);

        BigDecimal totalFee = flatFee.add(percentageFeeComponent);

        // Round to 2 decimal places (standard currency precision)
        return totalFee.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public String getAssetType() {
        return assetType;
    }

    public BigDecimal getFlatFee() {
        return flatFee;
    }

    public BigDecimal getPercentageFee() {
        return percentageFee;
    }

    @Override
    public String toString() {
        return "AssetFeeStructure{" +
                "assetType='" + assetType + '\'' +
                ", flatFee=" + flatFee +
                ", percentageFee=" + percentageFee +
                '}';
    }
}
