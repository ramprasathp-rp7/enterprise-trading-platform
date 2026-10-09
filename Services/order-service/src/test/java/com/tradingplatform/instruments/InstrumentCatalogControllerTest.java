package com.tradingplatform.instruments;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.tradingplatform.api.exception.GlobalExceptionHandler;
import com.tradingplatform.domain.exception.MarketDataUnavailableException;
import com.tradingplatform.domain.exception.SymbolNotDiscoveredException;
import com.tradingplatform.instruments.controller.InstrumentCatalogController;
import com.tradingplatform.instruments.model.InstrumentListing;
import com.tradingplatform.instruments.service.InstrumentCatalogService;

class InstrumentCatalogControllerTest {

    private InstrumentCatalogService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(InstrumentCatalogService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new InstrumentCatalogController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    private static InstrumentListing shop() {
        return new InstrumentListing(26L, "SHOP", "Shopify Inc.", "EQUITY", "USD", true, "NYSE");
    }

    @Test
    void list_returnsEveryInstrumentWithItsExchange() throws Exception {
        when(service.list()).thenReturn(List.of(shop()));

        mockMvc.perform(get("/api/v1/instruments"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(26))
            .andExpect(jsonPath("$[0].symbol").value("SHOP"))
            .andExpect(jsonPath("$[0].exchange").value("NYSE"))
            .andExpect(jsonPath("$[0].tradable").value(true));
    }

    @Test
    void discover_newSymbol_is201() throws Exception {
        when(service.discover("shop")).thenReturn(new InstrumentCatalogService.Discovery(shop(), true));

        mockMvc.perform(post("/api/v1/instruments/discover").contentType(MediaType.APPLICATION_JSON).content("{\"symbol\":\"shop\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.created").value(true))
            .andExpect(jsonPath("$.instrument.symbol").value("SHOP"));
    }

    @Test
    void discover_alreadyListed_is200() throws Exception {
        when(service.discover("SHOP")).thenReturn(new InstrumentCatalogService.Discovery(shop(), false));

        mockMvc.perform(post("/api/v1/instruments/discover").contentType(MediaType.APPLICATION_JSON).content("{\"symbol\":\"SHOP\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.created").value(false));
    }

    @Test
    void discover_unknownSymbol_isIns404WithTheReason() throws Exception {
        when(service.discover("NOPE")).thenThrow(new SymbolNotDiscoveredException("NOPE", "No market data was found for NOPE."));

        mockMvc.perform(post("/api/v1/instruments/discover").contentType(MediaType.APPLICATION_JSON).content("{\"symbol\":\"NOPE\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("INS-404"))
            .andExpect(jsonPath("$.message").value("No market data was found for NOPE."));
    }

    @Test
    void discover_providerDown_isMkt503() throws Exception {
        when(service.discover("SHOP")).thenThrow(new MarketDataUnavailableException("down", new RuntimeException()));

        mockMvc.perform(post("/api/v1/instruments/discover").contentType(MediaType.APPLICATION_JSON).content("{\"symbol\":\"SHOP\"}"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.errorCode").value("MKT-503"));
    }

    @Test
    void discover_blankOrMissingSymbol_isVal422() throws Exception {
        mockMvc.perform(post("/api/v1/instruments/discover").contentType(MediaType.APPLICATION_JSON).content("{\"symbol\":\"  \"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode").value("VAL-422"));
        mockMvc.perform(post("/api/v1/instruments/discover").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnprocessableEntity());
        verify(service, never()).discover(any());
    }
}
