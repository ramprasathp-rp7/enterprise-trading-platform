package com.tradingplatform.api.analytics;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for analytics ETL.
 */
@Component
@ConfigurationProperties(prefix = "analytics")
public class AnalyticsProperties {
    private String duckdbPath = "analytics_store.duckdb";
    private String watermarkTableName = "analytics_watermarks";
    private String deadLetterLogPath = "analytics_store.duckdb.dlq";

    public String getDuckdbPath() {
        return duckdbPath;
    }

    public void setDuckdbPath(String duckdbPath) {
        this.duckdbPath = duckdbPath;
    }

    public String getWatermarkTableName() {
        return watermarkTableName;
    }

    public void setWatermarkTableName(String watermarkTableName) {
        this.watermarkTableName = watermarkTableName;
    }

    public String getDeadLetterLogPath() {
        return deadLetterLogPath;
    }

    public void setDeadLetterLogPath(String deadLetterLogPath) {
        this.deadLetterLogPath = deadLetterLogPath;
    }
}
