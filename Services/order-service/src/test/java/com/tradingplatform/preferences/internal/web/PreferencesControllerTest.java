package com.tradingplatform.preferences.internal.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;

import com.tradingplatform.api.exception.GlobalExceptionHandler;
import com.tradingplatform.api.security.AccountAccessAuthorizer;
import com.tradingplatform.api.services.AccountService;
import com.tradingplatform.preferences.internal.crypto.ContactDetailCipher;
import com.tradingplatform.preferences.internal.persistence.FakePreferencesMapper;
import com.tradingplatform.preferences.internal.service.LastKnownChannels;
import com.tradingplatform.preferences.internal.service.PreferencesService;

/**
 * Drives the routes through the real AccountAccessAuthorizer with a verified JWT in the security
 * context, over the real service and an in-memory table.
 */
class PreferencesControllerTest {

    private static final long OWN_ACCOUNT = 2L;
    private static final long OTHER_ACCOUNT = 3L;

    private FakePreferencesMapper mapper;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mapper = new FakePreferencesMapper();
        PreferencesService service = new PreferencesService(
                mapper, new ContactDetailCipher("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="), mock(AccountService.class), new LastKnownChannels());
        mockMvc = MockMvcBuilders.standaloneSetup(new PreferencesController(new AccountAccessAuthorizer(), service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        signInAs(OWN_ACCOUNT);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getWithNothingStored_returnsPlatformDefault() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/2/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(2))
                .andExpect(jsonPath("$.alertChannels.length()").value(1))
                .andExpect(jsonPath("$.alertChannels[0].channel").value("IN_APP"))
                .andExpect(jsonPath("$.alertChannels[0].contactDetail").doesNotExist())
                .andExpect(jsonPath("$.configured").value(false));
    }

    @Test
    void putThenGet_readsBackSavedPreferences() throws Exception {
        mockMvc.perform(put("/api/v1/accounts/2/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alertChannels\":[{\"channel\":\"EMAIL\",\"contactDetail\":\"customer@example.com\"},{\"channel\":\"IN_APP\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertChannels.length()").value(2))
                .andExpect(jsonPath("$.configured").value(true));

        mockMvc.perform(get("/api/v1/accounts/2/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertChannels[0].channel").value("EMAIL"))
                .andExpect(jsonPath("$.alertChannels[0].contactDetail").value("customer@example.com"))
                .andExpect(jsonPath("$.alertChannels[1].channel").value("IN_APP"))
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void delete_resetsToDefault() throws Exception {
        mockMvc.perform(put("/api/v1/accounts/2/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"alertChannels\":[{\"channel\":\"SMS\",\"contactDetail\":\"+447700900123\"}]}"));

        mockMvc.perform(delete("/api/v1/accounts/2/preferences")).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/accounts/2/preferences"))
                .andExpect(jsonPath("$.configured").value(false));
    }

    @Test
    void anotherAccountsPreferences_areRefusedNotReturnedEmpty() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/3/preferences"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACC-403"))
                .andExpect(jsonPath("$.alertChannels").doesNotExist());

        mockMvc.perform(put("/api/v1/accounts/3/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alertChannels\":[{\"channel\":\"EMAIL\",\"contactDetail\":\"attacker@example.com\"}]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACC-403"));

        mockMvc.perform(delete("/api/v1/accounts/3/preferences"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACC-403"));

        assertEquals(0, mapper.calls(), "a refused request must never reach the table");
        assertTrue(mapper.rawRows(OTHER_ACCOUNT).isEmpty());
    }

    @Test
    void noAuthenticatedToken_isRefused() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/v1/accounts/2/preferences"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACC-403"));
    }

    @Test
    void unknownChannel_isVal422() throws Exception {
        mockMvc.perform(put("/api/v1/accounts/2/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alertChannels\":[{\"channel\":\"WEBHOOK\",\"contactDetail\":\"http://169.254.169.254/\"}]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VAL-422"));
    }

    @Test
    void missingChannel_isPref422() throws Exception {
        mockMvc.perform(put("/api/v1/accounts/2/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alertChannels\":[{\"contactDetail\":\"customer@example.com\"}]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("PREF-422"));
    }

    @Test
    void contactThatDoesNotFitChannel_isPref422() throws Exception {
        mockMvc.perform(put("/api/v1/accounts/2/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alertChannels\":[{\"channel\":\"SMS\",\"contactDetail\":\"customer@example.com\"}]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("PREF-422"));
    }

    @Test
    void emptyChannelList_isPref422() throws Exception {
        mockMvc.perform(put("/api/v1/accounts/2/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alertChannels\":[]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("PREF-422"));
    }

    @Test
    void moreThanThreeChannels_isPref422() throws Exception {
        mockMvc.perform(put("/api/v1/accounts/2/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alertChannels\":[{\"channel\":\"IN_APP\"},{\"channel\":\"IN_APP\"},"
                                + "{\"channel\":\"IN_APP\"},{\"channel\":\"IN_APP\"}]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("PREF-422"));
    }

    @Test
    void sameChannelTwice_isPref422() throws Exception {
        mockMvc.perform(put("/api/v1/accounts/2/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alertChannels\":[{\"channel\":\"EMAIL\",\"contactDetail\":\"a@example.com\"},"
                                + "{\"channel\":\"EMAIL\",\"contactDetail\":\"b@example.com\"}]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("PREF-422"));

        assertEquals(0, mapper.rawRows(OWN_ACCOUNT).size());
    }

    @Test
    void controllerExposesOnlyTheCustomerRoute() {
        RequestMapping mapping = PreferencesController.class.getAnnotation(RequestMapping.class);

        assertArrayEquals(new String[] {"/api/v1/accounts/{accountId}/preferences"}, mapping.value());
    }

    private static void signInAs(long accountId) {
        Jwt jwt = new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(900),
                Map.of("alg", "HS256"),
                Map.of("sub", "u-1", "accountId", accountId, "roles", List.of("CUSTOMER"), "iss", "auth-service"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(jwt, null, List.of()));
    }
}
