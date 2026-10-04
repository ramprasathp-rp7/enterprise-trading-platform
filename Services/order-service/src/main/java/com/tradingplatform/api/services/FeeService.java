package com.tradingplatform.api.services;

import com.tradingplatform.api.mappers.AssetFeeMapper;
import com.tradingplatform.domain.dto.FeeStructureResponse;
import com.tradingplatform.domain.model.AssetFeeStructure;
import org.springframework.stereotype.Service;

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
            throw new IllegalArgumentException("Asset type not found: " + assetType);
        }
        return toResponse(structure);
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
