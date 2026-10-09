package com.tradingplatform.api.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.tradingplatform.domain.dto.ErrorResponse;
import com.tradingplatform.domain.enums.ErrorCode;
import com.tradingplatform.domain.exception.CandleDataException;
import com.tradingplatform.domain.exception.CandleDataNotFoundException;
import com.tradingplatform.domain.exception.CandleSyncException;
import com.tradingplatform.domain.exception.DomainException;
import com.tradingplatform.domain.exception.InvalidTransactionException;
import com.tradingplatform.domain.exception.OrderNotCancellableException;
import com.tradingplatform.domain.exception.OrderNotFoundException;
import com.tradingplatform.strategy.exception.StrategyConflictException;
import com.tradingplatform.strategy.exception.StrategyNotFoundException;
import com.tradingplatform.watchlist.alerts.exception.PriceAlertConflictException;
import com.tradingplatform.watchlist.alerts.exception.PriceAlertNotFoundException;
import com.tradingplatform.watchlist.exception.WatchlistConflictException;
import com.tradingplatform.watchlist.exception.WatchlistNotFoundException;

/**
 * Every domain exception maps to its catalogue code and HTTP status, and internal details never
 * reach the client where the catalogue says so.
 */
class GlobalExceptionHandlerMappingTest {

    private static final GlobalExceptionHandler HANDLER = new GlobalExceptionHandler();
    private static final WebRequest REQUEST = new ServletWebRequest(new MockHttpServletRequest());

    static Stream<Arguments> handlers() {
        UUID orderId = UUID.randomUUID();
        return Stream.of(
            Arguments.of("order not found",
                (Supplier<ResponseEntity<ErrorResponse>>) () -> HANDLER.handleOrderNotFound(new OrderNotFoundException(orderId, "Order " + orderId), REQUEST),
                HttpStatus.NOT_FOUND, ErrorCode.ORD_409, "The requested order could not be found."),
            Arguments.of("invalid transaction",
                (Supplier<ResponseEntity<ErrorResponse>>) () -> HANDLER.handleInvalidTransaction(new InvalidTransactionException("Amount must be positive"), REQUEST),
                HttpStatus.BAD_REQUEST, ErrorCode.TRANS_400, "Amount must be positive"),
            Arguments.of("order not cancellable",
                (Supplier<ResponseEntity<ErrorResponse>>) () -> HANDLER.handleOrderNotCancellable(new OrderNotCancellableException("ORD-1"), REQUEST),
                HttpStatus.CONFLICT, ErrorCode.ORD_409, "The order is not in a cancellable state."),
            Arguments.of("watchlist not found",
                (Supplier<ResponseEntity<ErrorResponse>>) () -> HANDLER.handleWatchlistNotFound(new WatchlistNotFoundException(4L, "Watchlist 4 not found"), REQUEST),
                HttpStatus.NOT_FOUND, ErrorCode.WL_404, "Watchlist 4 not found"),
            Arguments.of("watchlist conflict",
                (Supplier<ResponseEntity<ErrorResponse>>) () -> HANDLER.handleWatchlistConflict(new WatchlistConflictException("Name already used"), REQUEST),
                HttpStatus.CONFLICT, ErrorCode.WL_409, "Name already used"),
            Arguments.of("candle data not found",
                (Supplier<ResponseEntity<ErrorResponse>>) () -> HANDLER.handleCandleDataNotFound(new CandleDataNotFoundException(2L, LocalDate.of(2026, 10, 9)), REQUEST),
                HttpStatus.NOT_FOUND, ErrorCode.CDL_404, "Candle data not found for requested date."),
            Arguments.of("price alert not found",
                (Supplier<ResponseEntity<ErrorResponse>>) () -> HANDLER.handlePriceAlertNotFound(new PriceAlertNotFoundException(9L), REQUEST),
                HttpStatus.NOT_FOUND, ErrorCode.ALR_404, null),
            Arguments.of("candle data problem",
                (Supplier<ResponseEntity<ErrorResponse>>) () -> HANDLER.handleCandleDataException(new CandleSyncException("Range too large"), REQUEST),
                HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.VAL_422, "Range too large"),
            Arguments.of("strategy not found",
                (Supplier<ResponseEntity<ErrorResponse>>) () -> HANDLER.handleStrategyNotFound(new StrategyNotFoundException(3L), REQUEST),
                HttpStatus.NOT_FOUND, ErrorCode.STR_404, null),
            Arguments.of("strategy conflict",
                (Supplier<ResponseEntity<ErrorResponse>>) () -> HANDLER.handleStrategyConflict(new StrategyConflictException("Already active"), REQUEST),
                HttpStatus.CONFLICT, ErrorCode.STR_409, "Already active"),
            Arguments.of("price alert conflict",
                (Supplier<ResponseEntity<ErrorResponse>>) () -> HANDLER.handlePriceAlertConflict(new PriceAlertConflictException("Too many alerts"), REQUEST),
                HttpStatus.CONFLICT, ErrorCode.ALR_409, "Too many alerts"),
            Arguments.of("unsupported method",
                (Supplier<ResponseEntity<ErrorResponse>>) () -> HANDLER.handleHttpRequestMethodNotSupported(new HttpRequestMethodNotSupportedException("PATCH"), REQUEST),
                HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.VAL_422, "Invalid input"),
            Arguments.of("unmapped path",
                (Supplier<ResponseEntity<ErrorResponse>>) () -> HANDLER.handleNoResourceFound(new NoResourceFoundException(HttpMethod.GET, "/api/v1/nope"), REQUEST),
                HttpStatus.NOT_FOUND, ErrorCode.VAL_422, "Invalid input"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("handlers")
    void mapsToTheCatalogue(String name, Supplier<ResponseEntity<ErrorResponse>> handle, HttpStatus status,
                            ErrorCode code, String message) {
        ResponseEntity<ErrorResponse> response = handle.get();

        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody().errorCode()).isEqualTo(code);
        if (message != null) {
            assertThat(response.getBody().message()).isEqualTo(message);
        }
    }

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    void otherDomainExceptionsUseTheStatusOfTheirCode(ErrorCode code) {
        DomainException ex = new DomainException(code, "Something about " + code) { };

        ResponseEntity<ErrorResponse> response = HANDLER.handleDomainException(ex, REQUEST);

        HttpStatus expected = switch (code.code().substring(code.code().lastIndexOf('-') + 1)) {
            case "401" -> HttpStatus.UNAUTHORIZED;
            case "403" -> HttpStatus.FORBIDDEN;
            case "404" -> HttpStatus.NOT_FOUND;
            case "400" -> HttpStatus.BAD_REQUEST;
            case "409" -> HttpStatus.CONFLICT;
            case "503" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        assertThat(response.getStatusCode()).isEqualTo(expected);
        assertThat(response.getBody().errorCode()).isEqualTo(code);
        assertThat(ex.getCode()).isEqualTo(code.code());
    }

    @Test
    void errorCodesRoundTripThroughTheirWireValue() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(ErrorCode.fromCode(code.code())).isEqualTo(code);
            assertThat(code).hasToString(code.code());
        }
        assertThatThrownBy(() -> ErrorCode.fromCode("NOPE-000")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exceptionsKeepTheirDetailsForLogs() {
        UUID orderId = UUID.randomUUID();
        Throwable cause = new IllegalStateException("db");

        assertThat(new OrderNotFoundException(orderId, "gone").getOrderId()).isEqualTo(orderId);
        assertThat(new OrderNotFoundException("gone").getOrderId()).isNull();
        assertThat(new OrderNotCancellableException("ORD-7").getOrderId()).isEqualTo("ORD-7");
        CandleDataNotFoundException missing = new CandleDataNotFoundException(2L, LocalDate.of(2026, 10, 9));
        assertThat(missing.getInstrumentId()).isEqualTo(2L);
        assertThat(missing.getDate()).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(new InvalidTransactionException("bad", cause).getCause()).isSameAs(cause);
        assertThat(new CandleSyncException("sync", cause).getCause()).isSameAs(cause);
        assertThat(new CandleDataException("data", cause)).hasMessage("data");
        assertThat(new WatchlistNotFoundException(4L).getWatchlistId()).isEqualTo(4L);
    }
}
