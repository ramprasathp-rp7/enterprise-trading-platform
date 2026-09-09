package com.tradingplatform.api.controllers;

import com.tradingplatform.domain.dto.ErrorResponse;
import com.tradingplatform.domain.enums.ErrorCode;
import com.tradingplatform.domain.exception.DomainException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(DomainException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case "ACC-404", "INS-404" -> HttpStatus.NOT_FOUND;
            case "ACC-403" -> HttpStatus.FORBIDDEN;
            case "ORD-400" -> HttpStatus.BAD_REQUEST;
            case "ORD-409" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };

        return ResponseEntity.status(status)
            .body(new ErrorResponse(ErrorCode.fromCode(ex.getCode()), ex.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    public ResponseEntity<ErrorResponse> handleValidation(Exception ex) {
        String message = "Invalid input";
        if (ex instanceof MethodArgumentNotValidException methodArgumentNotValidException) {
            FieldError firstError = methodArgumentNotValidException.getBindingResult().getFieldError();
            if (firstError != null && firstError.getDefaultMessage() != null) {
                message = firstError.getDefaultMessage();
            }
        }

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(new ErrorResponse(ErrorCode.VAL_422, message));
    }
}


