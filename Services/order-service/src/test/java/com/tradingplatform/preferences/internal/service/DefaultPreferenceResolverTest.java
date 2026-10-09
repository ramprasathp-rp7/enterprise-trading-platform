package com.tradingplatform.preferences.internal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tradingplatform.api.services.AccountService;
import com.tradingplatform.preferences.AlertChannel;
import com.tradingplatform.preferences.AlertTarget;
import com.tradingplatform.preferences.PreferencesUnavailableException;
import com.tradingplatform.preferences.ResolvedAlertChannels;
import com.tradingplatform.preferences.ResolvedAlertChannels.Source;
import com.tradingplatform.preferences.internal.crypto.ContactDetailCipher;
import com.tradingplatform.preferences.internal.persistence.FakePreferencesMapper;

class DefaultPreferenceResolverTest {

    private static final long ACCOUNT = 2L;
    private static final AlertChannelEntry EMAIL = new AlertChannelEntry(AlertChannel.EMAIL, "customer@example.com");
    private static final AlertChannelEntry SMS = new AlertChannelEntry(AlertChannel.SMS, "+447700900123");

    private FakePreferencesMapper mapper;
    private PreferencesService service;
    private DefaultPreferenceResolver resolver;

    @BeforeEach
    void setUp() {
        mapper = new FakePreferencesMapper();
        LastKnownChannels lastKnown = new LastKnownChannels();
        service = new PreferencesService(mapper, new ContactDetailCipher("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="), mock(AccountService.class), lastKnown);
        resolver = new DefaultPreferenceResolver(service, lastKnown);
    }

    @Test
    void storedPreference_resolvesToCustomerChannelWithDecryptedDestination() {
        service.replace(ACCOUNT, List.of(SMS));

        ResolvedAlertChannels resolved = resolver.resolveAlertChannels(ACCOUNT);

        assertEquals(List.of(new AlertTarget(AlertChannel.SMS, "+447700900123")), resolved.targets());
        assertEquals(Source.CUSTOMER, resolved.source());
        assertFalse(resolved.stale());
    }

    @Test
    void severalStoredChannels_resolveToOneTargetEach() {
        service.replace(ACCOUNT, List.of(new AlertChannelEntry(AlertChannel.IN_APP, null), SMS, EMAIL));

        ResolvedAlertChannels resolved = resolver.resolveAlertChannels(ACCOUNT);

        assertEquals(List.of(AlertChannel.EMAIL, AlertChannel.SMS, AlertChannel.IN_APP), resolved.channels());
        assertEquals("customer@example.com", resolved.targets().get(0).destination());
        assertEquals("+447700900123", resolved.targets().get(1).destination());
    }

    @Test
    void nothingStored_resolvesToSingleInAppPlatformDefault() {
        ResolvedAlertChannels resolved = resolver.resolveAlertChannels(ACCOUNT);

        assertEquals(List.of(AlertTarget.inApp()), resolved.targets());
        assertEquals(Source.PLATFORM_DEFAULT, resolved.source());
    }

    @Test
    void changeIsUsedOnTheVeryNextResolve() {
        service.replace(ACCOUNT, List.of(EMAIL));
        resolver.resolveAlertChannels(ACCOUNT);

        service.replace(ACCOUNT, List.of(SMS));

        assertEquals(List.of(AlertChannel.SMS), resolver.resolveAlertChannels(ACCOUNT).channels());
    }

    @Test
    void storeDown_withEarlierAnswer_servesLastKnownMarkedStale() {
        service.replace(ACCOUNT, List.of(EMAIL, SMS));
        resolver.resolveAlertChannels(ACCOUNT);

        mapper.setUnavailable(true);
        ResolvedAlertChannels resolved = resolver.resolveAlertChannels(ACCOUNT);

        assertEquals(List.of(AlertChannel.EMAIL, AlertChannel.SMS), resolved.channels());
        assertTrue(resolved.stale());
    }

    @Test
    void storeDown_withNoEarlierAnswer_raisesRatherThanGuessing() {
        mapper.setUnavailable(true);

        PreferencesUnavailableException ex = assertThrows(PreferencesUnavailableException.class,
                () -> resolver.resolveAlertChannels(ACCOUNT));

        assertEquals(ACCOUNT, ex.getAccountId());
    }

    @Test
    void reset_forgetsLastKnownSoAResetCustomerIsNotReachedOnOldDetails() {
        service.replace(ACCOUNT, List.of(EMAIL));
        resolver.resolveAlertChannels(ACCOUNT);
        service.reset(ACCOUNT);

        mapper.setUnavailable(true);

        assertThrows(PreferencesUnavailableException.class, () -> resolver.resolveAlertChannels(ACCOUNT));
    }

    @Test
    void undecryptableRow_isTreatedAsUnavailable() {
        service.replace(ACCOUNT, List.of(EMAIL));
        mapper.rawRow(ACCOUNT, "EMAIL").getContactDetailEnc()[20] ^= 1;

        assertTrue(resolver.resolveAlertChannels(ACCOUNT).stale(), "last known answer served");

        DefaultPreferenceResolver afterRestart = new DefaultPreferenceResolver(service, new LastKnownChannels());
        assertThrows(PreferencesUnavailableException.class, () -> afterRestart.resolveAlertChannels(ACCOUNT));
    }

    @Test
    void resolvedToString_redactsDestinations() {
        service.replace(ACCOUNT, List.of(EMAIL, SMS));

        String printed = resolver.resolveAlertChannels(ACCOUNT).toString();

        assertFalse(printed.contains("customer@example.com"));
        assertFalse(printed.contains("447700900123"));
    }

    @Test
    void resolvedAnswer_refusesEmptyOrDuplicateTargets() {
        assertThrows(IllegalArgumentException.class,
                () -> new ResolvedAlertChannels(ACCOUNT, List.of(), Source.CUSTOMER, false));
        assertThrows(IllegalArgumentException.class,
                () -> new ResolvedAlertChannels(ACCOUNT, List.of(AlertTarget.inApp(), AlertTarget.inApp()),
                        Source.CUSTOMER, false));
    }
}
