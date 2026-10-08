package com.tradingplatform.api.characterisation;

import com.tradingplatform.api.exception.GlobalExceptionHandler;
import com.tradingplatform.api.mappers.AccountMapper;
import com.tradingplatform.api.mappers.AssetFeeMapper;
import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.api.mappers.OrderMapper;
import com.tradingplatform.api.mappers.PositionMapper;
import com.tradingplatform.api.security.AccountAccessAuthorizer;
import com.tradingplatform.api.services.OrderPublisher;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.WebRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderPlacementCharacterisationTest {

    private AccountMapper accountMapper;
    private InstrumentMapper instrumentMapper;
    private PositionMapper positionMapper;
    private OrderMapper orderMapper;
    private AssetFeeMapper assetFeeMapper;
    private AccountAccessAuthorizer accountAccessAuthorizer;
    private OrderPublisher orderPublisher;

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
        assetFeeMapper = mock(AssetFeeMapper.class);
        accountAccessAuthorizer = mock(AccountAccessAuthorizer.class);
        orderPublisher = mock(OrderPublisher.class);

        // Add mocks for new dependencies
        var availableBalanceService = mock(com.tradingplatform.services.AvailableBalanceService.class);
        // Configure mock to return account's cash balance as available balance
        when(availableBalanceService.getAvailableBalance(anyLong()))
                .thenAnswer(invocation -> {
                    // Return the cash balance of the mocked account
                    Account acc = accountMapper.findByAccountId(invocation.getArgument(0));
                    return acc != null ? acc.getCashBalance() : BigDecimal.ZERO;
                });

        OrderValidationService validationService =
                new OrderValidationService(
                        accountMapper,
                        instrumentMapper,
                        positionMapper,
                        assetFeeMapper,
                        availableBalanceService
                );

        OrderSettlementService settlementService =
                mock(OrderSettlementService.class);

        orderService = new OrderService(
                validationService,
                settlementService,
                orderMapper,
                accountMapper,
                instrumentMapper,
                assetFeeMapper,
                accountAccessAuthorizer,
                orderPublisher
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

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void affordableBuy_pinsSprint7NewOrderBehaviour() {
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
        when(positionMapper.findByAccountAndInstrument(1L, 2L))
                .thenReturn(position);

        when(orderMapper.insertOrder(
                any(UUID.class),
                eq(1L),
                eq(2L),
                eq(OrderSide.BUY),
                eq(2),
                eq(new BigDecimal("50.00")),
                eq(OrderStatus.NEW),
                eq("idem-12345"),
                eq(BigDecimal.ONE),
                eq(true)  // ASYNC: ORDER_PLACED pending
        )).thenReturn(1);

        TransactionSynchronizationManager.initSynchronization();

        OrderResponse response = orderService.placeOrder(request);

        assertNotNull(response.orderId());
        assertTrue(response.orderId().startsWith("ORD-"));
        assertEquals(OrderStatus.NEW, response.status());
        assertEquals("Order accepted", response.message());
        assertEquals("AAPL", response.symbol());
        assertEquals(OrderSide.BUY, response.side());
        assertEquals(2, response.quantity());
        assertEquals(new BigDecimal("50.00"), response.price());
        assertEquals(BigDecimal.ZERO, response.fee());

        verify(orderMapper).insertOrder(
                any(UUID.class),
                eq(1L),
                eq(2L),
                eq(OrderSide.BUY),
                eq(2),
                eq(new BigDecimal("50.00")),
                eq(OrderStatus.NEW),
                eq("idem-12345"),
                eq(BigDecimal.ONE),
                eq(true)  // ASYNC: ORDER_PLACED pending
        );

        /*
         * Sprint 7 order placement must not settle the order.
         * Therefore cash and position must remain unchanged.
         */
        assertEquals(new BigDecimal("1000.00"), account.getCashBalance());
        assertEquals(0, position.getQuantity());
        assertEquals(new BigDecimal("0.000000"), position.getAverageCost());

        verify(accountMapper, never())
                .updateAccount(any(Account.class), anyInt());

        verify(positionMapper, never())
                .updatePosition(any(Position.class), anyInt());

        verify(orderMapper, never()).updateOrderStatus(
                any(UUID.class),
                any(OrderStatus.class),
                any(BigDecimal.class)
        );

        verify(orderMapper, never())
                .findOrderById(any(UUID.class));

        /*
         * The event is registered for after-commit publication.
         * Execute the registered callback to model a successful transaction commit.
         */
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();

        assertEquals(1, synchronizations.size());

        synchronizations.get(0).afterCommit();

        verify(orderPublisher).publishOrderPlaced(
                any(Order.class),
                eq(instrument)
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
        when(positionMapper.findByAccountAndInstrument(1L, 2L))
                .thenReturn(position);

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
                        eq("idem-12345"),
                        eq(BigDecimal.ONE),
                        eq(true)  // ASYNC: ORDER_PLACED pending
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

        verify(orderPublisher, never())
                .publishOrderPlaced(any(Order.class), any(Instrument.class));
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
        when(positionMapper.findByAccountAndInstrument(1L, 2L))
                .thenReturn(position);

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