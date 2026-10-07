package com.tradingplatform.services;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.tradingplatform.mappers.InstrumentMapper;

/**
 * Service to discover which symbols should be polled for market data.
 * Every symbol in the instruments table is polled, so held, watched and untraded instruments
 * all have a quote on the market-data topic (the order service caches them for its APIs).
 * The FX pairs that convert instrument currencies into account currencies are polled too.
 * Up to 25 instruments cost one Fauxnance request per poll.
 */
@Service
public class SymbolDiscoveryService {
    private static final Logger logger = LoggerFactory.getLogger(SymbolDiscoveryService.class);

    private final InstrumentMapper instrumentMapper;

    public SymbolDiscoveryService(InstrumentMapper instrumentMapper) {
        this.instrumentMapper = instrumentMapper;
    }

    /**
     * Get the set of symbols that should be polled for market data.
     * Returns every symbol in the instruments table.
     * 
     * @return Set of distinct symbol strings to poll (empty if there are no instruments or the lookup fails)
     */
    public Set<String> getActiveSymbols() {
        try {
            List<String> symbols = instrumentMapper.findAllSymbols();
            Set<String> result = new HashSet<>(symbols);
            // FX pairs for converting instrument currencies into account currencies (multi-currency)
            List<String> fxSymbols = instrumentMapper.findConversionFxSymbols();
            if (fxSymbols != null) {
                result.addAll(fxSymbols);
            }
            
            if (logger.isDebugEnabled()) {
                if (result.isEmpty()) {
                    logger.info("No instruments found for symbol discovery");
                } else {
                    logger.info("Discovered {} symbols to poll: {}", result.size(), result);
                }
            }
            
            return result;
        } catch (Exception e) {
            logger.warn("Error discovering active symbols, returning empty set: {}", e.getMessage());
            return new HashSet<>();
        }
    }
}
