package com.tradingplatform.domain.exception;

import com.tradingplatform.domain.dto.ErrorResponse;
import com.tradingplatform.domain.enums.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;


// Global exception handler for the trading platform API.

@RestControllerAdvice
public class GlobalExceptionHandler {
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

    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
   
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
            new ErrorResponse(ErrorCode.ACC_403, "This account is not active and cannot be used for trading."),
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

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    public ResponseEntity<ErrorResponse> handleValidationException(
            Exception ex,
            WebRequest request) {
        String message = "Invalid input";
        if (ex instanceof MethodArgumentNotValidException methodArgumentNotValidException) {
            FieldError firstError = methodArgumentNotValidException.getBindingResult().getFieldError();
            if (firstError != null && firstError.getDefaultMessage() != null) {
                message = firstError.getDefaultMessage();
            }
        }

        return new ResponseEntity<>(
            new ErrorResponse(ErrorCode.VAL_422, message),
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
        
        return new ResponseEntity<>(
            new ErrorResponse(ex.getErrorCode(), ex.getMessage()),
            HttpStatus.INTERNAL_SERVER_ERROR
        );
    }
    
    //Catch-all for unexpected exceptions
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            WebRequest request) {
        
        logger.error("Unexpected error occurred", ex);
        
        return new ResponseEntity<>(
            new ErrorResponse(ErrorCode.VAL_422, "An unexpected error occurred. Please contact support."),
            HttpStatus.INTERNAL_SERVER_ERROR
        );
    }
}
