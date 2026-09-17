package com.tradingplatform.domain.model;

import java.math.BigDecimal;

/**
 * Interface for calculating transaction fees.
 * Implementations provide fee calculation logic based on trade volume and asset type.
 */
public interface Feeable {

    /**
     * Calculates the total transaction fee.
     *
     * @param tradeVolume The notional value of the trade (price * quantity)
     * @return The calculated fee as a BigDecimal
     */
    BigDecimal calculateFee(BigDecimal tradeVolume);

    /**
     * Returns the asset type this fee structure applies to (EQUITY, FX, CRYPTO, etc.)
     *
     * @return The asset type
     */
    String getAssetType();
}
