package com.tradingplatform.api.analytics;

import com.tradingplatform.api.analytics.model.*;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;

/**
 * Load phase: writes dimensions and facts to DuckDB.
 */
@Component
public class AnalyticsLoad {

    private static final String SCHEMA_SQL_RESOURCE = "/analytics-schema.sql";

    private final AnalyticsProperties properties;

    public AnalyticsLoad(AnalyticsProperties properties) {
        this.properties = properties;
    }

    /**
     * Get DuckDB connection.
     */
    private Connection getDuckDbConnection() throws SQLException {
        try {
            Class.forName("org.duckdb.DuckDBDriver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("DuckDB driver not found on classpath", e);
        }
        return DriverManager.getConnection("jdbc:duckdb:" + properties.getDuckdbPath());
    }

    /**
     * Ensure schema exists; load from analytics-schema.sql if tables missing.
     */
    public void ensureSchema() throws SQLException, IOException {
        try (Connection conn = getDuckDbConnection()) {
            // Check if tables exist
            DatabaseMetaData meta = conn.getMetaData();
            ResultSet tables = meta.getTables(null, null, "fact_trades", new String[]{"TABLE"});
            
            if (!tables.next()) {
                // Tables don't exist; load schema.
                // Read via stream, not Paths.get(url.getPath()) — the latter
                // breaks once this resource is packaged inside a jar, since
                // its URL is jar:file:...!/analytics-schema.sql rather than a
                // real filesystem path.
                String schema;
                try (java.io.InputStream in = AnalyticsLoad.class.getClassLoader()
                        .getResourceAsStream("analytics-schema.sql")) {
                    if (in == null) {
                        throw new IOException(
                            "analytics-schema.sql not found on classpath — " +
                            "expected at src/main/resources/analytics-schema.sql");
                    }
                    schema = new String(in.readAllBytes());
                }

                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(schema);
                }
            }
        }
    }

    /**
     * Load dim_date: insert if not already populated.
     */
    public int loadDimDate(List<AnalyticsDimDate> dates) throws SQLException {
        // dim_date has no loaded_at column in analytics-schema.sql (unlike
        // dim_instrument, dim_account and fact_trades, which all do). The
        // AnalyticsDimDate model still carries loadedAt for in-memory use,
        // it's just not persisted here — the schema is binding, not this insert.
        String sql = """
            INSERT INTO dim_date
                (date_key, full_date, day, month, year, quarter, day_of_week, day_name, month_name, is_weekday)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """;

        try (Connection conn = getDuckDbConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            int count = 0;
            for (AnalyticsDimDate date : dates) {
                stmt.setInt(1, date.dateKey());
                stmt.setObject(2, date.fullDate());
                stmt.setInt(3, date.day());
                stmt.setInt(4, date.month());
                stmt.setInt(5, date.year());
                stmt.setInt(6, date.quarter());
                stmt.setInt(7, date.dayOfWeek());
                stmt.setString(8, date.dayName());
                stmt.setString(9, date.monthName());
                stmt.setBoolean(10, date.isWeekday());
                stmt.addBatch();
            }
            
            int[] results = stmt.executeBatch();
            return (int) Arrays.stream(results).filter(r -> r > 0).count();
        }
    }

    /**
     * Load dim_instrument: UPSERT on symbol (Type 1).
     */
    public int loadDimInstrument(List<AnalyticsDimInstrument> instruments) throws SQLException {
        String sql = """
            INSERT INTO dim_instrument
                (instrument_key, symbol, name, asset_class, currency, exchange, tradable, loaded_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (symbol) DO UPDATE SET
                name = EXCLUDED.name,
                asset_class = EXCLUDED.asset_class,
                currency = EXCLUDED.currency,
                exchange = EXCLUDED.exchange,
                tradable = EXCLUDED.tradable,
                loaded_at = EXCLUDED.loaded_at
            """;

        try (Connection conn = getDuckDbConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            for (AnalyticsDimInstrument instr : instruments) {
                stmt.setLong(1, instr.instrumentKey());
                stmt.setString(2, instr.symbol());
                stmt.setString(3, instr.name());
                stmt.setString(4, instr.assetClass());
                stmt.setString(5, instr.currency());
                stmt.setString(6, instr.exchange());
                stmt.setBoolean(7, instr.tradable());
                stmt.setTimestamp(8, Timestamp.from(instr.loadedAt()));
                stmt.addBatch();
            }
            
            int[] results = stmt.executeBatch();
            return (int) Arrays.stream(results).filter(r -> r > 0).count();
        }
    }

    /**
     * Load dim_account: Type 2 (versions).
     */
    public int loadDimAccount(List<AnalyticsDimAccount> accounts) throws SQLException {
        String sql = """
            INSERT INTO dim_account
                (account_key, account_id, holder_name, status, effective_date, end_date, is_current, source_id, loaded_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = getDuckDbConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            for (AnalyticsDimAccount account : accounts) {
                stmt.setLong(1, account.accountKey());
                stmt.setString(2, account.accountId());
                stmt.setString(3, account.holderName());
                stmt.setString(4, account.status());
                stmt.setObject(5, account.effectiveDate());
                stmt.setObject(6, account.endDate());
                stmt.setBoolean(7, account.isCurrent());
                // sourceId is intentionally null in AnalyticsTransform.transformDimAccount()
                // ("populated later if needed") — setLong would NPE unboxing a null Long.
                if (account.sourceId() != null) {
                    stmt.setLong(8, account.sourceId());
                } else {
                    stmt.setNull(8, Types.BIGINT);
                }
                stmt.setTimestamp(9, Timestamp.from(account.loadedAt()));
                stmt.addBatch();
            }
            
            int[] results = stmt.executeBatch();
            return (int) Arrays.stream(results).filter(r -> r > 0).count();
        }
    }

    /**
     * Load fact_trades: INSERT ... ON CONFLICT (source_order_id) DO UPDATE (idempotent).
     */
    public int loadFactTrades(List<AnalyticsFactTrade> trades) throws SQLException {
        String sql = """
            INSERT INTO fact_trades
                (trade_key, account_key, instrument_key, date_key, side, quantity, price, status,
                 executed_price, trade_value, source_order_id, created_at, loaded_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (source_order_id) DO UPDATE SET
                status = EXCLUDED.status,
                executed_price = EXCLUDED.executed_price,
                trade_value = EXCLUDED.trade_value,
                loaded_at = EXCLUDED.loaded_at
            """;

        try (Connection conn = getDuckDbConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            for (AnalyticsFactTrade trade : trades) {
                stmt.setLong(1, trade.tradeKey());
                stmt.setLong(2, trade.accountKey());
                stmt.setLong(3, trade.instrumentKey());
                stmt.setInt(4, trade.dateKey());
                stmt.setString(5, trade.side());
                stmt.setInt(6, trade.quantity());
                stmt.setBigDecimal(7, trade.price());
                stmt.setString(8, trade.status());
                stmt.setBigDecimal(9, trade.executedPrice());
                stmt.setBigDecimal(10, trade.tradeValue());
                stmt.setString(11, trade.sourceOrderId());
                stmt.setTimestamp(12, Timestamp.from(trade.createdAt()));
                stmt.setTimestamp(13, Timestamp.from(trade.loadedAt()));
                stmt.addBatch();
            }
            
            int[] results = stmt.executeBatch();
            return (int) Arrays.stream(results).filter(r -> r > 0).count();
        }
    }

    /**
     * Get instrument key map for transform phase.
     */
    public Map<String, Long> getInstrumentKeyMap() throws SQLException {
        Map<String, Long> map = new HashMap<>();
        String sql = "SELECT symbol, instrument_key FROM dim_instrument";

        try (Connection conn = getDuckDbConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                map.put(rs.getString("symbol"), rs.getLong("instrument_key"));
            }
        }

        return map;
    }

    /**
     * Get account key map for transform phase (current versions only).
     */
    public Map<String, Long> getAccountKeyMap() throws SQLException {
        Map<String, Long> map = new HashMap<>();
        String sql = "SELECT account_id, account_key FROM dim_account WHERE is_current = TRUE";

        try (Connection conn = getDuckDbConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                map.put(rs.getString("account_id"), rs.getLong("account_key"));
            }
        }

        return map;
    }

    /**
     * Get next trade key.
     */
    public long getNextTradeKey() throws SQLException {
        String sql = "SELECT COALESCE(MAX(trade_key), 0) + 1 as next_key FROM fact_trades";

        try (Connection conn = getDuckDbConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                return rs.getLong("next_key");
            }
        }

        return 1;
    }

    /**
     * Get next instrument key.
     */
    public long getNextInstrumentKey() throws SQLException {
        String sql = "SELECT COALESCE(MAX(instrument_key), 0) + 1 as next_key FROM dim_instrument";

        try (Connection conn = getDuckDbConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                return rs.getLong("next_key");
            }
        }

        return 1;
    }

    /**
     * Get next account key.
     */
    public long getNextAccountKey() throws SQLException {
        String sql = "SELECT COALESCE(MAX(account_key), 0) + 1 as next_key FROM dim_account";

        try (Connection conn = getDuckDbConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                return rs.getLong("next_key");
            }
        }

        return 1;
    }

    /**
     * Check if dim_date is already populated.
     */
    public boolean isDimDatePopulated() throws SQLException {
        String sql = "SELECT COUNT(*) as cnt FROM dim_date";

        try (Connection conn = getDuckDbConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                return rs.getInt("cnt") > 0;
            }
        }

        return false;
    }
}