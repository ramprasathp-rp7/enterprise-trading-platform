package com.tradingplatform.preferences.internal.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.tradingplatform.api.services.AccountService;
import com.tradingplatform.domain.exception.AccountNotFoundException;
import com.tradingplatform.domain.exception.InvalidPreferenceException;
import com.tradingplatform.preferences.AlertChannel;
import com.tradingplatform.preferences.AlertTarget;
import com.tradingplatform.preferences.internal.crypto.ContactDetailCipher;
import com.tradingplatform.preferences.internal.persistence.FakePreferencesMapper;

class PreferencesServiceTest {

    private static final long ACCOUNT = 2L;
    private static final long OTHER_ACCOUNT = 3L;

    private FakePreferencesMapper mapper;
    private AccountService accountService;
    private PreferencesService service;

    @BeforeEach
    void setUp() {
        mapper = new FakePreferencesMapper();
        accountService = mock(AccountService.class);
        service = new PreferencesService(mapper, new ContactDetailCipher("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="), accountService, new LastKnownChannels());
    }

    private static AlertChannelEntry email(String address) {
        return new AlertChannelEntry(AlertChannel.EMAIL, address);
    }

    private static AlertChannelEntry sms(String number) {
        return new AlertChannelEntry(AlertChannel.SMS, number);
    }

    private static AlertChannelEntry inApp() {
        return new AlertChannelEntry(AlertChannel.IN_APP, null);
    }

    @Test
    void get_withNothingStored_answersPlatformDefaultNotEmpty() {
        PreferencesView view = service.get(ACCOUNT);

        assertEquals(ACCOUNT, view.accountId());
        assertEquals(List.of(AlertTarget.inApp()), view.alertChannels());
        assertFalse(view.configured());
        assertNull(view.updatedAt());
    }

    @Test
    void replace_thenGet_readsBackWhatWasSaved() {
        service.replace(ACCOUNT, List.of(email("  customer@example.com ")));

        PreferencesView view = service.get(ACCOUNT);

        assertEquals(List.of(new AlertTarget(AlertChannel.EMAIL, "customer@example.com")), view.alertChannels());
        assertTrue(view.configured());
        assertNotNull(view.updatedAt());
    }

    @Test
    void replace_withSeveralChannels_storesEachAndReadsThemBackInChannelOrder() {
        service.replace(ACCOUNT, List.of(inApp(), sms("+44 7700 900123"), email("customer@example.com")));

        PreferencesView view = service.get(ACCOUNT);

        assertEquals(List.of(
                new AlertTarget(AlertChannel.EMAIL, "customer@example.com"),
                new AlertTarget(AlertChannel.SMS, "+447700900123"),
                AlertTarget.inApp()), view.alertChannels());
        assertEquals(3, mapper.rawRows(ACCOUNT).size());
    }

    @Test
    void replace_isAFullReplacement_droppingChannelsNoLongerChosen() {
        service.replace(ACCOUNT, List.of(email("customer@example.com"), sms("+447700900123")));
        service.replace(ACCOUNT, List.of(sms("+447700900999")));

        PreferencesView view = service.get(ACCOUNT);

        assertEquals(List.of(new AlertTarget(AlertChannel.SMS, "+447700900999")), view.alertChannels());
        assertEquals(1, mapper.rawRows(ACCOUNT).size());
    }

    @Test
    void replace_storesEveryContactDetailEncryptedNeverInClear() {
        service.replace(ACCOUNT, List.of(email("customer@example.com"), sms("+447700900123")));

        byte[] emailBytes = mapper.rawRow(ACCOUNT, "EMAIL").getContactDetailEnc();
        byte[] smsBytes = mapper.rawRow(ACCOUNT, "SMS").getContactDetailEnc();

        assertFalse(new String(emailBytes, StandardCharsets.ISO_8859_1).contains("customer@example.com"));
        assertFalse(new String(smsBytes, StandardCharsets.ISO_8859_1).contains("447700900123"));
    }

    @Test
    void replace_withInApp_storesNoContactDetail() {
        service.replace(ACCOUNT, List.of(inApp()));

        assertNull(mapper.rawRow(ACCOUNT, "IN_APP").getContactDetailEnc());
        assertTrue(service.get(ACCOUNT).configured());
    }

    @Test
    void preferences_areKeptPerAccount() {
        service.replace(ACCOUNT, List.of(email("customer@example.com")));

        assertFalse(service.get(OTHER_ACCOUNT).configured());
    }

    @Test
    void reset_returnsAccountToPlatformDefault() {
        service.replace(ACCOUNT, List.of(email("customer@example.com"), sms("+447700900123")));

        service.reset(ACCOUNT);

        assertTrue(mapper.rawRows(ACCOUNT).isEmpty());
        assertFalse(service.get(ACCOUNT).configured());
    }

    @Test
    void unknownAccount_isAccountNotFound() {
        when(accountService.getAccount(99L)).thenThrow(new AccountNotFoundException(99L));

        assertThrows(AccountNotFoundException.class, () -> service.get(99L));
        assertThrows(AccountNotFoundException.class, () -> service.replace(99L, List.of(inApp())));
        assertThrows(AccountNotFoundException.class, () -> service.reset(99L));
    }

    @Test
    void replace_refusesAnEmptyChoice() {
        InvalidPreferenceException ex = assertThrows(InvalidPreferenceException.class,
                () -> service.replace(ACCOUNT, List.of()));

        assertEquals("PREF-422", ex.getCode());
    }

    @Test
    void replace_refusesTheSameChannelTwice() {
        InvalidPreferenceException ex = assertThrows(InvalidPreferenceException.class,
                () -> service.replace(ACCOUNT, List.of(email("a@example.com"), email("b@example.com"))));

        assertEquals("PREF-422", ex.getCode());
        assertFalse(ex.getMessage().contains("example.com"));
        assertTrue(mapper.rawRows(ACCOUNT).isEmpty());
    }

    @Test
    void invalidReplace_leavesThePreviousChoiceInPlace() {
        service.replace(ACCOUNT, List.of(email("customer@example.com")));

        assertThrows(InvalidPreferenceException.class,
                () -> service.replace(ACCOUNT, List.of(inApp(), sms("not-a-number"))));

        assertEquals(AlertChannel.EMAIL, service.get(ACCOUNT).alertChannels().get(0).channel());
    }

    @ParameterizedTest(name = "{0} with \"{1}\" is refused")
    @CsvSource(nullValues = "NULL", value = {
        "EMAIL, NULL",
        "EMAIL, '   '",
        "EMAIL, not-an-email",
        "EMAIL, customer@",
        "SMS, NULL",
        "SMS, 07700900123",
        "SMS, +0123456789",
        "SMS, customer@example.com",
        "IN_APP, customer@example.com",
    })
    void replace_rejectsContactDetailThatDoesNotFitChannel(AlertChannel channel, String contact) {
        InvalidPreferenceException ex = assertThrows(InvalidPreferenceException.class,
                () -> service.replace(ACCOUNT, List.of(new AlertChannelEntry(channel, contact))));

        assertEquals("PREF-422", ex.getCode());
        if (contact != null && !contact.isBlank()) {
            assertFalse(ex.getMessage().contains(contact), "message must not echo the contact detail");
        }
        assertTrue(mapper.rawRows(ACCOUNT).isEmpty());
    }

    @Test
    void replace_rejectsOverlongEmail() {
        String longEmail = "a".repeat(250) + "@example.com";

        assertThrows(InvalidPreferenceException.class, () -> service.replace(ACCOUNT, List.of(email(longEmail))));
    }

    @Test
    void ciphertext_differsEachWriteForTheSameValue() {
        service.replace(ACCOUNT, List.of(email("customer@example.com")));
        byte[] first = mapper.rawRow(ACCOUNT, "EMAIL").getContactDetailEnc();
        service.replace(ACCOUNT, List.of(email("customer@example.com")));
        byte[] second = mapper.rawRow(ACCOUNT, "EMAIL").getContactDetailEnc();

        assertThrows(AssertionError.class, () -> assertArrayEquals(first, second));
    }

    @Test
    void toString_neverPrintsAContactDetail() {
        PreferencesView view = service.replace(ACCOUNT, List.of(email("customer@example.com"), sms("+447700900123")));

        assertFalse(view.toString().contains("customer@example.com"));
        assertFalse(view.toString().contains("447700900123"));
    }
}
