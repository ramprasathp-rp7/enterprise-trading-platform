package com.tradingplatform.api.analytics;

import com.tradingplatform.api.analytics.model.AnalyticsOrderDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Extract phase: reads orders and dimensions from Postgres.
 */
@Component
public class AnalyticsExtract {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Extract orders created since the given timestamp.
     * If since is null, returns all orders (first run).
     */
    public List<AnalyticsOrderDTO> extractOrders(Instant since) {
        String sql = """
            SELECT o.id, a.id as account_id, a.account_id as account_id_str, a.holder_name, 
                   i.symbol, o.side, o.quantity, o.price, o.executed_price, o.status, o.created_on
              FROM orders o
              JOIN accounts a ON a.id = o.account_id
              JOIN instruments i ON i.id = o.instrument_id
            """ + (since != null ? "WHERE o.created_on >= ? " : "") + """
            ORDER BY o.created_on
            """;

        RowMapper<AnalyticsOrderDTO> mapper = (rs, rowNum) -> {
            return new AnalyticsOrderDTO(
                (UUID) rs.getObject("id"),
                rs.getLong("account_id"),
                rs.getString("account_id_str"),
                rs.getString("holder_name"),
                rs.getString("symbol"),
                rs.getString("side"),
                rs.getInt("quantity"),
                rs.getBigDecimal("price"),
                rs.getBigDecimal("executed_price"),
                rs.getString("status"),
                rs.getTimestamp("created_on").toInstant()
            );
        };

        return since != null
            ? jdbcTemplate.query(sql, mapper, Timestamp.from(since))
            : jdbcTemplate.query(sql, mapper);
    }

    /**
     * Extract all instruments for dimension loading.
     */
    public List<String[]> extractDimInstruments() {
        String sql = "SELECT id, symbol, name, asset_class, currency, tradable FROM instruments ORDER BY symbol";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new String[]{
            rs.getString("symbol"),
            rs.getString("name"),
            rs.getString("asset_class"),
            rs.getString("currency"),
            String.valueOf(rs.getBoolean("tradable"))
        });
    }

    /**
     * Extract all accounts for dimension loading.
     */
    public List<String[]> extractDimAccounts() {
        String sql = "SELECT id, account_id, holder_name, status, created_on FROM accounts ORDER BY account_id";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new String[]{
            rs.getString("account_id"),
            rs.getString("holder_name"),
            rs.getString("status"),
            rs.getTimestamp("created_on").toString(),
            rs.getString("id") // Postgres surrogate key -> dim_account.source_id (NOT NULL)
        });
    }

    /**
     * Get the last watermark (last processed created_on timestamp).
     */
    public Instant getLastWatermark() {
        String sql = "SELECT last_processed_created_on FROM analytics_watermarks WHERE component = 'fact_trades'";
        try {
            Timestamp ts = jdbcTemplate.queryForObject(sql, Timestamp.class);
            return ts != null ? ts.toInstant() : null;
        } catch (Exception e) {
            // Table may not exist yet or no row
            return null;
        }
    }
}
