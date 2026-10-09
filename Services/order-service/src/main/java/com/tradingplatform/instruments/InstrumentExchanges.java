package com.tradingplatform.instruments;

import java.util.Locale;
import java.util.Set;

/**
 * The exchanges an instrument can trade on, and the rules for symbols whose row has none.
 */
public final class InstrumentExchanges {

    /** The values instruments.exchange accepts (ck_instruments_exchange) */
    public static final Set<String> KNOWN = Set.of("NYSE", "NASDAQ", "NSE", "BSE", "FX", "CRYPTO");

    private InstrumentExchanges() {
    }

    /**
     * The exchange for a symbol with none stored, using the same rules as the backfill in
     * migration 026: FX: pairs, X: crypto, .NS for NSE, .BO for BSE, NYSE for the two seeded
     * NYSE listings, NASDAQ otherwise.
     */
    public static String derive(String symbol) {
        String s = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        if (s.startsWith("FX:")) {
            return "FX";
        }
        if (s.startsWith("X:")) {
            return "CRYPTO";
        }
        if (s.endsWith(".NS")) {
            return "NSE";
        }
        if (s.endsWith(".BO")) {
            return "BSE";
        }
        if (s.equals("PFE") || s.equals("JPM")) {
            return "NYSE";
        }
        return "NASDAQ";
    }

    /** The stored exchange when it is a known one, otherwise the derived one */
    public static String resolve(String symbol, String stored) {
        if (stored != null && KNOWN.contains(stored.toUpperCase(Locale.ROOT))) {
            return stored.toUpperCase(Locale.ROOT);
        }
        return derive(symbol);
    }

    /**
     * Normalises an exchange from the Fauxnance registry. Unknown values fall back to the symbol
     * rules, so a new Fauxnance exchange cannot break the CHECK constraint.
     */
    public static String fromRegistry(String symbol, String registryExchange) {
        return resolve(symbol, registryExchange);
    }
}
