package com.tradingplatform.api.controllers;

import com.tradingplatform.api.exception.GlobalExceptionHandler;
import com.tradingplatform.api.mappers.AccountMapper;
import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.api.security.AccountAccessAuthorizer;
import com.tradingplatform.api.services.PortfolioPnlService;
import com.tradingplatform.api.services.QuoteResolver;
import com.tradingplatform.api.services.RealisedPnlCalculator;
import com.tradingplatform.domain.model.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Portfolio errors reach the client with their contract status and code, not as a 500.
 */
@DisplayName("PortfolioController error responses")
class PortfolioControllerErrorTest {

    private AccountMapper accountMapper;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        accountMapper = mock(AccountMapper.class);
        PortfolioPnlService service = new PortfolioPnlService(accountMapper, mock(InstrumentMapper.class),
            mock(QuoteResolver.class), mock(RealisedPnlCalculator.class));
        PortfolioController controller = new PortfolioController(service, mock(AccountAccessAuthorizer.class));
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("Unknown account is ACC-404, 404")
    void unknownAccountIs404() throws Exception {
        when(accountMapper.findByAccountId(99L)).thenReturn(null);

        mvc.perform(get("/api/v1/portfolio/99"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("ACC-404"));
        mvc.perform(get("/api/v1/portfolio/99/pnl"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("ACC-404"));
    }

    @Test
    @DisplayName("P&L with from after to is VAL-422, 422")
    void invertedDateRangeIs422() throws Exception {
        when(accountMapper.findByAccountId(1L)).thenReturn(mock(Account.class));

        mvc.perform(get("/api/v1/portfolio/1/pnl").param("from", "2026-10-07").param("to", "2026-10-01"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode").value("VAL-422"))
            .andExpect(jsonPath("$.message").value("from date must not be after to date"));
    }
}
