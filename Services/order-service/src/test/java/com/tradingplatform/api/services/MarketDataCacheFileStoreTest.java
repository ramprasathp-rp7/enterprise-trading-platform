package com.tradingplatform.api.services;

import com.tradingplatform.api.events.QuotePayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MarketDataCacheFileStore Tests")
class MarketDataCacheFileStoreTest {

    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");

    @TempDir
    Path dir;

    private MarketDataCache newCache() {
        return new MarketDataCache(Duration.ofHours(24), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static QuotePayload quote(String symbol, String price) {
        return new QuotePayload(symbol, new BigDecimal(price), new BigDecimal(price), new BigDecimal(price),
            "USD", null, null, null, "REGULAR", false, NOW.minusSeconds(5));
    }

    private Path file() {
        return dir.resolve("nested/market-data-cache.json");
    }

    @Test
    @DisplayName("Quotes saved by one cache are restored into a fresh cache, in order")
    void savesAndRestoresAcrossRestart() {
        MarketDataCache before = newCache();
        before.updateQuote("AAPL", quote("AAPL", "100.00"), NOW.minusSeconds(120));
        before.updateQuote("AAPL", quote("AAPL", "101.50"), NOW.minusSeconds(60));
        before.updateQuote("MSFT", quote("MSFT", "400.25"), NOW.minusSeconds(30));
        new MarketDataCacheFileStore(before, file().toString(), false).saveOnShutdown();

        MarketDataCache after = newCache();
        new MarketDataCacheFileStore(after, file().toString(), false).load();

        assertEquals(2, after.getCacheSize());
        assertEquals(new BigDecimal("101.50"), after.getLatestPrice("AAPL").orElseThrow());
        assertEquals(NOW.minusSeconds(60), after.getQuoteTimestamp("AAPL").orElseThrow());
        assertEquals(List.of(new BigDecimal("100.00"), new BigDecimal("101.50")),
            after.getQuoteHistory("AAPL").stream().map(q -> q.quote().price()).toList());
        assertEquals(quote("MSFT", "400.25"), after.getLatestQuote("MSFT").orElseThrow());
    }

    @Test
    @DisplayName("Wipe on startup deletes the file and leaves the cache empty")
    void wipeOnStartupDeletesFile() {
        MarketDataCache before = newCache();
        before.updateQuote("AAPL", quote("AAPL", "100.00"), NOW);
        new MarketDataCacheFileStore(before, file().toString(), false).save();
        assertTrue(Files.exists(file()));

        MarketDataCache after = newCache();
        new MarketDataCacheFileStore(after, file().toString(), true).load();

        assertEquals(0, after.getCacheSize());
        assertFalse(Files.exists(file()));
    }

    @Test
    @DisplayName("Missing file starts an empty cache")
    void missingFileStartsEmpty() {
        MarketDataCache cache = newCache();
        new MarketDataCacheFileStore(cache, file().toString(), false).load();
        assertEquals(0, cache.getCacheSize());
    }

    @Test
    @DisplayName("Corrupt file is ignored rather than failing startup")
    void corruptFileIsIgnored() throws IOException {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), "{ not json");

        MarketDataCache cache = newCache();
        assertDoesNotThrow(() -> new MarketDataCacheFileStore(cache, file().toString(), false).load());
        assertEquals(0, cache.getCacheSize());
    }

    @Test
    @DisplayName("Quotes outside the history window are trimmed on restore, newest kept")
    void restoreTrimsToHistoryWindow() {
        MarketDataCache before = newCache();
        before.updateQuote("AAPL", quote("AAPL", "90.00"), NOW.minus(Duration.ofHours(30)));
        before.updateQuote("AAPL", quote("AAPL", "95.00"), NOW.minus(Duration.ofHours(26)));
        new MarketDataCacheFileStore(before, file().toString(), false).save();

        MarketDataCache after = newCache();
        new MarketDataCacheFileStore(after, file().toString(), false).load();

        assertEquals(1, after.getQuoteHistory("AAPL").size());
        assertEquals(new BigDecimal("95.00"), after.getLatestPrice("AAPL").orElseThrow());
    }

    @Test
    @DisplayName("Unchanged cache is not rewritten")
    void unchangedCacheIsNotRewritten() throws IOException {
        MarketDataCache cache = newCache();
        cache.updateQuote("AAPL", quote("AAPL", "100.00"), NOW);
        MarketDataCacheFileStore store = new MarketDataCacheFileStore(cache, file().toString(), false);
        store.save();
        Files.writeString(file(), "sentinel");

        store.save();

        assertEquals("sentinel", Files.readString(file()));
    }
}
