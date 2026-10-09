package com.tradingplatform.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.tradingplatform.api.exception.GlobalExceptionHandler;
import com.tradingplatform.api.security.AccountAccessAuthorizer;
import com.tradingplatform.api.services.AccountService;
import com.tradingplatform.domain.exception.AccountNotFoundException;
import com.tradingplatform.settings.controller.AccountSettingsController;
import com.tradingplatform.settings.mapper.AccountSettingsMapper;
import com.tradingplatform.settings.model.AccountSettings;
import com.tradingplatform.settings.service.AccountSettingsService;

/**
 * The settings routes through the real AccountAccessAuthorizer and service, over a mocked table.
 */
class AccountSettingsControllerTest {

    private AccountSettingsMapper mapper;
    private AccountService accountService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mapper = mock(AccountSettingsMapper.class);
        accountService = mock(AccountService.class);
        AccountSettingsService service = new AccountSettingsService(mapper, accountService);
        mockMvc = MockMvcBuilders.standaloneSetup(new AccountSettingsController(new AccountAccessAuthorizer(), service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
        signInAs(2L);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void get_nothingStored_returnsTheDefaults() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/2/settings"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountId").value(2))
            .andExpect(jsonPath("$.market").value("ALL"))
            .andExpect(jsonPath("$.orderBudget").doesNotExist())
            .andExpect(jsonPath("$.defaultQuantity").doesNotExist())
            .andExpect(jsonPath("$.configured").value(false));
    }

    @Test
    void get_storedSettings_areReturned() throws Exception {
        when(mapper.findByAccountId(2L)).thenReturn(
            new AccountSettings(2L, new BigDecimal("5000.00"), "USA", 10, Instant.parse("2026-10-09T08:00:00Z")));

        mockMvc.perform(get("/api/v1/accounts/2/settings"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderBudget").value(5000.00))
            .andExpect(jsonPath("$.market").value("USA"))
            .andExpect(jsonPath("$.defaultQuantity").value(10))
            .andExpect(jsonPath("$.configured").value(true));
    }

    @Test
    void put_storesTheSettingsRoundingTheBudgetToCents() throws Exception {
        mockMvc.perform(put("/api/v1/accounts/2/settings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderBudget\": 2500.555, \"market\": \"NSE\", \"defaultQuantity\": 5}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderBudget").value(2500.56))
            .andExpect(jsonPath("$.market").value("NSE"))
            .andExpect(jsonPath("$.configured").value(true));

        ArgumentCaptor<AccountSettings> saved = ArgumentCaptor.forClass(AccountSettings.class);
        verify(mapper).upsert(saved.capture());
        assertEquals(2L, saved.getValue().getAccountId());
        assertEquals(new BigDecimal("2500.56"), saved.getValue().getOrderBudget());
        assertEquals(5, saved.getValue().getDefaultQuantity());
    }

    @Test
    void put_nullBudgetAndQuantity_clearThem() throws Exception {
        mockMvc.perform(put("/api/v1/accounts/2/settings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderBudget\": null, \"market\": \"ALL\", \"defaultQuantity\": null}"))
            .andExpect(status().isOk());

        ArgumentCaptor<AccountSettings> saved = ArgumentCaptor.forClass(AccountSettings.class);
        verify(mapper).upsert(saved.capture());
        assertNull(saved.getValue().getOrderBudget());
        assertNull(saved.getValue().getDefaultQuantity());
    }

    @Test
    void put_invalidValues_areVal422AndNothingIsStored() throws Exception {
        for (String body : List.of(
                "{\"market\": \"LSE\"}",
                "{\"orderBudget\": 0, \"market\": \"ALL\"}",
                "{\"orderBudget\": -5, \"market\": \"ALL\"}",
                "{\"market\": \"ALL\", \"defaultQuantity\": 0}",
                "{\"orderBudget\": 100}")) {
            mockMvc.perform(put("/api/v1/accounts/2/settings").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VAL-422"));
        }
        verify(mapper, never()).upsert(any());
    }

    @Test
    void anotherAccount_isAcc403() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/3/settings"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode").value("ACC-403"));
        mockMvc.perform(put("/api/v1/accounts/3/settings").contentType(MediaType.APPLICATION_JSON).content("{\"market\": \"ALL\"}"))
            .andExpect(status().isForbidden());
        verify(mapper, never()).upsert(any());
    }

    @Test
    void unknownAccount_isAcc404() throws Exception {
        when(accountService.getAccount(2L)).thenThrow(new AccountNotFoundException(2L));

        mockMvc.perform(get("/api/v1/accounts/2/settings"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("ACC-404"));
    }

    @Test
    void delete_returnsTheAccountToTheDefaults() throws Exception {
        mockMvc.perform(delete("/api/v1/accounts/2/settings")).andExpect(status().isNoContent());
        verify(mapper).deleteByAccountId(2L);
    }

    private static void signInAs(long accountId) {
        Jwt jwt = new Jwt("token-value", Instant.now(), Instant.now().plusSeconds(900), Map.of("alg", "HS256"),
            Map.of("sub", "u-1", "accountId", accountId, "roles", List.of("CUSTOMER"), "iss", "auth-service"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(jwt, null, List.of()));
    }
}
