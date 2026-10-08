package com.tradingplatform.api.mappers;

import com.tradingplatform.domain.model.AssetFeeStructure;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

/**
 * Mapper interface for AssetFeeStructure persistence.
 * Handles loading fee structures from database by asset type.
 * 
 * Fee Scenarios:
 * - Flat only: flat_fee > 0, percentage_fee = 0
 * - Percentage only: flat_fee = 0, percentage_fee > 0
 * - Combination: flat_fee > 0, percentage_fee > 0
 * 
 * Fee Calculation: total_fee = flat_fee + (notional * percentage_fee / 100)
 */
public interface AssetFeeMapper {

    /**
     * Finds an AssetFeeStructure by asset type.
     *
     * @param assetType The asset type (EQUITY, FX, CRYPTO)
     * @return The AssetFeeStructure for the given asset type, or null if not found
     */
    @ConstructorArgs({
        @Arg(column = "asset_type", javaType = String.class),
        @Arg(column = "flat_fee", javaType = BigDecimal.class),
        @Arg(column = "percentage_fee", javaType = BigDecimal.class)
    })
    @Select("SELECT asset_type, flat_fee, percentage_fee FROM assets WHERE asset_type = #{assetType}")
    AssetFeeStructure findByAssetType(@Param("assetType") String assetType);

    /**
     * Finds all AssetFeeStructures.
     *
     * @return List of all AssetFeeStructures ordered by asset type
     */
    @ConstructorArgs({
        @Arg(column = "asset_type", javaType = String.class),
        @Arg(column = "flat_fee", javaType = BigDecimal.class),
        @Arg(column = "percentage_fee", javaType = BigDecimal.class)
    })
    @Select("SELECT asset_type, flat_fee, percentage_fee FROM assets ORDER BY asset_type ASC")
    List<AssetFeeStructure> findAll();

    /**
     * Updates the fee structure for an asset type and stamps updated_on.
     * New orders pick the new fees up immediately, since fees are read per order.
     *
     * @return number of rows updated (0 when the asset type does not exist)
     */
    @Update("UPDATE assets SET flat_fee = #{flatFee}, percentage_fee = #{percentageFee}, updated_on = now() "
          + "WHERE asset_type = #{assetType}")
    int updateFees(@Param("assetType") String assetType,
                   @Param("flatFee") BigDecimal flatFee,
                   @Param("percentageFee") BigDecimal percentageFee);
}
