package com.tradingplatform.quote;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.tradingplatform.quote.model.Quote;

/**
 * Instrument-to-account FX rates for settlement, fetched fresh from Fauxnance at fill time.
 *
 * A rate is "account-currency units per one instrument-currency unit". Lookup order:
 * same currency gives 1; otherwise FX:{from}{to} (its price) and FX:{to}{from} (one over its price),
 * both requested in one batched call. Any ISO 4217 code works; nothing is hard-coded per currency.
 */
@Service
public class FxRateService {
    private static final Logger logger = LoggerFactory.getLogger(FxRateService.class);
    static final int RATE_SCALE = 10;

    private final QuoteClient quoteClient;

    public FxRateService(QuoteClient quoteClient) {
        this.quoteClient = quoteClient;
    }

    /**
     * Fresh rate converting {@code from} into {@code to}.
     *
     * @throws PricingUnavailableException if Fauxnance is down or serves neither pair, so the order
     *         takes the same retry path as a missing instrument price
     */
    public BigDecimal rate(String from, String to) throws PricingUnavailableException {
        String source = normalise(from);
        String target = normalise(to);
        if (source.equals(target)) {
            return BigDecimal.ONE;
        }
        List<String> symbols = List.of(directSymbol(source, target), directSymbol(target, source));
        Map<String, Quote> quotes = quoteClient.getLatestPrices(symbols).stream()
            .filter(q -> q.getSymbol() != null)
            .collect(Collectors.toMap(q -> q.getSymbol().toUpperCase(Locale.ROOT), Function.identity(), (a, b) -> a));

        BigDecimal direct = price(quotes.get(directSymbol(source, target)));
        if (direct != null) {
            return direct.setScale(RATE_SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal inverse = price(quotes.get(directSymbol(target, source)));
        if (inverse != null) {
            return BigDecimal.ONE.divide(inverse, RATE_SCALE, RoundingMode.HALF_UP);
        }
        logger.warn("No FX rate available for {}->{} (tried {})", source, target, symbols);
        throw new PricingUnavailableException("FX rate unavailable for " + source + "->" + target);
    }

    static String directSymbol(String from, String to) {
        return "FX:" + from + to;
    }

    private static BigDecimal price(Quote quote) {
        if (quote == null || quote.getPrice() == null || quote.getPrice().signum() <= 0) {
            return null;
        }
        return quote.getPrice();
    }

    private static String normalise(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }
}
