package com.tradingplatform.domain.dto;

import java.util.List;

public record CandleHistoryResponse(
    String symbol,
    String name,
    String assetClass,
    List<CandleResponse> candles,
    int totalCount,
    boolean hasMore
) {}
