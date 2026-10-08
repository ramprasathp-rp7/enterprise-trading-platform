package com.tradingplatform.preferences.internal.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Base64;
import java.util.List;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.tradingplatform.api.services.AccountService;
import com.tradingplatform.preferences.AlertChannel;
import com.tradingplatform.preferences.PreferenceResolver;
import com.tradingplatform.preferences.AlertTarget;
import com.tradingplatform.preferences.ResolvedAlertChannels;
import com.tradingplatform.preferences.internal.crypto.ContactDetailCipher;
import com.tradingplatform.preferences.internal.service.AdminPreferencesService;
import com.tradingplatform.preferences.internal.service.AdminPreferencesView;
import com.tradingplatform.preferences.internal.service.AlertChannelEntry;
import com.tradingplatform.preferences.internal.service.DefaultPreferenceResolver;
import com.tradingplatform.preferences.internal.service.LastKnownChannels;
import com.tradingplatform.preferences.internal.service.PreferencesService;
import com.tradingplatform.preferences.internal.service.PreferencesView;

/**
 * Preferences survive a restart of the service.
 *
 * Starts the module's persistence slice (MyBatis mapper, cipher, service, resolver) on a file-based
 * H2 database built from the real migration 014, writes a preference, closes the context so every
 * connection and in-memory cache is gone, then starts a brand-new context on the same file and
 * reads it back. Nothing survives between the two contexts except the database file.
 */
class PreferencesPersistenceRestartTest {

    private static final Path MIGRATION =
            Path.of("../../Database/postgres/order-service/migrations/014_create_customer_preferences.sql");
    private static final Path ADMIN_MIGRATION =
            Path.of("../../Database/postgres/order-service/migrations/024_create_admin_preferences.sql");
    private static final String KEY = Base64.getEncoder().encodeToString(
            "restart-test-key-0123456789abcde".getBytes(StandardCharsets.US_ASCII));

    @TempDir
    Path dir;

    private String url;

    @BeforeEach
    void createSchema() throws Exception {
        url = "jdbc:h2:file:" + dir.resolve("prefs").toAbsolutePath() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
        assertTrue(Files.exists(MIGRATION), "migration 014 not found at " + MIGRATION.toAbsolutePath());
        try (Connection c = DriverManager.getConnection(url, "sa", "")) {
            try (Statement s = c.createStatement()) {
                // Stand-in for the shared accounts table the migration references.
                s.execute("CREATE TABLE accounts (id BIGINT PRIMARY KEY)");
                s.execute("INSERT INTO accounts (id) VALUES (2), (3)");
            }
            ScriptUtils.executeSqlScript(c, new FileSystemResource(MIGRATION));
            assertTrue(Files.exists(ADMIN_MIGRATION), "migration 024 not found at " + ADMIN_MIGRATION.toAbsolutePath());
            ScriptUtils.executeSqlScript(c, new FileSystemResource(ADMIN_MIGRATION));
        }
    }

    private static final AlertChannelEntry EMAIL = new AlertChannelEntry(AlertChannel.EMAIL, "customer@example.com");
    private static final AlertChannelEntry SMS = new AlertChannelEntry(AlertChannel.SMS, "+447700900123");
    private static final AlertChannelEntry IN_APP = new AlertChannelEntry(AlertChannel.IN_APP, null);

    @Test
    void preferencesSurviveARestartOfTheService() throws Exception {
        try (AnnotationConfigApplicationContext first = start()) {
            first.getBean(PreferencesService.class).replace(2L, List.of(EMAIL, SMS));
        }

        try (AnnotationConfigApplicationContext second = start()) {
            PreferencesView view = second.getBean(PreferencesService.class).get(2L);
            assertTrue(view.configured());
            assertEquals(List.of(
                    new AlertTarget(AlertChannel.EMAIL, "customer@example.com"),
                    new AlertTarget(AlertChannel.SMS, "+447700900123")), view.alertChannels());

            ResolvedAlertChannels resolved = second.getBean(PreferenceResolver.class).resolveAlertChannels(2L);
            assertEquals(List.of(AlertChannel.EMAIL, AlertChannel.SMS), resolved.channels());
            assertEquals(ResolvedAlertChannels.Source.CUSTOMER, resolved.source());
            assertFalse(resolved.stale(), "answer must come from the database, not a cache");

            assertFalse(second.getBean(PreferencesService.class).get(3L).configured());
        }
    }

    @Test
    void contactDetailsAreNotStoredInClear() throws Exception {
        try (AnnotationConfigApplicationContext ctx = start()) {
            ctx.getBean(PreferencesService.class).replace(2L, List.of(EMAIL, SMS));
        }

        try (Connection c = DriverManager.getConnection(url, "sa", "");
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT contact_detail_enc FROM customer_preferences WHERE account_id = 2")) {
            int rows = 0;
            while (rs.next()) {
                rows++;
                String raw = new String(rs.getBytes(1), StandardCharsets.ISO_8859_1);
                assertFalse(raw.contains("447700900123"));
                assertFalse(raw.contains("customer@example.com"));
            }
            assertEquals(2, rows);
        }
    }

    @Test
    void secondWriteReplacesTheAccountsRows() throws Exception {
        try (AnnotationConfigApplicationContext ctx = start()) {
            PreferencesService service = ctx.getBean(PreferencesService.class);
            service.replace(2L, List.of(EMAIL, SMS, IN_APP));
            service.replace(2L, List.of(IN_APP));
            assertEquals(List.of(AlertTarget.inApp()), service.get(2L).alertChannels());
        }

        try (Connection c = DriverManager.getConnection(url, "sa", "");
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM customer_preferences")) {
            rs.next();
            assertEquals(1, rs.getInt(1));
        }
    }

    @Test
    void adminPreferencesSurviveARestartAndStayEncrypted() throws Exception {
        String admin = "f47ac10b-58cc-4372-a567-0e02b2c3d479";
        try (AnnotationConfigApplicationContext first = start()) {
            first.getBean(AdminPreferencesService.class).replace(admin, List.of(EMAIL, IN_APP));
        }

        try (AnnotationConfigApplicationContext second = start()) {
            AdminPreferencesView view = second.getBean(AdminPreferencesService.class).get(admin);
            assertTrue(view.configured());
            assertEquals(List.of(new AlertTarget(AlertChannel.EMAIL, "customer@example.com"), AlertTarget.inApp()),
                    view.alertChannels());
            assertFalse(second.getBean(AdminPreferencesService.class).get("someone-else").configured());
        }

        try (Connection c = DriverManager.getConnection(url, "sa", "");
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT contact_detail_enc FROM admin_preferences WHERE alert_channel = 'EMAIL'")) {
            assertTrue(rs.next());
            assertFalse(new String(rs.getBytes(1), StandardCharsets.ISO_8859_1).contains("customer@example.com"));
        }
    }

    private AnnotationConfigApplicationContext start() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
                java.util.Map.of("test.db.url", url, "PREFERENCES_CONTACT_KEY", KEY)));
        ctx.register(PersistenceSlice.class);
        ctx.refresh();
        return ctx;
    }

    @Configuration
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = PreferencesMapper.class)
    static class PersistenceSlice {

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        DataSource dataSource(@Value("${test.db.url}") String url) {
            JdbcDataSource ds = new JdbcDataSource();
            ds.setURL(url);
            ds.setUser("sa");
            return ds;
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            org.apache.ibatis.session.Configuration config = new org.apache.ibatis.session.Configuration();
            config.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(config);
            return factory.getObject();
        }

        @Bean
        ContactDetailCipher contactDetailCipher(@Value("${PREFERENCES_CONTACT_KEY}") String key) {
            return new ContactDetailCipher(key);
        }

        @Bean
        AccountService accountService() {
            return Mockito.mock(AccountService.class);
        }

        @Bean
        LastKnownChannels lastKnownChannels() {
            return new LastKnownChannels();
        }

        @Bean
        PreferencesService preferencesService(PreferencesMapper mapper, ContactDetailCipher cipher,
                                              AccountService accountService, LastKnownChannels lastKnown) {
            return new PreferencesService(mapper, cipher, accountService, lastKnown);
        }

        @Bean
        AdminPreferencesService adminPreferencesService(AdminPreferencesMapper mapper, ContactDetailCipher cipher) {
            return new AdminPreferencesService(mapper, cipher);
        }

        @Bean
        DefaultPreferenceResolver preferenceResolver(PreferencesService service, LastKnownChannels lastKnown) {
            return new DefaultPreferenceResolver(service, lastKnown);
        }
    }
}
