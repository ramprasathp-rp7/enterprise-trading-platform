package com.tradingplatform.preferences.internal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tradingplatform.preferences.AlertChannel;
import com.tradingplatform.preferences.AlertTarget;
import com.tradingplatform.preferences.internal.crypto.ContactDetailCipher;
import com.tradingplatform.preferences.internal.persistence.AdminPreferenceRecord;
import com.tradingplatform.preferences.internal.persistence.FakeAdminPreferencesMapper;

class DefaultAdminPreferenceResolverTest {

    private static final String ADMIN = "admin-1";

    private FakeAdminPreferencesMapper mapper;
    private AdminPreferencesService service;
    private DefaultAdminPreferenceResolver resolver;

    @BeforeEach
    void setUp() {
        mapper = new FakeAdminPreferencesMapper();
        service = new AdminPreferencesService(mapper, new ContactDetailCipher("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="));
        resolver = new DefaultAdminPreferenceResolver(service);
    }

    @Test
    void nothingStored_resolvesToInAppOnly() {
        assertEquals(List.of(AlertTarget.inApp()), resolver.resolveAdminAlertTargets(ADMIN));
    }

    @Test
    void storedChannels_resolveWithDecryptedDestinations() {
        service.replace(ADMIN, List.of(
                new AlertChannelEntry(AlertChannel.SMS, "+447700900123"),
                new AlertChannelEntry(AlertChannel.EMAIL, "ops@example.com")));

        assertEquals(List.of(
                new AlertTarget(AlertChannel.EMAIL, "ops@example.com"),
                new AlertTarget(AlertChannel.SMS, "+447700900123")), resolver.resolveAdminAlertTargets(ADMIN));
    }

    @Test
    void unreadableStore_resolvesToInAppOnly() {
        service.replace(ADMIN, List.of(new AlertChannelEntry(AlertChannel.EMAIL, "ops@example.com")));
        mapper.setUnavailable(true);

        assertEquals(List.of(AlertTarget.inApp()), resolver.resolveAdminAlertTargets(ADMIN));
    }

    @Test
    void rowCopiedToAnotherAdmin_doesNotDecryptAndFallsBackToInApp() {
        service.replace(ADMIN, List.of(new AlertChannelEntry(AlertChannel.EMAIL, "ops@example.com")));
        AdminPreferenceRecord row = mapper.rawRow(ADMIN, "EMAIL");
        mapper.insert(new AdminPreferenceRecord("admin-2", "EMAIL", row.getContactDetailEnc(), row.getUpdatedAt()));

        assertEquals(List.of(AlertTarget.inApp()), resolver.resolveAdminAlertTargets("admin-2"));
    }
}
