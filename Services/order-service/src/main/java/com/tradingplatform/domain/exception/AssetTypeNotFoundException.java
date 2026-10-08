package com.tradingplatform.domain.exception;

import com.tradingplatform.domain.enums.ErrorCode;

/** ASS-404: no fee structure exists for the requested asset type */
public final class AssetTypeNotFoundException extends DomainException {

    public AssetTypeNotFoundException(String assetType) {
        super(ErrorCode.ASS_404, "Asset type not found: " + assetType);
    }
}
