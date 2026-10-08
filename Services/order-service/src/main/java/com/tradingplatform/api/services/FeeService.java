package com.tradingplatform.api.services;

import com.tradingplatform.api.mappers.AssetFeeMapper;
import com.tradingplatform.domain.dto.FeeStructureResponse;
import com.tradingplatform.domain.dto.FeeUpdateRequest;
import com.tradingplatform.domain.exception.AssetTypeNotFoundException;
import com.tradingplatform.domain.model.AssetFeeStructure;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for managing asset fee structures.
 * Provides fee information for trading different asset classes.
 */
@Service
public class FeeService {
    private final AssetFeeMapper assetFeeMapper;

    public FeeService(AssetFeeMapper assetFeeMapper) {
        this.assetFeeMapper = assetFeeMapper;
    }

    /**
     * Get all asset fee structures.
     * 
     * @return List of fee structures for all asset classes
     */
    public List<FeeStructureResponse> getAllFees() {
        return assetFeeMapper.findAll()
            .stream()
            .map(this::toResponse)
            .toList();
    }

    /**
     * Get fee structure for a specific asset type.
     * 
     * @param assetType The asset type (EQUITY, FX, CRYPTO)
     * @return Fee structure for the asset type
     */
    public FeeStructureResponse getFeeByAssetType(String assetType) {
        AssetFeeStructure structure = assetFeeMapper.findByAssetType(assetType);
        if (structure == null) {
            throw new AssetTypeNotFoundException(assetType);
        }
        return toResponse(structure);
    }

    /**
     * Update the fee structure for an asset type (admin only).
     * Orders placed after this call are charged the new fees.
     *
     * @param assetType The asset type (EQUITY, FX, CRYPTO)
     * @param request   The new flat and percentage fees
     * @return The updated fee structure, as now stored
     */
    @Transactional
    public FeeStructureResponse updateFee(String assetType, FeeUpdateRequest request) {
        int updated = assetFeeMapper.updateFees(assetType, request.flatFee(), request.percentageFee());
        if (updated == 0) {
            throw new AssetTypeNotFoundException(assetType);
        }
        return getFeeByAssetType(assetType);
    }

    /**
     * Convert AssetFeeStructure to response DTO.
     */
    private FeeStructureResponse toResponse(AssetFeeStructure structure) {
        return new FeeStructureResponse(
            structure.getAssetType(),
            structure.getFlatFee(),
            structure.getPercentageFee()
        );
    }
}
