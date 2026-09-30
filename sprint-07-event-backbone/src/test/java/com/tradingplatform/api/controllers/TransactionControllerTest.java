package com.tradingplatform.api.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.api.exception.GlobalExceptionHandler;
import com.tradingplatform.api.security.AccountAccessAuthorizer;
import com.tradingplatform.api.services.TransactionService;
import com.tradingplatform.domain.dto.TransactionHistoryEntry;
import com.tradingplatform.domain.dto.TransactionRequest;
import com.tradingplatform.domain.dto.TransactionResponse;
import com.tradingplatform.domain.enums.TransactionAction;
import com.tradingplatform.domain.enums.TransactionType;
import com.tradingplatform.domain.exception.AccountNotFoundException;
import com.tradingplatform.domain.exception.InsufficientFundsForWithdrawalException;
import com.tradingplatform.domain.exception.TransactionNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for TransactionController.
 * Verifies REST endpoints for deposits, withdrawals, and transaction history queries.
 */
@ExtendWith(MockitoExtension.class)
class TransactionControllerTest {

    @Mock
    private AccountAccessAuthorizer accountAccessAuthorizer;

    @Mock
    private TransactionService transactionService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
            new TransactionController(accountAccessAuthorizer, transactionService)
        ).setControllerAdvice(new GlobalExceptionHandler()).build();
        objectMapper = new ObjectMapper();
    }

    /**
     * Test successful deposit endpoint.
     */
    @Test
    void depositSuccessfullyReturns201() throws Exception {
        doNothing().when(accountAccessAuthorizer).assertTokenCanAccess(1L);

        TransactionResponse mockResponse = new TransactionResponse(
            UUID.randomUUID(), 1L, null,
            TransactionType.DEPOSIT, TransactionAction.CREDIT,
            new BigDecimal("500.00"), Instant.now()
        );

        when(transactionService.deposit(1L, new BigDecimal("500.00")))
            .thenReturn(mockResponse);

        TransactionRequest request = new TransactionRequest(new BigDecimal("500.00"));

        mockMvc.perform(post("/api/v1/accounts/1/deposits")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accountId").value(1L))
            .andExpect(jsonPath("$.amount").value(500.00))
            .andExpect(jsonPath("$.transactionType").value("DEPOSIT"));
    }

    /**
     * Test deposit with invalid amount returns 422.
     */
    @Test
    void depositWithInvalidAmountReturns400() throws Exception {
        // Negative amount should be rejected by validation
        TransactionRequest invalidRequest = new TransactionRequest(new BigDecimal("-100.00"));

        mockMvc.perform(post("/api/v1/accounts/1/deposits")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isUnprocessableEntity());
    }

    /**
     * Test deposit with null amount returns 422.
     */
    @Test
    void depositWithNullAmountReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/1/deposits")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": null}"))
            .andExpect(status().isUnprocessableEntity());
    }

    /**
     * Test deposit with non-existent account returns 404.
     */
    @Test
    void depositWithNonExistentAccountReturns404() throws Exception {
        doNothing().when(accountAccessAuthorizer).assertTokenCanAccess(999L);

        when(transactionService.deposit(999L, new BigDecimal("500.00")))
            .thenThrow(new AccountNotFoundException(999L));

        TransactionRequest request = new TransactionRequest(new BigDecimal("500.00"));

        mockMvc.perform(post("/api/v1/accounts/999/deposits")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound());
    }

    /**
     * Test successful withdrawal endpoint.
     */
    @Test
    void withdrawalSuccessfullyReturns201() throws Exception {
        doNothing().when(accountAccessAuthorizer).assertTokenCanAccess(1L);

        TransactionResponse mockResponse = new TransactionResponse(
            UUID.randomUUID(), 1L, null,
            TransactionType.WITHDRAW, TransactionAction.DEBIT,
            new BigDecimal("200.00"), Instant.now()
        );

        when(transactionService.withdraw(1L, new BigDecimal("200.00")))
            .thenReturn(mockResponse);

        TransactionRequest request = new TransactionRequest(new BigDecimal("200.00"));

        mockMvc.perform(post("/api/v1/accounts/1/withdrawals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accountId").value(1L))
            .andExpect(jsonPath("$.amount").value(200.00))
            .andExpect(jsonPath("$.transactionType").value("WITHDRAW"));
    }

    /**
     * Test withdrawal with insufficient funds returns 400.
     */
    @Test
    void withdrawalWithInsufficientFundsReturns400() throws Exception {
        doNothing().when(accountAccessAuthorizer).assertTokenCanAccess(1L);

        when(transactionService.withdraw(1L, new BigDecimal("10000.00")))
            .thenThrow(new InsufficientFundsForWithdrawalException(
                new BigDecimal("10000.00"), new BigDecimal("500.00")));

        TransactionRequest request = new TransactionRequest(new BigDecimal("10000.00"));

        mockMvc.perform(post("/api/v1/accounts/1/withdrawals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    /**
     * Test get transaction by ID.
     */
    @Test
    void getTransactionByIdSuccessfullyReturns200() throws Exception {
        doNothing().when(accountAccessAuthorizer).assertTokenCanAccess(1L);

        UUID transactionId = UUID.randomUUID();
        TransactionResponse mockResponse = new TransactionResponse(
            transactionId, 1L, null,
            TransactionType.DEPOSIT, TransactionAction.CREDIT,
            new BigDecimal("500.00"), Instant.now()
        );

        when(transactionService.getTransaction(transactionId))
            .thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/accounts/1/transactions/" + transactionId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transactionId").value(transactionId.toString()))
            .andExpect(jsonPath("$.amount").value(500.00));
    }

    /**
     * Test get non-existent transaction returns 404.
     */
    @Test
    void getTransactionWithInvalidIdReturns404() throws Exception {
        doNothing().when(accountAccessAuthorizer).assertTokenCanAccess(1L);

        UUID invalidId = UUID.randomUUID();
        when(transactionService.getTransaction(invalidId))
            .thenThrow(new TransactionNotFoundException(invalidId.toString()));

        mockMvc.perform(get("/api/v1/accounts/1/transactions/" + invalidId))
            .andExpect(status().isNotFound());
    }

    /**
     * Test get transaction history.
     */
    @Test
    void getTransactionHistorySuccessfullyReturns200() throws Exception {
        doNothing().when(accountAccessAuthorizer).assertTokenCanAccess(1L);

        List<TransactionHistoryEntry> mockHistory = List.of(
            new TransactionHistoryEntry(
                UUID.randomUUID(), TransactionType.DEPOSIT,
                TransactionAction.CREDIT, new BigDecimal("500.00"), Instant.now()),
            new TransactionHistoryEntry(
                UUID.randomUUID(), TransactionType.WITHDRAW,
                TransactionAction.DEBIT, new BigDecimal("100.00"), Instant.now())
        );

        when(transactionService.getTransactionHistory(eq(1L), any(), any(), any()))
            .thenReturn(mockHistory);

        mockMvc.perform(get("/api/v1/accounts/1/transactions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].amount").value(500.00))
            .andExpect(jsonPath("$[1].amount").value(100.00));
    }

    /**
     * Test get transaction history for non-existent account returns 404.
     */
    @Test
    void getTransactionHistoryForNonExistentAccountReturns404() throws Exception {
        doNothing().when(accountAccessAuthorizer).assertTokenCanAccess(999L);

        when(transactionService.getTransactionHistory(eq(999L), any(), any(), any()))
            .thenThrow(new AccountNotFoundException(999L));

        mockMvc.perform(get("/api/v1/accounts/999/transactions"))
            .andExpect(status().isNotFound());
    }

    /**
     * Test get transaction history with empty result.
     */
    @Test
    void getTransactionHistoryWithNoTransactionsReturnsEmptyList() throws Exception {
        doNothing().when(accountAccessAuthorizer).assertTokenCanAccess(1L);

        when(transactionService.getTransactionHistory(eq(1L), any(), any(), any()))
            .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/accounts/1/transactions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(0));
    }
}
