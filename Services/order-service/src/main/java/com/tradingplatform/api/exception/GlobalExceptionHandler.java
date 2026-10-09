package com.tradingplatform.api.exception;

import com.tradingplatform.api.services.PortfolioPnlService;
import com.tradingplatform.domain.dto.ErrorResponse;
import com.tradingplatform.domain.enums.ErrorCode;
import com.tradingplatform.domain.exception.*;
import com.tradingplatform.watchlist.exception.WatchlistConflictException;
import com.tradingplatform.watchlist.exception.WatchlistNotFoundException;
import com.tradingplatform.watchlist.alerts.exception.PriceAlertConflictException;
import com.tradingplatform.watchlist.alerts.exception.PriceAlertNotFoundException;
import com.tradingplatform.strategy.exception.StrategyConflictException;
import com.tradingplatform.strategy.exception.StrategyNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;


// Global exception handler for the trading platform API.

@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationFailure(
            AuthenticationException ex,
            WebRequest request) {

        logger.warn("Authentication failed: {}", ex.getClass().getSimpleName());

        return new ResponseEntity<>(
            new ErrorResponse(ErrorCode.AUTH_401, "Unauthorised"),
            HttpStatus.UNAUTHORIZED
        );
    }
    
   
    // ACC-404: Account not found Maps to HTTP 404 (Not Found)

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(
            AccountNotFoundException ex,
            WebRequest request) {
        
        logger.error("Account not found: accountId={}", ex.getAccountId());
        
        return new ResponseEntity<>(
            new ErrorResponse(ErrorCode.ACC_404, "The requested account could not be found."),
            HttpStatus.NOT_FOUND
        );
    }
    
    
    // ACC-403: Unauthorized access to account (token does not reach account) Maps to HTTP 403 (Forbidden)
     
    @ExceptionHandler(AccountUnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleAccountUnauthorized(
            AccountUnauthorizedException ex,
            WebRequest request) {
        
        logger.error("Unauthorized account access: requestedAccountId={}, authenticatedAccountId={}", 
            ex.getAccountId(), ex.getAuthenticatedAccountId());
        
        return new ResponseEntity<>(
            new ErrorResponse(ErrorCode.ACC_403, "You do not have permission to access this account."),
            HttpStatus.FORBIDDEN
        );
    }
    
    //ACC-403: Account exists but is not active Maps to HTTP 403 (Forbidden)
    
    @ExceptionHandler(AccountNotActiveException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotActive(
            AccountNotActiveException ex,
            WebRequest request) {
        
        logger.error("Account not active: accountId={}, status={}", 
            ex.getAccountId(), ex.getActualStatus());
        
        return new ResponseEntity<>(
            new ErrorResponse(ErrorCode.ACC_403, "You do not have permission to access this account."),
            HttpStatus.FORBIDDEN
        );
    }
    
    // INS-404: Instrument not found or untradable Maps to HTTP 404 (Not Found)
     
    @ExceptionHandler(InstrumentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleInstrumentNotFound(
            InstrumentNotFoundException ex,
            WebRequest request) {
        
        logger.error("Instrument not found: symbol={}", ex.getSymbol());
        
        return new ResponseEntity<>(
            new ErrorResponse(ErrorCode.INS_404, "The specified instrument is not available for trading."),
            HttpStatus.NOT_FOUND
        );
    }
    
    // INS-404 (symbol discovery): Fauxnance does not recognise the symbol or has no quote for it
    
    @ExceptionHandler(SymbolNotDiscoveredException.class)
    public ResponseEntity<ErrorResponse> handleSymbolNotDiscovered(
            SymbolNotDiscoveredException ex,
            WebRequest request) {
        
        logger.info("Symbol not discovered: symbol={}, reason={}", ex.getSymbol(), ex.getMessage());
        
        return new ResponseEntity<>(
            new ErrorResponse(ErrorCode.INS_404, ex.getMessage()),
            HttpStatus.NOT_FOUND
        );
    }
    
    // MKT-503: The market data provider is unavailable Maps to HTTP 503 (Service Unavailable)
    
    @ExceptionHandler(MarketDataUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleMarketDataUnavailable(
            MarketDataUnavailableException ex,
            WebRequest request) {
        
        logger.warn("Market data provider unavailable: {}", ex.getMessage());
        
        return new ResponseEntity<>(
            new ErrorResponse(ErrorCode.MKT_503, "Market data is unavailable right now. Please try again shortly."),
            HttpStatus.SERVICE_UNAVAILABLE
        );
    }
    
    // MKT-404: No cached quote for the symbol Maps to HTTP 404 (Not Found)
    
    @ExceptionHandler(QuoteNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleQuoteNotFound(
            QuoteNotFoundException ex,
            WebRequest request) {
        
        logger.warn("No cached quote: symbol={}", ex.getSymbol());
        
        return new ResponseEntity<>(
            new ErrorResponse(ErrorCode.MKT_404, "No price is available for this symbol yet."),
            HttpStatus.NOT_FOUND
        );
    }
    
    // ORD-409: Order not found Maps to HTTP 404 (Not Found)
    // Per OpenAPI spec: DELETE /api/v1/orders/{id} returns HTTP 404 with ORD-409 error code
    
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFound(
            OrderNotFoundException ex,
            WebRequest request) {
        
        logger.error("Order not found: orderId={}", ex.getOrderId());
        
        return new ResponseEntity<>(
            new ErrorResponse(ErrorCode.ORD_409, "The requested order could not be found."),
            HttpStatus.NOT_FOUND
        );
    }
    
    // TRANS-404: Transaction not found Maps to HTTP 404 (Not Found)
    
    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTransactionNotFound(
            TransactionNotFoundException ex,
            WebRequest request) {
        
        logger.error("Transaction not found: {}", ex.getMessage());
        
        return new ResponseEntity<>(
            new ErrorResponse(ErrorCode.TRANS_404, "The requested transaction could not be found."),
            HttpStatus.NOT_FOUND
        );
    }
    
    // TRANS-400: Invalid transaction Maps to HTTP 400 (Bad Request)
    
    @ExceptionHandler(InvalidTransactionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransaction(
            InvalidTransactionException ex,
            WebRequest request) {
        
        logger.error("Invalid transaction: {}", ex.getMessage());
        
        return new ResponseEntity<>(
            new ErrorResponse(ErrorCode.TRANS_400, ex.getMessage()),
            HttpStatus.BAD_REQUEST
        );
    }
    
    // TRANS-400: Insufficient funds for withdrawal Maps to HTTP 400 (Bad Request)
    
    @ExceptionHandler(InsufficientFundsForWithdrawalException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFundsForWithdrawal(
            InsufficientFundsForWithdrawalException ex,
            WebRequest request) {
        
        logger.error("Insufficient funds for withdrawal: requested={}, available={}", 
            ex.getRequestedAmount(), ex.getAvailableBalance());
        
        return new ResponseEntity<>(
            new ErrorResponse(ErrorCode.TRANS_400, ex.getMessage()),
            HttpStatus.BAD_REQUEST
        );
    }
    
    // ORD-400: Insufficient funds for order Maps to HTTP 400 (Bad Request)
    
    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFunds(
            InsufficientFundsException ex,
            WebRequest request) {
        
        logger.error("Insufficient funds: available={}, required={}", 
            ex.getAvailable(), ex.getRequired());
        
        return new ResponseEntity<>(
            new ErrorResponse(ErrorCode.ORD_400, "Insufficient funds to complete this order."),
            HttpStatus.BAD_REQUEST
        );
    }
    
    // ORD-409: Insufficient holdings for order (conflict state) Maps to HTTP 409 (Conflict)
    
    @ExceptionHandler(InsufficientHoldingsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientHoldings(
            InsufficientHoldingsException ex,
            WebRequest request) {
        
        logger.error("Insufficient holdings: held={}, requested={}", 
            ex.getHeld(), ex.getRequested());
        
        return new ResponseEntity<>(
            new ErrorResponse(ErrorCode.ORD_409, "Insufficient holdings to complete this order."),
            HttpStatus.CONFLICT
        );
    }
    
    // ORD-409: Duplicate idempotency key (order conflict) Maps to HTTP 409 (Conflict)
    
    @ExceptionHandler(DuplicateOrderException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateOrder(
            DuplicateOrderException ex,
            WebRequest request) {
        
        logger.error("Duplicate idempotency key detected: key={}", 
            ex.getIdempotencyKey());
        
        return new ResponseEntity<>(
            new ErrorResponse(ErrorCode.ORD_409, "This order has already been processed. Please verify the result or use a new idempotency key."),
            HttpStatus.CONFLICT
        );
    }
    
    // ORD-409: Order not cancellable (not in NEW status) Maps to HTTP 409 (Conflict)
    
    @ExceptionHandler(OrderNotCancellableException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotCancellable(
            OrderNotCancellableException ex,
            WebRequest request) {
        
        logger.error("Order not cancellable: orderId={}", ex.getOrderId());
        
        return new ResponseEntity<>(
            new ErrorResponse(ErrorCode.ORD_409, "The order is not in a cancellable state."),
            HttpStatus.CONFLICT
        );
    }
    
    // WL-404: Watchlist or watchlist item not found for this account Maps to HTTP 404 (Not Found)

    @ExceptionHandler(WatchlistNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleWatchlistNotFound(
            WatchlistNotFoundException ex,
            WebRequest request) {

        logger.error("Watchlist not found: watchlistId={}", ex.getWatchlistId());

        return new ResponseEntity<>(
            new ErrorResponse(ErrorCode.WL_404, ex.getMessage()),
            HttpStatus.NOT_FOUND
        );
    }

    // WL-409: Duplicate watchlist name or symbol already in the watchlist Maps to HTTP 409 (Conflict)

    @ExceptionHandler(WatchlistConflictException.class)
    public ResponseEntity<ErrorResponse> handleWatchlistConflict(
            WatchlistConflictException ex,
            WebRequest request) {

        logger.error("Watchlist conflict: {}", ex.getMessage());

        return new ResponseEntity<>(
            new ErrorResponse(ErrorCode.WL_409, ex.getMessage()),
            HttpStatus.CONFLICT
        );
    }

    @ExceptionHandler(CandleDataNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCandleDataNotFound(
            CandleDataNotFoundException ex,
            WebRequest request) {

        logger.error("Candle data not found: instrumentId={}, date={}", ex.getInstrumentId(), ex.getDate());

        return new ResponseEntity<>(
            new ErrorResponse(ErrorCode.CDL_404, "Candle data not found for requested date."),
            HttpStatus.NOT_FOUND
        );
    }
    // ALR-404: Price alert not found for this account Maps to HTTP 404 (Not Found)

    @ExceptionHandler(PriceAlertNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePriceAlertNotFound(
            PriceAlertNotFoundException ex,
            WebRequest request) {

        logger.error("Price alert not found: alertId={}", ex.getAlertId());

        return new ResponseEntity<>(
            new ErrorResponse(ErrorCode.ALR_404, ex.getMessage()),
            HttpStatus.NOT_FOUND
        );
    }

    @ExceptionHandler(CandleDataException.class)
    public ResponseEntity<ErrorResponse> handleCandleDataException(
            CandleDataException ex,
            WebRequest request) {

        logger.error("Candle data error: {}", ex.getMessage());

        return new ResponseEntity<>(
            new ErrorResponse(ErrorCode.VAL_422, ex.getMessage()),
            HttpStatus.UNPROCESSABLE_ENTITY
        );
    }

    // ALR-409: Alert cap reached or the same active alert already exists Maps to HTTP 409 (Conflict)

    @ExceptionHandler(StrategyNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleStrategyNotFound(StrategyNotFoundException ex, WebRequest request) {
        logger.error("Strategy not found: {}", ex.getMessage());
        return new ResponseEntity<>(new ErrorResponse(ErrorCode.STR_404, ex.getMessage()), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(StrategyConflictException.class)
    public ResponseEntity<ErrorResponse> handleStrategyConflict(StrategyConflictException ex, WebRequest request) {
        logger.error("Strategy conflict: {}", ex.getMessage());
        return new ResponseEntity<>(new ErrorResponse(ErrorCode.STR_409, ex.getMessage()), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(PriceAlertConflictException.class)
    public ResponseEntity<ErrorResponse> handlePriceAlertConflict(
            PriceAlertConflictException ex,
            WebRequest request) {

        logger.error("Price alert conflict: {}", ex.getMessage());

        return new ResponseEntity<>(
            new ErrorResponse(ErrorCode.ALR_409, ex.getMessage()),
            HttpStatus.CONFLICT
        );
    }

    // VAL-422: Invalid order data (quantity, price, etc.) Maps to HTTP 422 (Unprocessable Entity)
     
    @ExceptionHandler(InvalidOrderException.class)
    public ResponseEntity<ErrorResponse> handleInvalidOrder(
            InvalidOrderException ex,
            WebRequest request) {
        
        logger.error("Invalid order data: {}", ex.getMessage());
        
        return new ResponseEntity<>(
            new ErrorResponse(ErrorCode.VAL_422, ex.getMessage()),
            HttpStatus.UNPROCESSABLE_ENTITY
        );
    }

    // PREF-422: Invalid preference data (key, value, format, etc.) Maps to HTTP 422 (Unprocessable Entity)

    @ExceptionHandler(InvalidPreferenceException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPreference(
            InvalidPreferenceException ex,
            WebRequest request) {

        logger.error("Invalid preference data: {}", ex.getMessage());

        return new ResponseEntity<>(
            new ErrorResponse(ErrorCode.INVALID_PREFERENCE, ex.getMessage()),
            HttpStatus.UNPROCESSABLE_ENTITY
        );
    }

    // VAL-422: Invalid portfolio P&L request (e.g. from after to) Maps to HTTP 422 (Unprocessable Entity)

    @ExceptionHandler(PortfolioPnlService.PortfolioPnlException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPnlRequest(
            PortfolioPnlService.PortfolioPnlException ex,
            WebRequest request) {

        logger.error("Invalid P&L request: {}", ex.getMessage());

        return new ResponseEntity<>(
            new ErrorResponse(ErrorCode.VAL_422, ex.getMessage()),
            HttpStatus.UNPROCESSABLE_ENTITY
        );
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    public ResponseEntity<ErrorResponse> handleValidationException(
            Exception ex,
            WebRequest request) {
        logger.error("Validation error: {}", ex.getMessage());
        
        return new ResponseEntity<>(
            new ErrorResponse(ErrorCode.VAL_422, "Invalid input"),
            HttpStatus.UNPROCESSABLE_ENTITY
        );
    }
    
    // ORD-409: Optimistic lock conflict (concurrent modification) Maps to HTTP 409 (Conflict)
    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(
            OptimisticLockException ex,
            WebRequest request) {
        
        logger.error("Optimistic lock conflict: resourceType={}, resourceId={}", 
            ex.getResourceType(), ex.getResourceId());
        
        return new ResponseEntity<>(
            new ErrorResponse(ErrorCode.ORD_409, "The resource was modified by another transaction. Please retry your request."),
            HttpStatus.CONFLICT
        );
    }
    
    // Catch-all handler for any DomainException not explicitly handled
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(
            DomainException ex,
            WebRequest request) {
        
        logger.error("Unhandled domain exception: code={}, message={}", 
            ex.getCode(), ex.getMessage(), ex);
        
        HttpStatus status = mapErrorCodeToHttpStatus(ex.getErrorCode());
        return new ResponseEntity<>(
            new ErrorResponse(ex.getErrorCode(), ex.getMessage()),
            status
        );
    }
    
    // Handle malformed request bodies
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            WebRequest request) {
        
        logger.warn("Malformed request body");
        
        return new ResponseEntity<>(
            new ErrorResponse(ErrorCode.VAL_422, "Invalid input"),
            HttpStatus.UNPROCESSABLE_ENTITY
        );
    }
    
    // Handle unsupported HTTP methods
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            WebRequest request) {
        
        logger.warn("HTTP method not supported: {}", ex.getMethod());
        
        return new ResponseEntity<>(
            new ErrorResponse(ErrorCode.VAL_422, "Invalid input"),
            HttpStatus.METHOD_NOT_ALLOWED
        );
    }
    
    // Handle resource not found (404 for non-mapped endpoints)
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException ex,
            WebRequest request) {
        
        logger.warn("Resource not found: {} {}", ex.getHttpMethod(), ex.getResourcePath());
        
        return new ResponseEntity<>(
            new ErrorResponse(ErrorCode.VAL_422, "Invalid input"),
            HttpStatus.NOT_FOUND
        );
    }
    
    /**
     * Maps error codes to their corresponding HTTP status.
     */
    private HttpStatus mapErrorCodeToHttpStatus(ErrorCode code) {
        return switch(code) {
            case AUTH_401 -> HttpStatus.UNAUTHORIZED;
            case ACC_404, INS_404, TRANS_404, WL_404, ALR_404, CDL_404, MKT_404, STR_404, ASS_404 -> HttpStatus.NOT_FOUND;
            case ACC_403 -> HttpStatus.FORBIDDEN;
            case ORD_400, TRANS_400 -> HttpStatus.BAD_REQUEST;
            case ORD_409, WL_409, ALR_409, STR_409 -> HttpStatus.CONFLICT;
            case VAL_422, INVALID_PREFERENCE -> HttpStatus.UNPROCESSABLE_ENTITY;
            case MKT_503 -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }
    
    //Catch-all for unexpected exceptions
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            WebRequest request) {
        
        String errorMessage = ex.getMessage() != null ? ex.getMessage() : "Unexpected error";
        logger.error("Unexpected error: {} - {}", ex.getClass().getSimpleName(), errorMessage, ex);
        
        return new ResponseEntity<>(
            new ErrorResponse(ErrorCode.VAL_422, "Error: " + errorMessage),
            HttpStatus.INTERNAL_SERVER_ERROR
        );
    }
}
