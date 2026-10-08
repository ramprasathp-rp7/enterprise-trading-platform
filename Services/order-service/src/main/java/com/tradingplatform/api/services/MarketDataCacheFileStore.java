package com.tradingplatform.api.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Keeps MarketDataCache across restarts by snapshotting it to a JSON file.
 *
 * Lifecycle:
 * - Startup: restores the file into the cache. Runs in @PostConstruct, before the scheduled
 *   Fauxnance refresh and the Kafka listener start writing, so restored quotes stay older than live ones.
 * - Every market-data.cache.persist-interval-ms (default one minute): saves the cache if it changed.
 * - Shutdown: saves once more, so a clean stop loses nothing (a crash loses at most one interval).
 *
 * Wiping: with market-data.cache.wipe-on-startup=true (env MARKET_DATA_CACHE_WIPE_ON_STARTUP)
 * the file is deleted on startup and the cache starts empty; saving then carries on as normal,
 * so set it back to false to keep quotes on the next restart.
 *
 * The file is written to a temp file and moved into place, so a crash mid-save never leaves a
 * half-written snapshot. An unreadable file is logged and ignored; the cache simply starts empty.
 *
 * Disable entirely with market-data.cache.persistence.enabled=false.
 */
@Component
@ConditionalOnProperty(name = "market-data.cache.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class MarketDataCacheFileStore {

    private static final Logger logger = LoggerFactory.getLogger(MarketDataCacheFileStore.class);

    private static final TypeReference<Map<String, List<MarketDataCache.CachedQuote>>> SNAPSHOT_TYPE =
        new TypeReference<>() {};

    private final MarketDataCache marketDataCache;
    private final Path file;
    private final boolean wipeOnStartup;
    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    /** Cache version last written to (or read from) the file; -1 forces the first save. */
    private long savedVersion = -1;

    public MarketDataCacheFileStore(
            MarketDataCache marketDataCache,
            @Value("${market-data.cache.file:data/market-data-cache.json}") String file,
            @Value("${market-data.cache.wipe-on-startup:false}") boolean wipeOnStartup) {
        this.marketDataCache = Objects.requireNonNull(marketDataCache, "marketDataCache cannot be null");
        this.file = Path.of(Objects.requireNonNull(file, "file cannot be null")).toAbsolutePath();
        this.wipeOnStartup = wipeOnStartup;
    }

    /** Restores the cache from the file, or deletes the file when wiping is enabled. */
    @PostConstruct
    public void load() {
        if (wipeOnStartup) {
            try {
                boolean deleted = Files.deleteIfExists(file);
                logger.warn("Market data cache wipe requested: {} {}", file, deleted ? "deleted" : "did not exist");
            } catch (IOException ex) {
                logger.error("Market data cache wipe failed: could not delete {}: {}", file, ex.getMessage());
            }
            return;
        }

        if (!Files.exists(file)) {
            logger.info("No market data cache file at {}; starting with an empty cache", file);
            return;
        }

        try {
            Map<String, List<MarketDataCache.CachedQuote>> saved = objectMapper.readValue(file.toFile(), SNAPSHOT_TYPE);
            int restored = marketDataCache.restore(saved);
            savedVersion = marketDataCache.version();
            logger.info("Restored {} quotes for {} symbols from {}", restored, marketDataCache.getCacheSize(), file);
        } catch (IOException | RuntimeException ex) {
            logger.error("Could not read market data cache file {}; starting with an empty cache: {}",
                file, ex.getMessage());
        }
    }

    /** Saves the cache if it changed since the last save. */
    @Scheduled(initialDelayString = "${market-data.cache.persist-interval-ms:60000}",
               fixedDelayString = "${market-data.cache.persist-interval-ms:60000}")
    public synchronized void save() {
        long version = marketDataCache.version();
        if (version == savedVersion) {
            return;
        }

        try {
            Path dir = file.getParent();
            if (dir != null) {
                Files.createDirectories(dir);
            }
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            objectMapper.writeValue(tmp.toFile(), marketDataCache.snapshot());
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            savedVersion = version;
            logger.info("Saved market data cache ({} symbols) to {}", marketDataCache.getCacheSize(), file);
        } catch (IOException ex) {
            logger.error("Could not save market data cache to {}: {}", file, ex.getMessage());
        }
    }

    /** Final save on shutdown. */
    @PreDestroy
    public void saveOnShutdown() {
        save();
    }

    Path file() {
        return file;
    }
}
