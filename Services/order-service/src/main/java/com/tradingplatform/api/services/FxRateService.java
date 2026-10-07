package com.tradingplatform.api.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Instrument-to-account currency rates from Fauxnance FX: pairs.
 *
 * A rate is "to-currency units per one from-currency unit". Lookup order: the same currency gives 1;
 * otherwise FX:{from}{to} (its price), then FX:{to}{from} (one over its price). Any ISO 4217 code
 * works; nothing is hard-coded per currency. Callers that also need instrument quotes add
 * {@link #pairSymbols} to their own batch and convert with {@link #fromQuotes}, so the FX pair costs
 * no extra Fauxnance request.
 */
@Service
public class FxRateService {

    private static final Logger log = LoggerFactory.getLogger(FxRateService.class);
    static final int RATE_SCALE = 10;

    /** A rate with the time and staleness of the quote it came from. */
    public record FxRate(BigDecimal rate, Instant asOf, boolean stale) {
        public static final FxRate ONE = new FxRate(BigDecimal.ONE, null, false);
    }

    private final QuoteResolver quoteResolver;

    public FxRateService(QuoteResolver quoteResolver) {
        this.quoteResolver = quoteResolver;
    }

    /** Only converts a currency into itself; for callers built without a quote source. */
    public static FxRateService sameCurrencyOnly() {
        return new FxRateService(null);
    }

    /** Fresh rate converting {@code from} into {@code to}, or empty if Fauxnance and the cache have neither pair. */
    public Optional<FxRate> rate(String from, String to) {
        List<String> symbols = pairSymbols(from, to);
        if (symbols.isEmpty()) {
            return Optional.of(FxRate.ONE);
        }
        if (quoteResolver == null) {
            return Optional.empty();
        }
        return fromQuotes(from, to, quoteResolver.resolve(symbols));
    }

    /** The FX: symbols that can price {@code from} into {@code to}; empty when they are the same currency. */
    public static List<String> pairSymbols(String from, String to) {
        String source = normalise(from);
        String target = normalise(to);
        if (source.equals(target)) {
            return List.of();
        }
        return List.of("FX:" + source + target, "FX:" + target + source);
    }

    /** Converts already-resolved quotes into a rate (direct pair first, then the inverse). */
    public static Optional<FxRate> fromQuotes(String from, String to, Map<String, QuoteResolver.ResolvedQuote> quotes) {
        List<String> symbols = pairSymbols(from, to);
        if (symbols.isEmpty()) {
            return Optional.of(FxRate.ONE);
        }
        QuoteResolver.ResolvedQuote direct = quotes.get(symbols.get(0));
        if (usable(direct)) {
            return Optional.of(new FxRate(direct.quote().getPrice().setScale(RATE_SCALE, RoundingMode.HALF_UP),
                direct.quote().getQuoteAsOf(), direct.stale()));
        }
        QuoteResolver.ResolvedQuote inverse = quotes.get(symbols.get(1));
        if (usable(inverse)) {
            return Optional.of(new FxRate(BigDecimal.ONE.divide(inverse.quote().getPrice(), RATE_SCALE, RoundingMode.HALF_UP),
                inverse.quote().getQuoteAsOf(), inverse.stale()));
        }
        log.warn("No FX rate available for {}->{} (tried {})", normalise(from), normalise(to), symbols);
        return Optional.empty();
    }

    private static boolean usable(QuoteResolver.ResolvedQuote quote) {
        return quote != null && quote.quote() != null && quote.quote().getPrice() != null
            && quote.quote().getPrice().signum() > 0;
    }

    static String normalise(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }
}
