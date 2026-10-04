package com.tradingplatform.services;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.tradingplatform.mappers.PositionMapper;

/**
 * Service to discover which symbols should be polled for market data.
 * Currently, symbols are those with active positions (non-zero quantity held).
 * In the future, this can be extended to include watchlist symbols.
 */
@Service
public class SymbolDiscoveryService {
    private static final Logger logger = LoggerFactory.getLogger(SymbolDiscoveryService.class);

    private final PositionMapper positionMapper;

    public SymbolDiscoveryService(PositionMapper positionMapper) {
        this.positionMapper = positionMapper;
    }

    /**
     * Get the set of symbols that should be polled for market data.
     * Currently returns symbols from active positions (quantity > 0).
     * 
     * @return Set of distinct symbol strings to poll (may be empty if no active positions)
     */
    public Set<String> getActiveSymbols() {
        try {
            List<String> symbols = positionMapper.getDistinctSymbolsWithQuantity();
            Set<String> result = new HashSet<>(symbols);
            
            if (logger.isDebugEnabled()) {
                if (result.isEmpty()) {
                    logger.info("No active positions found for symbol discovery");
                } else {
                    logger.info("Discovered {} active symbols: {}", result.size(), result);
                }
            }
            
            return result;
        } catch (Exception e) {
            logger.warn("Error discovering active symbols, returning empty set: {}", e.getMessage());
            return new HashSet<>();
        }
    }
}
