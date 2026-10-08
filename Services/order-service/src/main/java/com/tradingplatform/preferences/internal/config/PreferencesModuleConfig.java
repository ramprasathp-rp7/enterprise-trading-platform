package com.tradingplatform.preferences.internal.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the preferences module's mappers. Kept inside the module so the shared application
 * class does not need to know the module's package layout.
 */
@Configuration
@MapperScan("com.tradingplatform.preferences.internal.persistence")
public class PreferencesModuleConfig {
}
