package com.tradingplatform.domain.exception;

import com.tradingplatform.api.exception.GlobalExceptionHandler;
import com.tradingplatform.domain.dto.ErrorResponse;
import com.tradingplatform.domain.enums.ErrorCode;
import com.tradingplatform.domain.enums.AccountStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for GlobalExceptionHandler.
 * 
 * Verifies:
 * 1. Each domain exception maps to correct HTTP status code
 * 2. Each exception returns the documented error code
 * 3. Response body contains only user-facing message (no internal details)
 * 4. Clients can branch on error codes (multiple codes per status works)
 */
@DisplayName("GlobalExceptionHandler Tests")
class GlobalExceptionHandlerTest {
    
    private GlobalExceptionHandler handler;
    private WebRequest webRequest;
    
    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        webRequest = new ServletWebRequest(mockRequest);
    }
    
    @Test
    @DisplayName("1. AccountNotFoundException maps to ACC-404 with status 404")
    void testAccountNotFound_Returns404_WithErrorCode() {
        // Given
        Long accountId = 123L;
        AccountNotFoundException ex = new AccountNotFoundException(accountId);
        
        // When
        ResponseEntity<ErrorResponse> response = handler.handleAccountNotFound(ex, webRequest);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo(ErrorCode.ACC_404);
        assertThat(response.getBody().message())
            .isEqualTo("The requested account could not be found.");
        
        // Verify no internal details exposed
        assertThat(response.getBody().message())
            .doesNotContain(String.valueOf(accountId))
            .doesNotContain("AccountNotFound")
            .doesNotContain("Exception");
    }
    
    @Test
    @DisplayName("2. AccountNotActiveException maps to ACC-403 with status 403")
    void testAccountNotActive_Returns403_WithErrorCode() {
        // Given
        Long accountId = 456L;
        AccountNotActiveException ex = new AccountNotActiveException(accountId, AccountStatus.SUSPENDED);
        
        // When
        ResponseEntity<ErrorResponse> response = handler.handleAccountNotActive(ex, webRequest);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo(ErrorCode.ACC_403);
        assertThat(response.getBody().message())
            .isEqualTo("You do not have permission to access this account.");
        
        // Verify no internal details exposed
        assertThat(response.getBody().message())
            .doesNotContain(String.valueOf(accountId))
            .doesNotContain("SUSPENDED")
            .doesNotContain("AccountStatus");
    }
    
    @Test
    @DisplayName("3. InstrumentNotFoundException maps to INS-404 with status 404")
    void testInstrumentNotFound_Returns404_WithErrorCode() {
        // Given
        String symbol = "UNKNOWN";
        InstrumentNotFoundException ex = new InstrumentNotFoundException(symbol);
        
        // When
        ResponseEntity<ErrorResponse> response = handler.handleInstrumentNotFound(ex, webRequest);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo(ErrorCode.INS_404);
        assertThat(response.getBody().message())
            .isEqualTo("The specified instrument is not available for trading.");
        
        // Verify no internal details exposed
        assertThat(response.getBody().message())
            .doesNotContain(symbol)
            .doesNotContain("InstrumentNotFound")
            .doesNotContain("symbol");
    }
    
    @Test
    @DisplayName("4. InsufficientFundsException maps to ORD-400 with status 400")
    void testInsufficientFunds_Returns400_WithErrorCode() {
        // Given
        BigDecimal available = new BigDecimal("1000.00");
        BigDecimal required = new BigDecimal("5000.00");
        InsufficientFundsException ex = new InsufficientFundsException(available, required);
        
        // When
        ResponseEntity<ErrorResponse> response = handler.handleInsufficientFunds(ex, webRequest);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo(ErrorCode.ORD_400);
        assertThat(response.getBody().message())
            .isEqualTo("Insufficient funds to complete this order.");
        
        // Verify no financial amounts exposed in response
        assertThat(response.getBody().message())
            .doesNotContain(available.toString())
            .doesNotContain(required.toString())
            .doesNotContain("InsufficientFunds");
    }
    
    @Test
    @DisplayName("5. DuplicateOrderException maps to ORD-409 with status 409")
    void testDuplicateOrder_Returns409_WithErrorCode() {
        // Given
        String idempotencyKey = "test-key-12345";
        DuplicateOrderException ex = new DuplicateOrderException(idempotencyKey);
        
        // When
        ResponseEntity<ErrorResponse> response = handler.handleDuplicateOrder(ex, webRequest);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo(ErrorCode.ORD_409);
        assertThat(response.getBody().message())
            .contains("already been processed")
            .contains("idempotency key");
        
        // Verify no internal details exposed
        assertThat(response.getBody().message())
            .doesNotContain(idempotencyKey)
            .doesNotContain("DuplicateOrder");
    }
    
    @Test
    @DisplayName("6. InvalidOrderException maps to VAL-422 with status 422")
    void testInvalidOrder_Returns422_WithErrorCode() {
        // Given
        String validationMessage = "Quantity must be positive";
        InvalidOrderException ex = new InvalidOrderException(validationMessage);
        
        // When
        ResponseEntity<ErrorResponse> response = handler.handleInvalidOrder(ex, webRequest);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo(ErrorCode.VAL_422);
        assertThat(response.getBody().message())
            .isEqualTo(validationMessage);
    }
    
    @Test
    @DisplayName("Support Multiple Codes - Distinguish 404s by code (ACC-404 vs INS-404)")
    void testMultipleCodes_For404Status_ClientCanDistinguish() {
        // Given two different 404 scenarios
        AccountNotFoundException accountNotFound = new AccountNotFoundException(100L);
        InstrumentNotFoundException instrumentNotFound = new InstrumentNotFoundException("ABC");
        
        // When
        ResponseEntity<ErrorResponse> accountResponse = handler.handleAccountNotFound(accountNotFound, webRequest);
        ResponseEntity<ErrorResponse> instrumentResponse = handler.handleInstrumentNotFound(instrumentNotFound, webRequest);
        
        // Then both are 404, but with different codes
        assertThat(accountResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(instrumentResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        
        // But clients can distinguish by code
        assertThat(accountResponse.getBody().errorCode()).isEqualTo(ErrorCode.ACC_404);
        assertThat(instrumentResponse.getBody().errorCode()).isEqualTo(ErrorCode.INS_404);
    }
    
    @Test
    @DisplayName("Support Multiple Codes - Distinguish 409s by code (DuplicateOrder vs InsufficientHoldings)")
    void testMultipleCodes_For409Status_ClientCanDistinguish() {
        // Given two different 409 scenarios
        DuplicateOrderException duplicateOrder = new DuplicateOrderException("key1");
        InsufficientHoldingsException insufficientHoldings = new InsufficientHoldingsException(100, 200);
        
        // When
        ResponseEntity<ErrorResponse> dupResponse = handler.handleDuplicateOrder(duplicateOrder, webRequest);
        ResponseEntity<ErrorResponse> holdResponse = handler.handleInsufficientHoldings(insufficientHoldings, webRequest);
        
        // Then both are 409, but with same code (both ORD-409)
        assertThat(dupResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(holdResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        
        // Both use ORD-409 code but with different messages
        assertThat(dupResponse.getBody().errorCode()).isEqualTo(ErrorCode.ORD_409);
        assertThat(holdResponse.getBody().errorCode()).isEqualTo(ErrorCode.ORD_409);
        assertThat(dupResponse.getBody().message()).contains("already been processed");
        assertThat(holdResponse.getBody().message()).contains("holdings");
    }
    
    @Test
    @DisplayName("Response body contains no stack trace or class information")
    void testNoStackTraceExposed() {
        // Given
        AccountNotFoundException ex = new AccountNotFoundException(999L);
        
        // When
        ResponseEntity<ErrorResponse> response = handler.handleAccountNotFound(ex, webRequest);
        
        // Then verify no Java internals in response
        String responseMessage = response.getBody().message();
        assertThat(responseMessage)
            .doesNotContain("Exception")
            .doesNotContain("at java.")
            .doesNotContain("at com.tradingplatform")
            .doesNotContain("StackTrace")
            .doesNotContain("stackTrace");
    }
    
    @Test
    @DisplayName("Response body contains only message and error code for client parsing")
    void testResponseStructureForClientConsumption() {
        // Given
        InvalidOrderException ex = new InvalidOrderException("Price must be greater than zero");
        
        // When
        ResponseEntity<ErrorResponse> response = handler.handleInvalidOrder(ex, webRequest);
        
        // Then verify response is minimal and client-parseable
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.errorCode()).isNotNull();
        assertThat(body.message()).isNotNull();
        
        // ErrorResponse record provides all needed fields for clients to branch on
        assertThat(body.errorCode().code()).isEqualTo("VAL-422");
        assertThat(body.message()).contains("Price");
    }
    
    @Test
    @DisplayName("InvalidOrderException preserves validation message for user guidance")
    void testInvalidOrderExceptionPreservesValidationMessage() {
        // Given specific validation failure
        String validationMessage = "Price must be greater than zero";
        InvalidOrderException ex = new InvalidOrderException(validationMessage);
        
        // When
        ResponseEntity<ErrorResponse> response = handler.handleInvalidOrder(ex, webRequest);
        
        // Then the message helps the user understand what to fix
        assertThat(response.getBody().message())
            .isEqualTo(validationMessage)
            .contains("must")
            .doesNotContain("null")
            .doesNotContain("Exception");
    }
    
    @Test
    @DisplayName("All error responses use documented error codes for client branching")
    void testAllErrorsUseDocumentedCodes() {
        // Given various exceptions
        AccountNotFoundException acc404 = new AccountNotFoundException(1L);
        AccountNotActiveException acc403 = new AccountNotActiveException(1L, AccountStatus.SUSPENDED);
        InstrumentNotFoundException ins404 = new InstrumentNotFoundException("ABC");
        InsufficientFundsException ord400 = new InsufficientFundsException(BigDecimal.ONE, BigDecimal.TEN);
        InvalidOrderException val422 = new InvalidOrderException("Test");
        DuplicateOrderException ord409 = new DuplicateOrderException("key");
        
        // When handling each
        var r1 = handler.handleAccountNotFound(acc404, webRequest);
        var r2 = handler.handleAccountNotActive(acc403, webRequest);
        var r3 = handler.handleInstrumentNotFound(ins404, webRequest);
        var r4 = handler.handleInsufficientFunds(ord400, webRequest);
        var r5 = handler.handleInvalidOrder(val422, webRequest);
        var r6 = handler.handleDuplicateOrder(ord409, webRequest);
        
        // Then all use documented codes
        assertThat(r1.getBody().errorCode().code()).isEqualTo("ACC-404");
        assertThat(r2.getBody().errorCode().code()).isEqualTo("ACC-403");
        assertThat(r3.getBody().errorCode().code()).isEqualTo("INS-404");
        assertThat(r4.getBody().errorCode().code()).isEqualTo("ORD-400");
        assertThat(r5.getBody().errorCode().code()).isEqualTo("VAL-422");
        assertThat(r6.getBody().errorCode().code()).isEqualTo("ORD-409");
    }

    @Test
    @DisplayName("7. AccountUnauthorizedException maps to ACC-403 with status 403")
    void testAccountUnauthorized_Returns403_WithErrorCode() {
        // Given
        Long requestedAccountId = 100L;
        Long authenticatedAccountId = 200L;
        AccountUnauthorizedException ex = new AccountUnauthorizedException(requestedAccountId, authenticatedAccountId);
        
        // When
        ResponseEntity<ErrorResponse> response = handler.handleAccountUnauthorized(ex, webRequest);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo(ErrorCode.ACC_403);
        assertThat(response.getBody().message())
            .isEqualTo("You do not have permission to access this account.");
        
        // Verify no internal details exposed
        assertThat(response.getBody().message())
            .doesNotContain(String.valueOf(requestedAccountId))
            .doesNotContain(String.valueOf(authenticatedAccountId))
            .doesNotContain("Unauthorized");
    }

    @Test
    @DisplayName("8. InsufficientHoldingsException maps to ORD-409 with status 409")
    void testInsufficientHoldings_Returns409_WithErrorCode() {
        // Given
        int held = 50;
        int requested = 100;
        InsufficientHoldingsException ex = new InsufficientHoldingsException(held, requested);
        
        // When
        ResponseEntity<ErrorResponse> response = handler.handleInsufficientHoldings(ex, webRequest);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo(ErrorCode.ORD_409);
        assertThat(response.getBody().message())
            .isEqualTo("Insufficient holdings to complete this order.");
        
        // Verify no financial details exposed
        assertThat(response.getBody().message())
            .doesNotContain(String.valueOf(held))
            .doesNotContain(String.valueOf(requested))
            .doesNotContain("InsufficientHoldings");
    }

    @Test
    @DisplayName("9. OptimisticLockException maps to ORD-409 with status 409")
    void testOptimisticLock_Returns409_WithErrorCode() {
        // Given
        String resourceType = "Order";
        String resourceId = "ORD-12345";
        OptimisticLockException ex = new OptimisticLockException(resourceType, resourceId);
        
        // When
        ResponseEntity<ErrorResponse> response = handler.handleOptimisticLock(ex, webRequest);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo(ErrorCode.ORD_409);
        assertThat(response.getBody().message())
            .isEqualTo("The resource was modified by another transaction. Please retry your request.");
        
        // Verify no internal details exposed
        assertThat(response.getBody().message())
            .doesNotContain(resourceId)
            .doesNotContain(resourceType)
            .doesNotContain("OptimisticLock");
    }

    @Test
    @DisplayName("10. Generic Exception (catch-all) maps to status 500 with VAL-422")
    void testGenericException_Returns500_WithVAL422() {
        // Given a generic exception
        Exception ex = new RuntimeException("Unexpected error");
        
        // When
        ResponseEntity<ErrorResponse> response = handler.handleGenericException(ex, webRequest);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo(ErrorCode.VAL_422);
        assertThat(response.getBody().message())
            .isEqualTo("Error: Unexpected error");
        
        // Verify no stack trace or internal class names exposed
        assertThat(response.getBody().message())
            .doesNotContain("RuntimeException")
            .doesNotContain("at java.");
    }

    @Test
    @DisplayName("Support Multiple Codes - Both 403s return identical messages")
    void testMultiplePaths_For403Status_DistinctHandlers() {
        // Given two different 403 scenarios
        AccountNotActiveException notActive = new AccountNotActiveException(1L, AccountStatus.SUSPENDED);
        AccountUnauthorizedException unauthorized = new AccountUnauthorizedException(1L, 2L);
        
        // When
        ResponseEntity<ErrorResponse> notActiveResponse = handler.handleAccountNotActive(notActive, webRequest);
        ResponseEntity<ErrorResponse> unauthorizedResponse = handler.handleAccountUnauthorized(unauthorized, webRequest);
        
        // Then both are 403 with same code and identical messages for security
        assertThat(notActiveResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(unauthorizedResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        
        assertThat(notActiveResponse.getBody().errorCode()).isEqualTo(ErrorCode.ACC_403);
        assertThat(unauthorizedResponse.getBody().errorCode()).isEqualTo(ErrorCode.ACC_403);
        
        // Same message for both scenarios (doesn't reveal which check failed)
        assertThat(notActiveResponse.getBody().message())
            .isEqualTo("You do not have permission to access this account.");
        assertThat(unauthorizedResponse.getBody().message())
            .isEqualTo("You do not have permission to access this account.");
    }

    @Test
    @DisplayName("Support Multiple Codes - Distinguish 409s including OptimisticLock")
    void testMultiplePaths_For409Status_IncludingOptimisticLock() {
        // Given three different 409 scenarios
        DuplicateOrderException duplicate = new DuplicateOrderException("key1");
        InsufficientHoldingsException holdings = new InsufficientHoldingsException(100, 200);
        OptimisticLockException optimisticLock = new OptimisticLockException("Order", "ORD-123");
        
        // When
        ResponseEntity<ErrorResponse> dupResponse = handler.handleDuplicateOrder(duplicate, webRequest);
        ResponseEntity<ErrorResponse> holdResponse = handler.handleInsufficientHoldings(holdings, webRequest);
        ResponseEntity<ErrorResponse> lockResponse = handler.handleOptimisticLock(optimisticLock, webRequest);
        
        // Then all are 409 with ORD-409 code but different messages
        assertThat(dupResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(holdResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(lockResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        
        assertThat(dupResponse.getBody().errorCode()).isEqualTo(ErrorCode.ORD_409);
        assertThat(holdResponse.getBody().errorCode()).isEqualTo(ErrorCode.ORD_409);
        assertThat(lockResponse.getBody().errorCode()).isEqualTo(ErrorCode.ORD_409);
        
        // Different messages for each scenario
        assertThat(dupResponse.getBody().message()).contains("already been processed");
        assertThat(holdResponse.getBody().message()).contains("holdings");
        assertThat(lockResponse.getBody().message()).contains("modified by another transaction");
    }
}
