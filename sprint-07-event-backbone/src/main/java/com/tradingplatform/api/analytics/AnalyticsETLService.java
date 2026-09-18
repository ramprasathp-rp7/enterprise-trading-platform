package com.tradingplatform.api.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tradingplatform.api.analytics.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Analytics ETL Service: orchestrates extract → transform → load.
 */
@Service
public class AnalyticsETLService {

    @Autowired
    private AnalyticsExtract extract;

    @Autowired
    private AnalyticsTransform transform;

    @Autowired
    private AnalyticsLoad load;

    @Autowired
    private AnalyticsProperties properties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    /**
     * Run incremental load: extract → transform → load.
     * Returns AnalyticsLoadResult with counts and batch ID.
     */
    public AnalyticsLoadResult runLoad() throws SQLException, IOException {
        Instant startTime = Instant.now();
        String batchId = UUID.randomUUID().toString();
        
        int dimDateLoaded = 0;
        int dimInstrumentLoaded = 0;
        int dimAccountLoaded = 0;
        int factTradesLoaded = 0;
        int deadLettered = 0;

        try {
            // Step 1: Ensure schema exists
            load.ensureSchema();

            // Step 2: Load dim_date (once, covering wide range)
            if (!load.isDimDatePopulated()) {
                LocalDate start = LocalDate.of(2016, 1, 1);
                LocalDate end = LocalDate.of(2035, 12, 31);
                List<AnalyticsDimDate> dateDim = transform.transformDimDateBatch(start, end);
                dimDateLoaded = load.loadDimDate(dateDim);
            }

            // Step 3: Load dim_instrument
            List<String[]> rawInstruments = extract.extractDimInstruments();
            List<AnalyticsDimInstrument> instrumentDim = new ArrayList<>();
            long nextInstrumentKey = load.getNextInstrumentKey();
            for (String[] raw : rawInstruments) {
                instrumentDim.add(transform.transformDimInstrument(raw, nextInstrumentKey++));
            }
            dimInstrumentLoaded = load.loadDimInstrument(instrumentDim);

            // Step 4: Load dim_account
            List<String[]> rawAccounts = extract.extractDimAccounts();
            List<AnalyticsDimAccount> accountDim = new ArrayList<>();
            long nextAccountKey = load.getNextAccountKey();
            LocalDate today = LocalDate.now(ZoneId.of("UTC"));
            for (String[] raw : rawAccounts) {
                accountDim.add(transform.transformDimAccount(raw, nextAccountKey++, today));
            }
            dimAccountLoaded = load.loadDimAccount(accountDim);

            // Step 5: Get watermark and extract orders
            Instant watermark = extract.getLastWatermark();
            List<AnalyticsOrderDTO> orders = extract.extractOrders(watermark);

            // Step 6: Get dimension key maps for transform
            Map<String, Long> instrumentKeyMap = load.getInstrumentKeyMap();
            Map<String, Long> accountKeyMap = load.getAccountKeyMap();

            // Step 7: Transform and load fact trades (with quality checks)
            List<AnalyticsFactTrade> validTrades = new ArrayList<>();
            long nextTradeKey = load.getNextTradeKey();

            for (AnalyticsOrderDTO order : orders) {
                try {
                    // Compute date_key from order creation timestamp
                    LocalDate orderDate = order.createdOn().atZone(ZoneId.of("UTC")).toLocalDate();
                    Integer dateKey = orderDate.getYear() * 10000 + 
                                      orderDate.getMonthValue() * 100 + 
                                      orderDate.getDayOfMonth();

                    // Transform with all 7 quality checks
                    AnalyticsFactTrade trade = transform.transformFactTrade(
                        order,
                        instrumentKeyMap,
                        accountKeyMap,
                        nextTradeKey++,
                        dateKey
                    );
                    validTrades.add(trade);
                } catch (AnalyticsQualityException ex) {
                    // Dead-letter the row
                    deadLetterRow(batchId, order, ex);
                    deadLettered++;
                }
            }

            // Load valid trades
            if (!validTrades.isEmpty()) {
                factTradesLoaded = load.loadFactTrades(validTrades);
            }

            // Step 8: Update watermark
            if (!orders.isEmpty()) {
                Instant maxCreatedOn = orders.stream()
                    .map(AnalyticsOrderDTO::createdOn)
                    .max(Comparator.naturalOrder())
                    .orElse(Instant.now());
                updateWatermark(maxCreatedOn, UUID.fromString(batchId));
            }

        } catch (Exception e) {
            System.err.println("Analytics ETL error: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }

        Instant endTime = Instant.now();
        return new AnalyticsLoadResult(
            startTime,
            endTime,
            dimDateLoaded,
            dimInstrumentLoaded,
            dimAccountLoaded,
            factTradesLoaded,
            deadLettered,
            batchId
        );
    }

    /**
     * Dead-letter a failed row: write to .dlq file (JSON lines).
     */
    private void deadLetterRow(String batchId, AnalyticsOrderDTO order, AnalyticsQualityException ex) {
        try (FileWriter fw = new FileWriter(
            new File(properties.getDeadLetterLogPath()),
            true  // append
        )) {
            Map<String, Object> dlq = new LinkedHashMap<>();
            dlq.put("batchId", batchId);
            dlq.put("sourceOrderId", order.orderId().toString());
            dlq.put("reason", ex.getMessage());
            dlq.put("reasonCode", ex.getReasonCode());
            dlq.put("timestamp", Instant.now().toString());
            dlq.put("orderJson", objectMapper.writeValueAsString(order));

            fw.write(objectMapper.writeValueAsString(dlq));
            fw.write("\n");
        } catch (IOException ioEx) {
            System.err.println("Failed to write dead letter: " + ioEx.getMessage());
        }
    }

    /**
     * Update watermark in Postgres (analytics_watermarks table).
     * Uses UPSERT: insert if not exists, update if exists.
     */
    private void updateWatermark(Instant lastProcessedCreatedOn, UUID batchId) {
        try {
            String sql = """
                INSERT INTO analytics_watermarks 
                    (component, last_processed_created_on, batch_id, last_run_at, updated_at)
                VALUES ('fact_trades', ?, ?, now(), now())
                ON CONFLICT (component) DO UPDATE
                SET last_processed_created_on = ?, batch_id = ?, last_run_at = now(), updated_at = now()
                """;

            jdbcTemplate.update(sql,
                Timestamp.from(lastProcessedCreatedOn),
                batchId.toString(),
                Timestamp.from(lastProcessedCreatedOn),
                batchId.toString()
            );

            System.out.println("Watermark persisted: " + lastProcessedCreatedOn + ", batch: " + batchId);
        } catch (Exception e) {
            System.err.println("Failed to update watermark: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Watermark persistence failed", e);
        }
    }
}
