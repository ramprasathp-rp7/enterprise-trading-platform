package com.tradingplatform.api.characterisation;

import com.tradingplatform.api.exception.GlobalExceptionHandler;
import com.tradingplatform.api.mappers.AccountMapper;
import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.api.mappers.OrderMapper;
import com.tradingplatform.api.mappers.PositionMapper;
import com.tradingplatform.api.security.AccountAccessAuthorizer;
import com.tradingplatform.api.services.OrderService;
import com.tradingplatform.api.services.OrderSettlementService;
import com.tradingplatform.api.services.OrderValidationService;
import com.tradingplatform.domain.dto.ErrorResponse;
import com.tradingplatform.domain.dto.OrderResponse;
import com.tradingplatform.domain.dto.PlaceOrderRequest;
import com.tradingplatform.domain.enums.AccountStatus;
import com.tradingplatform.domain.enums.AssetClass;
import com.tradingplatform.domain.enums.ErrorCode;
import com.tradingplatform.domain.enums.OrderSide;
import com.tradingplatform.domain.enums.OrderStatus;
import com.tradingplatform.domain.exception.AccountNotActiveException;
import com.tradingplatform.domain.exception.DuplicateOrderException;
import com.tradingplatform.domain.exception.InsufficientFundsException;
import com.tradingplatform.domain.exception.InstrumentNotFoundException;
import com.tradingplatform.domain.model.Account;
import com.tradingplatform.domain.model.Instrument;
import com.tradingplatform.domain.model.Order;
import com.tradingplatform.domain.model.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderPlacementCharacterisationTest {

    private AccountMapper accountMapper;
    private InstrumentMapper instrumentMapper;
    private PositionMapper positionMapper;
    private OrderMapper orderMapper;
    private AccountAccessAuthorizer accountAccessAuthorizer;

    private OrderService orderService;
    private GlobalExceptionHandler exceptionHandler;

    private Account account;
    private Instrument instrument;
    private Position position;

    @BeforeEach
    void setUp() {
        accountMapper = mock(AccountMapper.class);
        instrumentMapper = mock(InstrumentMapper.class);
        positionMapper = mock(PositionMapper.class);
        orderMapper = mock(OrderMapper.class);
        accountAccessAuthorizer = mock(AccountAccessAuthorizer.class);

        OrderValidationService validationService =
                new OrderValidationService(
                        accountMapper,
                        instrumentMapper,
                        positionMapper
                );

        OrderSettlementService settlementService =
                new OrderSettlementService(
                        accountMapper,
                        positionMapper,
                        orderMapper
                );

        orderService = new OrderService(
                validationService,
                settlementService,
                orderMapper,
                instrumentMapper,
                accountAccessAuthorizer
        );

        exceptionHandler = new GlobalExceptionHandler();

        account = new Account(
                1L,
                "ACC-001",
                "Test Customer",
                new BigDecimal("1000.00"),
                "USD",
                AccountStatus.ACTIVE,
                0
        );

        instrument = Instrument.tradable(
                2L,
                "AAPL",
                "Apple Inc.",
                AssetClass.EQUITY,
                "USD"
        );

        position = Position.empty(1L, 2L);
    }

    @Test
    void affordableBuy_pinsResponseAndPersistenceBehaviour() {
        PlaceOrderRequest request = new PlaceOrderRequest(
                1L,
                "AAPL",
                OrderSide.BUY,
                2,
                new BigDecimal("50.00"),
                "idem-12345"
        );

        when(accountMapper.findByAccountId(1L)).thenReturn(account);
        when(instrumentMapper.findBySymbol("AAPL")).thenReturn(instrument);
        when(instrumentMapper.findById(2L)).thenReturn(instrument);
        when(positionMapper.findByAccountAndInstrument(1L, 2L)).thenReturn(position);

        when(accountMapper.updateAccount(any(Account.class), eq(1))).thenReturn(1);
        when(positionMapper.updatePosition(any(Position.class), eq(1))).thenReturn(1);

        when(orderMapper.updateOrderStatus(
                any(UUID.class),
                eq(OrderStatus.FILLED),
                eq(new BigDecimal("50.00"))
        )).thenReturn(1);

        when(orderMapper.findOrderById(any(UUID.class)))
                .thenAnswer(invocation -> {
                    Order order = new Order(
                            1L,
                            2L,
                            OrderSide.BUY,
                            2,
                            new BigDecimal("50.00"),
                            "idem-12345"
                    );
                    order.markFilled(new BigDecimal("50.00"));
                    return order;
                });

        OrderResponse response = orderService.placeOrder(request);

        assertNotNull(response.orderId());
        assertTrue(response.orderId().startsWith("ORD-"));
        assertEquals(OrderStatus.FILLED, response.status());
        assertEquals("Order executed", response.message());
        assertEquals("AAPL", response.symbol());
        assertEquals(OrderSide.BUY, response.side());
        assertEquals(2, response.quantity());
        assertEquals(new BigDecimal("50.00"), response.price());

        verify(orderMapper).insertOrder(
                any(UUID.class),
                eq(1L),
                eq(2L),
                eq(OrderSide.BUY),
                eq(2),
                eq(new BigDecimal("50.00")),
                eq(OrderStatus.NEW),
                eq("idem-12345")
        );

        verify(accountMapper).updateAccount(
                eq(account),
                eq(1)
        );
        assertEquals(new BigDecimal("900.00"), account.getCashBalance());

        verify(positionMapper).updatePosition(
                eq(position),
                eq(1)
        );
        assertEquals(2, position.getQuantity());
        assertEquals(new BigDecimal("50.000000"), position.getAverageCost());

        verify(orderMapper).updateOrderStatus(
                any(UUID.class),
                eq(OrderStatus.FILLED),
                eq(new BigDecimal("50.00"))
        );
    }

    @Test
    void reusedIdempotencyKey_pinsErrorCodeAndHttpStatus() {
        PlaceOrderRequest request = new PlaceOrderRequest(
                1L,
                "AAPL",
                OrderSide.BUY,
                2,
                new BigDecimal("50.00"),
                "idem-12345"
        );

        when(accountMapper.findByAccountId(1L)).thenReturn(account);
        when(instrumentMapper.findBySymbol("AAPL")).thenReturn(instrument);
        when(positionMapper.findByAccountAndInstrument(1L, 2L)).thenReturn(position);
        doThrow(new DuplicateKeyException("duplicate"))
                .when(orderMapper)
                .insertOrder(
                        any(UUID.class),
                        eq(1L),
                        eq(2L),
                        eq(OrderSide.BUY),
                        eq(2),
                        eq(new BigDecimal("50.00")),
                        eq(OrderStatus.NEW),
                        eq("idem-12345")
                );

        DuplicateOrderException exception =
                assertThrows(
                        DuplicateOrderException.class,
                        () -> orderService.placeOrder(request)
                );

        ResponseEntity<ErrorResponse> response =
                exceptionHandler.handleDuplicateOrder(
                        exception,
                        mock(WebRequest.class)
                );

        assertEquals(ErrorCode.ORD_409, response.getBody().errorCode());
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    void unaffordableBuy_pinsErrorCodeAndHttpStatus() {
        account = new Account(
                1L,
                "ACC-001",
                "Test Customer",
                new BigDecimal("50.00"),
                "USD",
                AccountStatus.ACTIVE,
                0
        );

        PlaceOrderRequest request = new PlaceOrderRequest(
                1L,
                "AAPL",
                OrderSide.BUY,
                2,
                new BigDecimal("50.00"),
                "idem-12345"
        );

        when(accountMapper.findByAccountId(1L)).thenReturn(account);
        when(instrumentMapper.findBySymbol("AAPL")).thenReturn(instrument);
        when(positionMapper.findByAccountAndInstrument(1L, 2L)).thenReturn(position);

        InsufficientFundsException exception =
                assertThrows(
                        InsufficientFundsException.class,
                        () -> orderService.placeOrder(request)
                );

        ResponseEntity<ErrorResponse> response =
                exceptionHandler.handleInsufficientFunds(
                        exception,
                        mock(WebRequest.class)
                );

        assertEquals(ErrorCode.ORD_400, response.getBody().errorCode());
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void unknownSymbol_pinsErrorCodeAndHttpStatus() {
        PlaceOrderRequest request = new PlaceOrderRequest(
                1L,
                "UNKNOWN",
                OrderSide.BUY,
                2,
                new BigDecimal("50.00"),
                "idem-12345"
        );

        when(accountMapper.findByAccountId(1L)).thenReturn(account);
        when(instrumentMapper.findBySymbol("UNKNOWN")).thenReturn(null);

        InstrumentNotFoundException exception =
                assertThrows(
                        InstrumentNotFoundException.class,
                        () -> orderService.placeOrder(request)
                );

        ResponseEntity<ErrorResponse> response =
                exceptionHandler.handleInstrumentNotFound(
                        exception,
                        mock(WebRequest.class)
                );

        assertEquals(ErrorCode.INS_404, response.getBody().errorCode());
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void inactiveAccount_pinsErrorCodeAndHttpStatus() {
        account = new Account(
                1L,
                "ACC-001",
                "Test Customer",
                new BigDecimal("1000.00"),
                "USD",
                AccountStatus.SUSPENDED,
                0
        );

        PlaceOrderRequest request = new PlaceOrderRequest(
                1L,
                "AAPL",
                OrderSide.BUY,
                2,
                new BigDecimal("50.00"),
                "idem-12345"
        );

        when(accountMapper.findByAccountId(1L)).thenReturn(account);

        AccountNotActiveException exception =
                assertThrows(
                        AccountNotActiveException.class,
                        () -> orderService.placeOrder(request)
                );

        ResponseEntity<ErrorResponse> response =
                exceptionHandler.handleAccountNotActive(
                        exception,
                        mock(WebRequest.class)
                );

        assertEquals(ErrorCode.ACC_403, response.getBody().errorCode());
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}