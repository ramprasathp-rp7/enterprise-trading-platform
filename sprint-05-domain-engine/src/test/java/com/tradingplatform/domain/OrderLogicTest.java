package com.tradingplatform.domain;

import com.tradingplatform.domain.dto.PlaceOrderRequest;
import com.tradingplatform.domain.enums.AccountStatus;
import com.tradingplatform.domain.enums.AssetClass;
import com.tradingplatform.domain.enums.OrderSide;
import com.tradingplatform.domain.enums.OrderStatus;
import com.tradingplatform.domain.exception.AccountNotActiveException;
import com.tradingplatform.domain.exception.AccountNotFoundException;
import com.tradingplatform.domain.exception.DuplicateOrderException;
import com.tradingplatform.domain.exception.InstrumentNotFoundException;
import com.tradingplatform.domain.exception.InsufficientFundsException;
import com.tradingplatform.domain.exception.InsufficientHoldingsException;
import com.tradingplatform.domain.exception.InvalidOrderException;
import com.tradingplatform.domain.model.Account;
import com.tradingplatform.domain.model.Instrument;
import com.tradingplatform.domain.model.Order;
import com.tradingplatform.domain.model.Position;
import com.tradingplatform.domain.rules.IdempotencyKeyChecker;
import com.tradingplatform.domain.rules.OrderPlacementService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderLogicTest {

    private final OrderPlacementService service = new OrderPlacementService();

    // Never reports any key as used
    private static final IdempotencyKeyChecker NEVER_USED = key -> false;

    private static Account activeAccount(String cash) {
        return new Account(1L, "ACC-000001", "Priya Menon", new BigDecimal(cash), "USD",
            AccountStatus.ACTIVE, 0);
    }

    private static Account inactiveAccount(AccountStatus status, String cash) {
        return new Account(1L, "ACC-000001", "Priya Menon", new BigDecimal(cash), "USD", status, 0);
    }

    private static Instrument tradableInstrument() {
        return Instrument.tradable(1L, "ACME", "Acme Corp", AssetClass.EQUITY, "USD");
    }

    private static Instrument delistedInstrument() {
        return Instrument.delisted(1L, "OLDCO", "Old Company Inc", AssetClass.EQUITY, "USD");
    }

    private static PlaceOrderRequest buyRequest(int quantity, String price, String idempotencyKey) {
        return new PlaceOrderRequest(1L, "ACME", OrderSide.BUY, quantity, new BigDecimal(price), idempotencyKey);
    }

    private static PlaceOrderRequest sellRequest(int quantity, String price, String idempotencyKey) {
        return new PlaceOrderRequest(1L, "ACME", OrderSide.SELL, quantity, new BigDecimal(price), idempotencyKey);
    }

    // A key checker backed by a set
    private static IdempotencyKeyChecker usedKeys(String... alreadyUsed) {
        Set<String> keys = new HashSet<>(Set.of(alreadyUsed));
        return keys::contains;
    }

    // --- rule 1: the account must exist -------------------------------------------------------

    @Test
    void rule1Fires_orderRejectedOnAccountThatDoesNotExist() {
        AccountNotFoundException ex = assertThrows(AccountNotFoundException.class, () ->
            service.placeOrder(buyRequest(10, "25.50", "idem-key-0001"), Optional.empty(),
                Optional.of(tradableInstrument()), Position.empty(1L, 1L), NEVER_USED));
        assertEquals("ACC-404", ex.getCode());
    }

    @Test
    void rule1DoesNotFire_whenAccountExists() {
        assertThrows(InstrumentNotFoundException.class, () ->
            service.placeOrder(buyRequest(10, "25.50", "idem-key-0001"), Optional.of(activeAccount("1000.00")),
                Optional.empty(), Position.empty(1L, 1L), NEVER_USED));
    }

    // --- rule 2: the account must be ACTIVE -------------------------------------------------------

    @ParameterizedTest(name = "rule2Fires_orderRejectedOnAccountThatIs{0}")
    @EnumSource(value = AccountStatus.class, names = {"SUSPENDED", "CLOSED"})
    void rule2Fires_orderRejectedOnInactiveAccount(AccountStatus status) {
        AccountNotActiveException ex = assertThrows(AccountNotActiveException.class, () ->
            service.placeOrder(buyRequest(10, "25.50", "idem-key-0001"), Optional.of(inactiveAccount(status, "1000.00")),
                Optional.of(tradableInstrument()), Position.empty(1L, 1L), NEVER_USED));
        assertEquals("ACC-403", ex.getCode());
        assertEquals(status, ex.getActualStatus());
    }

    @Test
    void rule2DoesNotFire_forActiveAccount() {
        Order order = service.placeOrder(buyRequest(10, "25.50", "idem-key-0001"),
            Optional.of(activeAccount("1000.00")), Optional.of(tradableInstrument()),
            Position.empty(1L, 1L), NEVER_USED);
        assertEquals(OrderStatus.FILLED, order.getStatus());
    }

    // --- rule 3: the instrument must exist and be tradable -------------------------------------------------------

    @Test
    void rule3Fires_onUnknownAndOnDelistedInstruments() {
        assertAll(
            () -> {
                InstrumentNotFoundException ex = assertThrows(InstrumentNotFoundException.class, () ->
                    service.placeOrder(buyRequest(10, "25.50", "idem-key-0001"), Optional.of(activeAccount("1000.00")),
                        Optional.empty(), Position.empty(1L, 1L), NEVER_USED));
                assertEquals("INS-404", ex.getCode());
            },
            () -> assertThrows(InstrumentNotFoundException.class, () ->
                service.placeOrder(buyRequest(10, "25.50", "idem-key-0002"), Optional.of(activeAccount("1000.00")),
                    Optional.of(delistedInstrument()), Position.empty(1L, 1L), NEVER_USED))
        );
    }

    @Test
    void rule3DoesNotFire_forTradableInstrument() {
        Order order = service.placeOrder(buyRequest(10, "25.50", "idem-key-0001"),
            Optional.of(activeAccount("1000.00")), Optional.of(tradableInstrument()),
            Position.empty(1L, 1L), NEVER_USED);
        assertEquals(OrderStatus.FILLED, order.getStatus());
    }

    // --- rules 4 and 5: quantity and price must be greater than zero -------------------------------------------------------

    @Test
    void rules4And5Fire_onNonPositiveQuantityOrPrice() {
        assertAll(
            () -> {
                InvalidOrderException ex = assertThrows(InvalidOrderException.class, () ->
                    service.placeOrder(buyRequest(0, "25.50", "idem-key-0001"), Optional.of(activeAccount("1000.00")),
                        Optional.of(tradableInstrument()), Position.empty(1L, 1L), NEVER_USED));
                assertEquals("VAL-422", ex.getCode());
            },
            () -> {
                InvalidOrderException ex = assertThrows(InvalidOrderException.class, () ->
                    service.placeOrder(buyRequest(10, "0.00", "idem-key-0002"), Optional.of(activeAccount("1000.00")),
                        Optional.of(tradableInstrument()), Position.empty(1L, 1L), NEVER_USED));
                assertEquals("VAL-422", ex.getCode());
            }
        );
    }

    @Test
    void rule4DoesNotFire_forPositiveQuantity() {
        Order order = service.placeOrder(buyRequest(1, "25.50", "idem-key-0001"),
            Optional.of(activeAccount("1000.00")), Optional.of(tradableInstrument()),
            Position.empty(1L, 1L), NEVER_USED);
        assertEquals(OrderStatus.FILLED, order.getStatus());
    }

    @Test
    void rule5DoesNotFire_forPositivePrice() {
        Order order = service.placeOrder(buyRequest(10, "0.01", "idem-key-0001"),
            Optional.of(activeAccount("1000.00")), Optional.of(tradableInstrument()),
            Position.empty(1L, 1L), NEVER_USED);
        assertEquals(OrderStatus.FILLED, order.getStatus());
    }

    // --- rule 6: BUY affordability -------------------------------------------------------

    @Test
    void rule6Fires_buyRejectedOnInsufficientFunds() {
        InsufficientFundsException ex = assertThrows(InsufficientFundsException.class, () ->
            service.placeOrder(buyRequest(10, "25.50", "idem-key-0001"), Optional.of(activeAccount("100.00")),
                Optional.of(tradableInstrument()), Position.empty(1L, 1L), NEVER_USED));
        assertEquals("ORD-400", ex.getCode());
        assertEquals(new BigDecimal("100.00"), ex.getAvailable());
        assertEquals(new BigDecimal("255.00"), ex.getRequired());
    }

    @Test
    void rule6DoesNotFire_buySucceedsAndUpdatesCashAndPosition() {
        Account account = activeAccount("1000.00");
        Position position = Position.empty(1L, 1L);

        Order order = service.placeOrder(buyRequest(10, "25.50", "idem-key-0001"), Optional.of(account),
            Optional.of(tradableInstrument()), position, NEVER_USED);

        assertEquals(OrderStatus.FILLED, order.getStatus());
        assertEquals(new BigDecimal("25.50"), order.getExecutedPrice());
        // 1000.00 - (10 * 25.50) = 745.00
        assertEquals(new BigDecimal("745.00"), account.getCashBalance());
        assertEquals(10, position.getQuantity());
        assertEquals(new BigDecimal("25.500000"), position.getAverageCost());
    }

    // --- rule 7: SELL holdings -------------------------------------------------------

    @Test
    void rule7Fires_sellRejectedOnInsufficientHoldings() {
        Position position = Position.empty(1L, 1L);
        position.applyBuy(5, new BigDecimal("20.00"));

        InsufficientHoldingsException ex = assertThrows(InsufficientHoldingsException.class, () ->
            service.placeOrder(sellRequest(6, "25.50", "idem-key-0001"), Optional.of(activeAccount("1000.00")),
                Optional.of(tradableInstrument()), position, NEVER_USED));
        assertEquals("ORD-409", ex.getCode());
        assertEquals(5, ex.getHeld());
        assertEquals(6, ex.getRequested());
    }

    @Test
    void rule7DoesNotFire_sellSucceedsAndUpdatesCashAndPosition() {
        Account account = activeAccount("1000.00");
        Position position = Position.empty(1L, 1L);
        position.applyBuy(10, new BigDecimal("20.00"));

        Order order = service.placeOrder(sellRequest(4, "25.50", "idem-key-0001"), Optional.of(account),
            Optional.of(tradableInstrument()), position, NEVER_USED);

        assertEquals(OrderStatus.FILLED, order.getStatus());
        // 1000.00 + (4 * 25.50) = 1102.00
        assertEquals(new BigDecimal("1102.00"), account.getCashBalance());
        assertEquals(6, position.getQuantity());
        // Average cost is left untouched by a sell.
        assertEquals(new BigDecimal("20.000000"), position.getAverageCost());
    }

    // --- rule 8: idempotency key must not already be used -------------------------------------------------------

    @Test
    void rule8Fires_duplicateIdempotencyKeyRejected() {
        DuplicateOrderException ex = assertThrows(DuplicateOrderException.class, () ->
            service.placeOrder(buyRequest(10, "25.50", "idem-key-0001"), Optional.of(activeAccount("1000.00")),
                Optional.of(tradableInstrument()), Position.empty(1L, 1L), usedKeys("idem-key-0001")));
        assertEquals("ORD-409", ex.getCode());
        assertEquals("idem-key-0001", ex.getIdempotencyKey());
    }

    @Test
    void rule8DoesNotFire_forAFreshIdempotencyKey() {
        Order order = service.placeOrder(buyRequest(10, "25.50", "idem-key-0002"),
            Optional.of(activeAccount("1000.00")), Optional.of(tradableInstrument()),
            Position.empty(1L, 1L), usedKeys("idem-key-0001"));
        assertEquals(OrderStatus.FILLED, order.getStatus());
    }

    // --- evaluation order: first failure wins -------------------------------------------------------

    @Test
    void evaluationOrder_suspendedAccountWithNoCashGetsAccountNotActiveNotInsufficientFunds() {
        Account account = inactiveAccount(AccountStatus.SUSPENDED, "0.00");
        AccountNotActiveException ex = assertThrows(AccountNotActiveException.class, () ->
            service.placeOrder(buyRequest(10, "25.50", "idem-key-0001"), Optional.of(account),
                Optional.of(tradableInstrument()), Position.empty(1L, 1L), NEVER_USED));
        assertEquals("ACC-403", ex.getCode());
    }
}