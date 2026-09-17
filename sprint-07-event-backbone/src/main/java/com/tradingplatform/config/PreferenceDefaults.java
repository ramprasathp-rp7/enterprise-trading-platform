package com.tradingplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring configuration for platform-wide default preferences.
 *
 * Loaded from application.properties (trading-platform.preferences.defaults.*).
 * These defaults are applied to all accounts unless they override specific preferences.
 * This approach allows environment-specific defaults (dev, staging, prod) by using
 * different .properties files without requiring database migrations.
 */
@Component
@ConfigurationProperties(prefix = "trading-platform.preferences")
public class PreferenceDefaults {
    private Map<String, Object> defaults = new HashMap<>();

    public Map<String, Object> getDefaults() {
        return defaults;
    }

    public void setDefaults(Map<String, Object> defaults) {
        this.defaults = defaults;
    }

    /**
     * Get a copy of the defaults map (to prevent modification of the original).
     */
    public Map<String, Object> getDefaultsCopy() {
        return new HashMap<>(defaults);
    }
}
