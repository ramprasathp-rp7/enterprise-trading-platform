package com.tradingplatform.strategy.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.api.events.QuotePayload;
import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.api.mappers.PositionMapper;
import com.tradingplatform.api.services.MarketDataCache;
import com.tradingplatform.domain.enums.OrderSide;
import com.tradingplatform.domain.model.Instrument;
import com.tradingplatform.domain.model.Position;
import com.tradingplatform.strategy.client.OrderPlacementException;
import com.tradingplatform.strategy.client.StrategyTokenIssuer;
import com.tradingplatform.strategy.client.TradeApiOrderClient;
import com.tradingplatform.strategy.mapper.StrategyExecutionMapper;
import com.tradingplatform.strategy.mapper.TradingStrategyMapper;
import com.tradingplatform.strategy.model.ExecutionOutcome;
import com.tradingplatform.strategy.model.IndicatorState;
import com.tradingplatform.strategy.model.StrategyExecution;
import com.tradingplatform.strategy.model.StrategySignal;
import com.tradingplatform.strategy.model.StrategyType;
import com.tradingplatform.strategy.model.TradeSide;
import com.tradingplatform.strategy.model.TradingStrategy;

class StrategyEvaluatorTest {

    private static final Instant T0 = Instant.parse("2026-10-07T14:00:00Z");

    private MarketDataCache cache;
    private TradingStrategyMapper strategyMapper;
    private StrategyExecutionMapper executionMapper;
    private PositionMapper positionMapper;
    private TradeApiOrderClient orderClient;
    private StrategyEvaluator evaluator;
    private TradingStrategy strategy;

    @BeforeEach
    void setUp() {
        cache = new MarketDataCache(Duration.ofDays(3650));
        strategyMapper = mock(TradingStrategyMapper.class);
        executionMapper = mock(StrategyExecutionMapper.class);
        InstrumentMapper instrumentMapper = mock(InstrumentMapper.class);
        positionMapper = mock(PositionMapper.class);
        StrategyTokenIssuer tokenIssuer = mock(StrategyTokenIssuer.class);
        orderClient = mock(TradeApiOrderClient.class);

        Instrument aapl = mock(Instrument.class);
        when(aapl.getId()).thenReturn(2L);
        when(instrumentMapper.findBySymbol("AAPL")).thenReturn(aapl);
        when(tokenIssuer.issue(any())).thenReturn("tok");
        when(executionMapper.sumPlacedNotionalSince(anyLong(), any())).thenReturn(BigDecimal.ZERO);
        when(orderClient.placeOrder(anyString(), anyLong(), anyString(), any(), anyInt(), any(), anyString()))
            .thenReturn(new TradeApiOrderClient.PlacedOrder("ORD-1", false));

        strategy = new TradingStrategy();
        strategy.setStrategyId(7L);
        strategy.setAccountId(101L);
        strategy.setSymbol("AAPL");
        strategy.setStrategyType(StrategyType.MA_CROSSOVER);
        strategy.setShortWindow(2);
        strategy.setLongWindow(4);
        strategy.setTradeSide(TradeSide.BOTH);
        strategy.setQuantity(5);
        strategy.setMaxPosition(100);
        strategy.setMaxDailyNotional(new BigDecimal("10000"));
        strategy.setMaxConsecutiveFailures(3);
        strategy.setActive(true);
        strategy.setLastState(IndicatorState.BELOW);
        when(strategyMapper.findActiveSymbols()).thenReturn(List.of("AAPL"));
        when(strategyMapper.findActiveBySymbol("AAPL")).thenReturn(List.of(strategy));
        when(strategyMapper.lockById(7L)).thenAnswer(inv -> strategy);

        StrategyExecutor executor = new StrategyExecutor(strategyMapper, executionMapper, instrumentMapper, positionMapper,
            tokenIssuer, orderClient, new ObjectMapper(), Clock.fixed(T0, ZoneOffset.UTC));
        evaluator = new StrategyEvaluator(cache, strategyMapper, executor, 0);
    }

    /** One quote a minute, observed from T0; bid/ask 0.10 either side */
    private void quotes(double... prices) {
        for (int i = 0; i < prices.length; i++) {
            BigDecimal p = BigDecimal.valueOf(prices[i]);
            QuotePayload q = new QuotePayload("AAPL", p, p.subtract(new BigDecimal("0.10")), p.add(new BigDecimal("0.10")),
                "USD", null, null, null, null, false, T0.plusSeconds(60L * i));
            cache.updateQuote("AAPL", q, Instant.now());
        }
    }

    private Instant newest(int count) {
        return T0.plusSeconds(60L * (count - 1));
    }

    private StrategyExecution recorded() {
        ArgumentCaptor<StrategyExecution> captor = ArgumentCaptor.forClass(StrategyExecution.class);
        verify(executionMapper).insert(captor.capture());
        return captor.getValue();
    }

    private void heldQuantity(int quantity) {
        Position position = mock(Position.class);
        when(position.getQuantity()).thenReturn(quantity);
        when(positionMapper.findByAccountAndInstrument(101L, 2L)).thenReturn(position);
    }

    @Test
    void triggersOnACrossoverAndPlacesTheOrderThroughTheTradeApi() {
        quotes(100, 98, 103, 105);

        evaluator.evaluateAll();

        String key = "STRAT-7-" + newest(4).toEpochMilli();
        verify(orderClient).placeOrder("tok", 101L, "AAPL", OrderSide.BUY, 5, new BigDecimal("105.10"), key);
        verify(strategyMapper).recordEvaluation(7L, IndicatorState.ABOVE, newest(4));
        verify(strategyMapper).resetFailures(7L);
        StrategyExecution execution = recorded();
        assertEquals(ExecutionOutcome.PLACED, execution.getOutcome());
        assertEquals(StrategySignal.BUY, execution.getSignal());
        assertEquals("ORD-1", execution.getOrderId());
        assertEquals(new BigDecimal("525.50"), execution.getNotional());
        assertTrue(execution.getIndicatorsJson().contains("\"shortMa\":104"));
        assertTrue(execution.getReason().contains("crossed above"));
    }

    @Test
    void takesNoActionWhenConditionsAreNotMet() {
        strategy.setLastState(IndicatorState.ABOVE);
        quotes(100, 98, 103, 105);

        evaluator.evaluateAll();

        verify(orderClient, never()).placeOrder(anyString(), anyLong(), anyString(), any(), anyInt(), any(), anyString());
        verify(executionMapper, never()).insert(any());
        verify(strategyMapper).recordEvaluation(7L, IndicatorState.ABOVE, newest(4));
    }

    @Test
    void waitsForEnoughHistoryWithoutTrading() {
        quotes(100, 105);

        evaluator.evaluateAll();

        verify(orderClient, never()).placeOrder(anyString(), anyLong(), anyString(), any(), anyInt(), any(), anyString());
        verify(strategyMapper).recordEvaluation(7L, IndicatorState.BELOW, newest(2));
    }

    @Test
    void ignoresSignalsOnASideTheStrategyDoesNotTrade() {
        strategy.setTradeSide(TradeSide.SELL_ONLY);
        quotes(100, 98, 103, 105);

        evaluator.evaluateAll();

        verify(orderClient, never()).placeOrder(anyString(), anyLong(), anyString(), any(), anyInt(), any(), anyString());
        verify(strategyMapper).recordEvaluation(7L, IndicatorState.ABOVE, newest(4));
    }

    @Test
    void doesNotEvaluateTheSameQuoteTwice() {
        quotes(100, 98, 103, 105);
        strategy.setLastEvaluatedQuoteAt(newest(4));

        evaluator.evaluateAll();

        verify(strategyMapper, never()).recordEvaluation(anyLong(), any(), any());
        verify(strategyMapper, never()).lockById(anyLong());
    }

    @Test
    void repeatedCopiesOfOneObservationCountOnce() {
        quotes(100, 98, 103, 105);
        // The cache refreshes again with the same observation (market closed): no new evaluation
        strategy.setLastEvaluatedQuoteAt(newest(4));
        BigDecimal p = BigDecimal.valueOf(105);
        cache.updateQuote("AAPL", new QuotePayload("AAPL", p, p, p, "USD", null, null, null, null, true, newest(4)), Instant.now());

        assertEquals(4, PricePoint.series(cache.getQuoteHistory("AAPL")).size());
        evaluator.evaluateAll();
        verify(strategyMapper, never()).lockById(anyLong());
    }

    @Test
    void placesNothingWhenTheStrategyWasDisabledMidCycle() {
        quotes(100, 98, 103, 105);
        TradingStrategy disabled = new TradingStrategy();
        disabled.setStrategyId(7L);
        disabled.setAccountId(101L);
        disabled.setSymbol("AAPL");
        disabled.setQuantity(5);
        disabled.setActive(false);
        // Evaluated as active, but disabled by the time the executor locks the row
        when(strategyMapper.lockById(7L)).thenReturn(disabled);

        evaluator.evaluateAll();

        verify(orderClient, never()).placeOrder(anyString(), anyLong(), anyString(), any(), anyInt(), any(), anyString());
        StrategyExecution execution = recorded();
        assertEquals(ExecutionOutcome.SKIPPED_DISABLED, execution.getOutcome());
        assertTrue(execution.getReason().endsWith("Not placed: the strategy was turned off."));
    }

    @Test
    void stopsAtTheMaxPosition() {
        strategy.setMaxPosition(12);
        heldQuantity(10);
        quotes(100, 98, 103, 105);

        evaluator.evaluateAll();

        verify(orderClient, never()).placeOrder(anyString(), anyLong(), anyString(), any(), anyInt(), any(), anyString());
        assertEquals(ExecutionOutcome.SKIPPED_LIMIT, recorded().getOutcome());
    }

    @Test
    void stopsAtTheDailySpendLimit() {
        strategy.setMaxDailyNotional(new BigDecimal("1000"));
        when(executionMapper.sumPlacedNotionalSince(eq(7L), eq(Instant.parse("2026-10-07T00:00:00Z")))).thenReturn(new BigDecimal("600"));
        quotes(100, 98, 103, 105);

        evaluator.evaluateAll();

        verify(orderClient, never()).placeOrder(anyString(), anyLong(), anyString(), any(), anyInt(), any(), anyString());
        StrategyExecution execution = recorded();
        assertEquals(ExecutionOutcome.SKIPPED_LIMIT, execution.getOutcome());
        assertTrue(execution.getReason().contains("daily limit of 1000"));
    }

    @Test
    void doesNotSellWhatIsNotHeld() {
        strategy.setLastState(IndicatorState.ABOVE);
        quotes(105, 103, 98, 96);

        evaluator.evaluateAll();

        verify(orderClient, never()).placeOrder(anyString(), anyLong(), anyString(), any(), anyInt(), any(), anyString());
        assertEquals(ExecutionOutcome.SKIPPED_NO_POSITION, recorded().getOutcome());
    }

    @Test
    void sellsAtTheBidWhenHeld() {
        strategy.setLastState(IndicatorState.ABOVE);
        heldQuantity(20);
        quotes(105, 103, 98, 96);

        evaluator.evaluateAll();

        verify(orderClient).placeOrder(eq("tok"), eq(101L), eq("AAPL"), eq(OrderSide.SELL), eq(5), eq(new BigDecimal("95.90")), anyString());
    }

    @Test
    void countsAFailureAndReportsWhenTheStrategyStops() {
        strategy.setConsecutiveFailures(2);
        when(orderClient.placeOrder(anyString(), anyLong(), anyString(), any(), anyInt(), any(), anyString()))
            .thenThrow(new OrderPlacementException("VAL-422", "Insufficient funds", null));
        quotes(100, 98, 103, 105);

        evaluator.evaluateAll();

        verify(strategyMapper).recordFailure(7L);
        verify(strategyMapper, never()).resetFailures(anyLong());
        StrategyExecution execution = recorded();
        assertEquals(ExecutionOutcome.FAILED, execution.getOutcome());
        assertEquals("VAL-422", execution.getErrorCode());
        assertTrue(execution.getReason().contains("turned off after 3 failures"));
    }

    @Test
    void oneFailingStrategyDoesNotStopTheOthers() {
        TradingStrategy broken = new TradingStrategy();
        broken.setStrategyId(8L);
        broken.setStrategyType(StrategyType.BOLLINGER); // no band settings: evaluation throws
        broken.setTradeSide(TradeSide.BOTH);
        when(strategyMapper.findActiveBySymbol("AAPL")).thenReturn(List.of(broken, strategy));
        quotes(100, 98, 103, 105);

        evaluator.evaluateAll();

        verify(orderClient).placeOrder(anyString(), anyLong(), anyString(), any(), anyInt(), any(), anyString());
    }
}
