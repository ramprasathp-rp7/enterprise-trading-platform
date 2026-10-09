package com.tradingplatform.preferences.internal.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
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

import com.tradingplatform.api.exception.GlobalExceptionHandler;
import com.tradingplatform.api.security.AdminIdentity;
import com.tradingplatform.preferences.internal.crypto.ContactDetailCipher;
import com.tradingplatform.preferences.internal.persistence.FakeAdminPreferencesMapper;
import com.tradingplatform.preferences.internal.service.AdminPreferencesService;

/**
 * Drives the admin routes through the real AdminIdentity with a verified JWT in the security
 * context, over the real service and an in-memory table. ROLE_ADMIN itself is enforced by the
 * security chain, which a standalone MockMvc does not run.
 */
class AdminPreferencesControllerTest {

    private static final String ADMIN = "f47ac10b-58cc-4372-a567-0e02b2c3d479";
    private static final String OTHER_ADMIN = "0b9e2f1c-0000-4000-8000-000000000002";

    private FakeAdminPreferencesMapper mapper;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mapper = new FakeAdminPreferencesMapper();
        AdminPreferencesService service = new AdminPreferencesService(mapper, new ContactDetailCipher("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="));
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminPreferencesController(new AdminIdentity(), service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        signInAs(ADMIN);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getWithNothingStored_returnsPlatformDefault() throws Exception {
        mockMvc.perform(get("/api/v1/admin/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").doesNotExist())
                .andExpect(jsonPath("$.alertChannels.length()").value(1))
                .andExpect(jsonPath("$.alertChannels[0].channel").value("IN_APP"))
                .andExpect(jsonPath("$.configured").value(false));
    }

    @Test
    void putThenGet_readsBackSavedPreferencesInChannelOrder() throws Exception {
        mockMvc.perform(put("/api/v1/admin/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alertChannels\":[{\"channel\":\"IN_APP\"},{\"channel\":\"SMS\",\"contactDetail\":\"+44 7700 900123\"},{\"channel\":\"EMAIL\",\"contactDetail\":\"ops@example.com\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.updatedAt").exists());

        mockMvc.perform(get("/api/v1/admin/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertChannels[0].channel").value("EMAIL"))
                .andExpect(jsonPath("$.alertChannels[0].contactDetail").value("ops@example.com"))
                .andExpect(jsonPath("$.alertChannels[1].channel").value("SMS"))
                .andExpect(jsonPath("$.alertChannels[1].contactDetail").value("+447700900123"))
                .andExpect(jsonPath("$.alertChannels[2].channel").value("IN_APP"))
                .andExpect(jsonPath("$.configured").value(true));
    }

    @Test
    void contactDetailsAreStoredEncrypted() throws Exception {
        mockMvc.perform(put("/api/v1/admin/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alertChannels\":[{\"channel\":\"EMAIL\",\"contactDetail\":\"ops@example.com\"}]}"))
                .andExpect(status().isOk());

        byte[] stored = mapper.rawRow(ADMIN, "EMAIL").getContactDetailEnc();
        assertTrue(!new String(stored, StandardCharsets.ISO_8859_1).contains("ops@example.com"));
    }

    @Test
    void eachAdminSeesOnlyTheirOwnPreferences() throws Exception {
        mockMvc.perform(put("/api/v1/admin/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alertChannels\":[{\"channel\":\"EMAIL\",\"contactDetail\":\"ops@example.com\"}]}"))
                .andExpect(status().isOk());

        signInAs(OTHER_ADMIN);
        mockMvc.perform(get("/api/v1/admin/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.alertChannels[0].channel").value("IN_APP"));
    }

    @Test
    void invalidContactDetail_isPref422AndNothingIsStored() throws Exception {
        mockMvc.perform(put("/api/v1/admin/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alertChannels\":[{\"channel\":\"SMS\",\"contactDetail\":\"07700 900123\"}]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("PREF-422"));

        assertEquals(List.of(), mapper.rawRows(ADMIN));
    }

    @Test
    void emptyChannelList_isPref422() throws Exception {
        mockMvc.perform(put("/api/v1/admin/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alertChannels\":[]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("PREF-422"));
    }

    @Test
    void delete_returnsTheAdminToTheDefault() throws Exception {
        mockMvc.perform(put("/api/v1/admin/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alertChannels\":[{\"channel\":\"EMAIL\",\"contactDetail\":\"ops@example.com\"}]}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/admin/preferences")).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/admin/preferences"))
                .andExpect(jsonPath("$.configured").value(false));
        assertEquals(List.of(), mapper.rawRows(ADMIN));
    }

    @Test
    void noVerifiedToken_isAuth401() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/v1/admin/preferences"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH-401"));
    }

    private static void signInAs(String adminUserId) {
        Jwt jwt = new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(900),
                Map.of("alg", "HS256"),
                Map.of("sub", adminUserId, "accountId", 0, "roles", List.of("ADMIN"), "iss", "auth-service"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(jwt, null, List.of()));
    }
}
