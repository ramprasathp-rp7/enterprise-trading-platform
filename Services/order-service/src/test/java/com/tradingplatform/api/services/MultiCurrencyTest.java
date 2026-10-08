package com.tradingplatform.api.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tradingplatform.api.events.EventType;
import com.tradingplatform.api.events.MessageEnvelope;
import com.tradingplatform.api.events.TradeEventPayload;
import com.tradingplatform.api.mappers.AccountMapper;
import com.tradingplatform.api.mappers.AssetFeeMapper;
import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.api.mappers.OrderMapper;
import com.tradingplatform.api.mappers.PositionMapper;
import com.tradingplatform.api.mappers.TransactionMapper;
import com.tradingplatform.domain.dto.PlaceOrderRequest;
import com.tradingplatform.domain.dto.PortfolioSummary;
import com.tradingplatform.domain.dto.PricedPosition;
import com.tradingplatform.domain.enums.AccountStatus;
import com.tradingplatform.domain.enums.AssetClass;
import com.tradingplatform.domain.enums.OrderSide;
import com.tradingplatform.domain.enums.TransactionType;
import com.tradingplatform.domain.exception.InsufficientFundsException;
import com.tradingplatform.domain.exception.InvalidOrderException;
import com.tradingplatform.domain.model.Account;
import com.tradingplatform.domain.model.AssetFeeStructure;
import com.tradingplatform.domain.model.Instrument;
import com.tradingplatform.domain.model.Order;
import com.tradingplatform.domain.model.Position;
import com.tradingplatform.domain.model.Transaction;
import com.tradingplatform.services.AvailableBalanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Multi-currency: orders trade in the instrument's currency; cash, fees, P&L and totals are in the
 * account's. Placement prices the funds check at the placement rate; the fill settles at the fill rate.
 */
class MultiCurrencyTest {

    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");

    /** INFY.NS trades in INR; FX:USDINR = 80 makes one rupee USD 0.0125. */
    private static final Instrument INFY = Instrument.tradable(2L, "INFY.NS", "Infosys", AssetClass.EQUITY, "INR");
    private static final Instrument AAPL = Instrument.tradable(3L, "AAPL", "Apple", AssetClass.EQUITY, "USD");

    private static FauxnanceApiClient.QuoteData quote(String symbol, String price, String currency) {
        BigDecimal p = new BigDecimal(price);
        return new FauxnanceApiClient.QuoteData(symbol, p, p, p, currency, NOW, false);
    }

    private static Account usdAccount(String cash) {
        return new Account(1L, "ACC-1", "Alice", new BigDecimal(cash), "USD", AccountStatus.ACTIVE, 0);
    }

    /** A QuoteResolver over a fixed set of quotes. */
    private static QuoteResolver resolverOf(FauxnanceApiClient.QuoteData... quotes) {
        Map<String, QuoteResolver.ResolvedQuote> bySymbol = new HashMap<>();
        for (FauxnanceApiClient.QuoteData q : quotes) {
            bySymbol.put(q.getSymbol(), new QuoteResolver.ResolvedQuote(q, false));
        }
        QuoteResolver resolver = mock(QuoteResolver.class);
        when(resolver.resolve(any())).thenAnswer(inv -> {
            Collection<String> requested = inv.getArgument(0);
            Map<String, QuoteResolver.ResolvedQuote> out = new HashMap<>();
            requested.forEach(s -> { if (bySymbol.containsKey(s)) out.put(s, bySymbol.get(s)); });
            return out;
        });
        return resolver;
    }

    @Nested
    class FxRates {
        @Test
        void sameCurrencyIsOne() {
            assertEquals(BigDecimal.ONE, FxRateService.sameCurrencyOnly().rate("usd", "USD").orElseThrow().rate());
        }

        @Test
        void inversePairIsOneOverItsPrice() {
            FxRateService fx = new FxRateService(resolverOf(quote("FX:USDINR", "80", "INR")));
            assertEquals(new BigDecimal("0.0125000000"), fx.rate("INR", "USD").orElseThrow().rate());
        }

        @Test
        void directPairIsItsPrice() {
            FxRateService fx = new FxRateService(resolverOf(quote("FX:EURUSD", "1.0850", "USD")));
            assertEquals(new BigDecimal("1.0850000000"), fx.rate("EUR", "USD").orElseThrow().rate());
        }

        @Test
        void anyCurrencyWorksAndAMissingPairIsEmpty() {
            FxRateService fx = new FxRateService(resolverOf(quote("FX:USDCHF", "0.8", "CHF")));
            assertEquals(new BigDecimal("1.2500000000"), fx.rate("CHF", "USD").orElseThrow().rate());
            assertTrue(fx.rate("XYZ", "USD").isEmpty());
            assertTrue(FxRateService.sameCurrencyOnly().rate("INR", "USD").isEmpty());
        }
    }

    @Nested
    class Placement {
        private AccountMapper accountMapper;
        private InstrumentMapper instrumentMapper;
        private PositionMapper positionMapper;
        private AssetFeeMapper assetFeeMapper;
        private AvailableBalanceService availableBalanceService;

        @BeforeEach
        void setUp() {
            accountMapper = mock(AccountMapper.class);
            instrumentMapper = mock(InstrumentMapper.class);
            positionMapper = mock(PositionMapper.class);
            assetFeeMapper = mock(AssetFeeMapper.class);
            availableBalanceService = mock(AvailableBalanceService.class);
            when(accountMapper.findByAccountId(1L)).thenReturn(usdAccount("1000.00"));
            when(instrumentMapper.findBySymbol("INFY.NS")).thenReturn(INFY);
            when(assetFeeMapper.findByAssetType("EQUITY"))
                .thenReturn(new AssetFeeStructure("EQUITY", new BigDecimal("5.00"), new BigDecimal("0.0001")));
        }

        private OrderValidationService service(FxRateService fx) {
            return new OrderValidationService(accountMapper, instrumentMapper, positionMapper, assetFeeMapper,
                availableBalanceService, fx);
        }

        private PlaceOrderRequest buyInfy(int qty, String price) {
            return new PlaceOrderRequest(1L, "INFY.NS", OrderSide.BUY, qty, new BigDecimal(price), "idem-fx-0001");
        }

        @Test
        void foreignBuyIsCheckedInAccountCurrencyAtThePlacementRate() {
            when(availableBalanceService.getAvailableBalance(anyLong())).thenReturn(new BigDecimal("1000.00"));
            FxRateService fx = new FxRateService(resolverOf(quote("FX:USDINR", "80", "INR")));

            // 40 x INR 1600 = INR 64,000 = USD 800.00; fee 5.00 + 0.08 = 5.08
            Order order = service(fx).validateAndCreateOrder(buyInfy(40, "1600.00"));

            assertEquals(new BigDecimal("0.0125000000"), order.getPlacementFxRate());
            assertEquals(new BigDecimal("5.08"), order.getFee());
            assertEquals(new BigDecimal("805.08"), order.reservedAmount());
        }

        @Test
        void foreignBuyBeyondTheAccountCurrencyBalanceIsRejected() {
            when(availableBalanceService.getAvailableBalance(anyLong())).thenReturn(new BigDecimal("800.00"));
            FxRateService fx = new FxRateService(resolverOf(quote("FX:USDINR", "80", "INR")));

            // USD 800.00 + 5.08 fee is more than USD 800.00 available (INR 64,000 would have "fitted")
            assertThrows(InsufficientFundsException.class, () -> service(fx).validateAndCreateOrder(buyInfy(40, "1600.00")));
        }

        @Test
        void missingFxRateRejectsTheOrder() {
            when(availableBalanceService.getAvailableBalance(anyLong())).thenReturn(new BigDecimal("1000.00"));
            FxRateService fx = new FxRateService(resolverOf());

            assertThrows(InvalidOrderException.class, () -> service(fx).validateAndCreateOrder(buyInfy(1, "1600.00")));
        }
    }

    @Nested
    class SyncSettlement {
        @Test
        void foreignSellBooksRealisedPnlInAccountCurrencyIncludingFx() {
            AccountMapper accountMapper = mock(AccountMapper.class);
            PositionMapper positionMapper = mock(PositionMapper.class);
            OrderMapper orderMapper = mock(OrderMapper.class);
            TransactionMapper transactionMapper = mock(TransactionMapper.class);
            Account account = usdAccount("1000.00");
            when(accountMapper.findByAccountId(1L)).thenReturn(account);
            when(accountMapper.updateAccount(any(), anyInt())).thenReturn(1);
            // 10 held at INR 800, bought when that was USD 10.00 each
            Position held = new Position(1L, 2L, 10, new BigDecimal("800"), new BigDecimal("10.00"), 1);
            when(positionMapper.findByAccountAndInstrument(1L, 2L)).thenReturn(held);
            when(positionMapper.updatePosition(any(), anyInt())).thenReturn(1);

            Order sell = new Order(1L, 2L, OrderSide.SELL, 4, new BigDecimal("800.00"), "idem-fx-0002");
            new OrderSettlementService(accountMapper, positionMapper, orderMapper, transactionMapper)
                .settle(sell, new BigDecimal("800.00"), new BigDecimal("0.0130"));

            // same INR price, stronger rupee: 4 x USD 10.40, of which 4 x 0.40 is FX gain
            assertEquals(new BigDecimal("1041.60"), account.getCashBalance());
            verify(orderMapper).recordFill(sell.getId(), new BigDecimal("0.0130"), new BigDecimal("1.60"));
            ArgumentCaptor<Transaction> tx = ArgumentCaptor.forClass(Transaction.class);
            verify(transactionMapper).insertTransaction(tx.capture());
            assertEquals(new BigDecimal("41.60"), tx.getValue().getAmount());
        }
    }

    @Nested
    class FeeDeduction {
        @Test
        void settlementTransactionAndFeeAreInAccountCurrencyAtTheFillRate() throws Exception {
            OrderMapper orderMapper = mock(OrderMapper.class);
            AccountMapper accountMapper = mock(AccountMapper.class);
            InstrumentMapper instrumentMapper = mock(InstrumentMapper.class);
            AssetFeeMapper assetFeeMapper = mock(AssetFeeMapper.class);
            TransactionMapper transactionMapper = mock(TransactionMapper.class);
            ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

            UUID orderId = UUID.randomUUID();
            Order order = new Order(orderId, 1L, 2L, OrderSide.BUY, 40, new BigDecimal("1600.00"), "idem-fx-0003");
            order.markFilled(new BigDecimal("1600.00"));
            when(orderMapper.findOrderById(orderId)).thenReturn(order);
            when(instrumentMapper.findById(2L)).thenReturn(INFY);
            when(assetFeeMapper.findByAssetType("EQUITY"))
                .thenReturn(new AssetFeeStructure("EQUITY", new BigDecimal("5.00"), new BigDecimal("0.0001")));
            when(accountMapper.findByAccountId(1L)).thenReturn(usdAccount("5000.00"));
            when(accountMapper.updateAccount(any(), anyInt())).thenReturn(1);

            // the rupee moved between placement (80) and fill (78.125): fill rate 0.0128
            TradeEventPayload payload = new TradeEventPayload(orderId.toString(), 1L, "INFY.NS", "BUY", 40,
                new BigDecimal("1600.00"), new BigDecimal("1600.00"), new BigDecimal("1600.00"), new BigDecimal("5.08"),
                "FILLED", null, null, null, null, NOW, new BigDecimal("0.0128"));
            String message = objectMapper.writeValueAsString(new MessageEnvelope<>(UUID.randomUUID().toString(),
                EventType.ORDER_FILLED, NOW, com.tradingplatform.api.events.Source.TRADE_EXECUTOR, 1, payload));

            new FeeDeductionService(orderMapper, accountMapper, instrumentMapper, assetFeeMapper, transactionMapper,
                objectMapper).handleOrderFilled(message, null);

            ArgumentCaptor<Transaction> tx = ArgumentCaptor.forClass(Transaction.class);
            verify(transactionMapper, times(2)).insertTransaction(tx.capture());
            assertEquals(TransactionType.BUY, tx.getAllValues().get(0).getTransactionType());
            assertEquals(new BigDecimal("819.20"), tx.getAllValues().get(0).getAmount()); // INR 64,000 x 0.0128
            assertEquals(new BigDecimal("5.08"), tx.getAllValues().get(1).getAmount());   // 5.00 + 0.01% of 819.20
        }
    }

    @Nested
    class Portfolio {
        private AccountMapper accountMapper;
        private InstrumentMapper instrumentMapper;
        private RealisedPnlCalculator realisedPnlCalculator;

        @BeforeEach
        void setUp() {
            accountMapper = mock(AccountMapper.class);
            instrumentMapper = mock(InstrumentMapper.class);
            realisedPnlCalculator = mock(RealisedPnlCalculator.class);
            when(accountMapper.findByAccountId(1L)).thenReturn(usdAccount("1000.00"));
            when(instrumentMapper.findById(2L)).thenReturn(INFY);
            when(instrumentMapper.findById(3L)).thenReturn(AAPL);
            when(accountMapper.findPositionByAccountId(1L)).thenReturn(List.of(
                // 40 INFY.NS at INR 1500, USD 18.00 each at purchase; 10 AAPL at USD 100
                new Position(1L, 2L, 40, new BigDecimal("1500"), new BigDecimal("18.00"), 1),
                new Position(1L, 3L, 10, new BigDecimal("100"), new BigDecimal("100"), 1)));
            when(realisedPnlCalculator.calculateTotalRealisedPnl(1L)).thenReturn(new BigDecimal("23.96"));
        }

        @Test
        void totalsAreInAccountCurrencyWithForeignHoldingsConverted() {
            PortfolioPnlService service = new PortfolioPnlService(accountMapper, instrumentMapper,
                resolverOf(quote("INFY.NS", "1600", "INR"), quote("AAPL", "110", "USD"), quote("FX:USDINR", "80", "INR")),
                realisedPnlCalculator);

            PortfolioSummary summary = service.getPortfolioSummary(1L);

            // INFY: INR 64,000 / 80 = USD 800.00 against USD 720.00 cost; AAPL: 1100.00 against 1000.00
            assertEquals("USD", summary.getBaseCurrency());
            assertEquals(new BigDecimal("1900.00"), summary.getMarketValue());
            assertEquals(new BigDecimal("1720.00"), summary.getCostBasis());
            assertEquals(new BigDecimal("180.00"), summary.getUnrealisedPnl());
            assertEquals(new BigDecimal("2900.00"), summary.getTotalValue());
            assertFalse(summary.getPartial());

            PricedPosition infy = service.getPricedPositions(1L, "INFY.NS").get(0);
            assertEquals("INR", infy.getCurrency());
            assertEquals(new BigDecimal("64000"), infy.getMarketValue());
            assertEquals(new BigDecimal("800.00"), infy.getMarketValueAccount());
            assertEquals(new BigDecimal("0.0125000000"), infy.getFxRate());
        }

        @Test
        void aHoldingWithNoFxRateIsLeftOutAndMarksThePortfolioPartial() {
            PortfolioPnlService service = new PortfolioPnlService(accountMapper, instrumentMapper,
                resolverOf(quote("INFY.NS", "1600", "INR"), quote("AAPL", "110", "USD")),
                realisedPnlCalculator);

            PortfolioSummary summary = service.getPortfolioSummary(1L);

            assertEquals(new BigDecimal("1100.00"), summary.getMarketValue());
            assertTrue(summary.getPartial());
            PricedPosition infy = service.getPricedPositions(1L, "INFY.NS").get(0);
            assertNull(infy.getMarketValueAccount());
            assertTrue(infy.getStale());
        }
    }
}
