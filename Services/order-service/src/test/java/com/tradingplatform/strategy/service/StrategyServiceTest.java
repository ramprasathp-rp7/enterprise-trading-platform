package com.tradingplatform.strategy.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.api.security.AccountAccessAuthorizer;
import com.tradingplatform.domain.enums.AssetClass;
import com.tradingplatform.domain.exception.AccountUnauthorizedException;
import com.tradingplatform.domain.exception.InstrumentNotFoundException;
import com.tradingplatform.domain.exception.InvalidOrderException;
import com.tradingplatform.domain.model.Instrument;
import com.tradingplatform.strategy.controller.StrategyController;
import com.tradingplatform.strategy.dto.CreateStrategyRequest;
import com.tradingplatform.strategy.dto.StrategyExecutionResponse;
import com.tradingplatform.strategy.dto.StrategyResponse;
import com.tradingplatform.strategy.dto.UpdateStrategyRequest;
import com.tradingplatform.strategy.exception.StrategyConflictException;
import com.tradingplatform.strategy.exception.StrategyNotFoundException;
import com.tradingplatform.strategy.mapper.StrategyExecutionMapper;
import com.tradingplatform.strategy.mapper.TradingStrategyMapper;
import com.tradingplatform.strategy.model.ExecutionOutcome;
import com.tradingplatform.strategy.model.StrategyExecution;
import com.tradingplatform.strategy.model.StrategySignal;
import com.tradingplatform.strategy.model.StrategyType;
import com.tradingplatform.strategy.model.TradeSide;
import com.tradingplatform.strategy.model.TradingStrategy;

class StrategyServiceTest {

    private static final long ACCOUNT = 5L;

    private TradingStrategyMapper strategyMapper;
    private StrategyExecutionMapper executionMapper;
    private StrategyService service;
    private TradingStrategy stored;

    @BeforeEach
    void setUp() {
        strategyMapper = mock(TradingStrategyMapper.class);
        executionMapper = mock(StrategyExecutionMapper.class);
        InstrumentMapper instruments = mock(InstrumentMapper.class);
        when(instruments.findBySymbol("AAPL")).thenReturn(new Instrument(2L, "AAPL", "Apple Inc.", AssetClass.EQUITY, "USD", true));
        when(instruments.findBySymbol("TATASTEEL.BO"))
            .thenReturn(new Instrument(5L, "TATASTEEL.BO", "Tata Steel", AssetClass.EQUITY, "INR", false));

        // insert assigns an id and the row reads back as stored
        doAnswer(inv -> {
            stored = inv.getArgument(0);
            stored.setStrategyId(9L);
            return 1;
        }).when(strategyMapper).insert(any());
        when(strategyMapper.findById(eq(9L), eq(ACCOUNT))).thenAnswer(inv -> stored);

        service = new StrategyService(strategyMapper, executionMapper, instruments, new ObjectMapper(), 2);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static CreateStrategyRequest maCrossover(String symbol, Integer shortWindow, Integer longWindow) {
        return new CreateStrategyRequest(symbol, StrategyType.MA_CROSSOVER, shortWindow, longWindow, null, null,
            TradeSide.BOTH, 10, 50, new BigDecimal("5000"), null);
    }

    @Test
    void createsAMovingAverageStrategyWithDefaultsAndDescribesIt() {
        StrategyResponse created = service.createStrategy(ACCOUNT, maCrossover(" aapl ", null, null));

        assertEquals(9L, created.strategyId());
        assertEquals("AAPL", created.symbol());
        assertEquals(5, created.shortWindow());
        assertEquals(20, created.longWindow());
        assertEquals(3, created.maxConsecutiveFailures());
        assertTrue(created.active());
        assertEquals("Buy 10 AAPL when the 5-point average crosses above the 20-point average; "
            + "sell 10 when the 5-point average crosses below the 20-point average.", created.description());
    }

    @Test
    void createsABollingerStrategyWithDefaultBands() {
        StrategyResponse created = service.createStrategy(ACCOUNT, new CreateStrategyRequest("AAPL", StrategyType.BOLLINGER,
            null, null, null, null, TradeSide.BUY_ONLY, 1, 5, new BigDecimal("1000"), 5));

        assertEquals(20, created.bandWindow());
        assertEquals(0, new BigDecimal("2").compareTo(created.bandK()));
        assertEquals(5, created.maxConsecutiveFailures());
        assertEquals("Buy 1 AAPL when the price falls below the 20-point lower Bollinger band (2 std dev).", created.description());
    }

    @Test
    void rejectsBadSettingsAndUnknownOrUntradableInstruments() {
        assertThrows(InvalidOrderException.class, () -> service.createStrategy(ACCOUNT, maCrossover("AAPL", 20, 5)));
        assertThrows(InstrumentNotFoundException.class, () -> service.createStrategy(ACCOUNT, maCrossover("NOPE", null, null)));
        assertThrows(InvalidOrderException.class, () -> service.createStrategy(ACCOUNT, maCrossover("TATASTEEL.BO", null, null)));
        verify(strategyMapper, never()).insert(any());
    }

    @Test
    void enforcesTheActiveCapAndOneRulePerTypeAndSymbol() {
        when(strategyMapper.countActiveByAccountId(ACCOUNT)).thenReturn(2);
        assertThrows(StrategyConflictException.class, () -> service.createStrategy(ACCOUNT, maCrossover("AAPL", null, null)));

        when(strategyMapper.countActiveByAccountId(ACCOUNT)).thenReturn(0);
        when(strategyMapper.existsActiveRule(ACCOUNT, "AAPL", StrategyType.MA_CROSSOVER)).thenReturn(true);
        assertThrows(StrategyConflictException.class, () -> service.createStrategy(ACCOUNT, maCrossover("AAPL", null, null)));
    }

    @Test
    void turnsAStrategyOffAndBackOnWithTheSameCaps() {
        service.createStrategy(ACCOUNT, maCrossover("AAPL", null, null));

        stored.setActive(true);
        service.setActive(ACCOUNT, 9L, false);
        verify(strategyMapper).updateActive(9L, ACCOUNT, false);

        stored.setActive(false);
        when(strategyMapper.existsActiveRule(ACCOUNT, "AAPL", StrategyType.MA_CROSSOVER)).thenReturn(true);
        assertThrows(StrategyConflictException.class, () -> service.setActive(ACCOUNT, 9L, true));
        verify(strategyMapper, never()).updateActive(9L, ACCOUNT, true);
    }

    @Test
    void reportsUnknownStrategies() {
        assertThrows(StrategyNotFoundException.class, () -> service.getStrategy(ACCOUNT, 404L));
        assertThrows(StrategyNotFoundException.class, () -> service.setActive(ACCOUNT, 404L, false));
        assertThrows(StrategyNotFoundException.class, () -> service.deleteStrategy(ACCOUNT, 404L));
        assertThrows(StrategyNotFoundException.class, () -> service.getExecutions(ACCOUNT, 404L, null));
    }

    @Test
    void returnsExecutionsWithTheirIndicatorValuesAndCapsTheLimit() {
        service.createStrategy(ACCOUNT, maCrossover("AAPL", null, null));
        StrategyExecution e = new StrategyExecution();
        e.setExecutionId(1L);
        e.setStrategyId(9L);
        e.setSignal(StrategySignal.BUY);
        e.setOutcome(ExecutionOutcome.PLACED);
        e.setIndicatorsJson("{\"shortMa\":104,\"longMa\":101.5}");
        e.setQuoteReceivedAt(Instant.parse("2026-10-07T14:03:00Z"));
        when(executionMapper.findByStrategyId(9L, StrategyService.MAX_EXECUTIONS)).thenReturn(List.of(e));

        List<StrategyExecutionResponse> executions = service.getExecutions(ACCOUNT, 9L, 10_000);

        assertEquals(1, executions.size());
        assertEquals(104, ((Number) executions.get(0).indicators().get("shortMa")).intValue());
        assertEquals(Instant.parse("2026-10-07T14:03:00Z"), executions.get(0).quoteAt());
    }

    @Test
    void controllerOnlyServesTheTokensOwnAccount() {
        StrategyController controller = new StrategyController(service, new AccountAccessAuthorizer());
        signInAs(ACCOUNT);

        assertEquals(HttpStatus.CREATED, controller.createStrategy(ACCOUNT, maCrossover("AAPL", null, null)).getStatusCode());
        assertEquals(false, controller.updateStrategy(ACCOUNT, 9L, new UpdateStrategyRequest(false)).getStatusCode().isError());
        when(strategyMapper.delete(9L, ACCOUNT)).thenReturn(1);
        assertEquals(HttpStatus.NO_CONTENT, controller.deleteStrategy(ACCOUNT, 9L).getStatusCode());

        assertThrows(AccountUnauthorizedException.class, () -> controller.getStrategies(6L));
        assertThrows(AccountUnauthorizedException.class, () -> controller.updateStrategy(6L, 9L, new UpdateStrategyRequest(false)));
        verify(strategyMapper, never()).findByAccountId(anyLong());
        ArgumentCaptor<Boolean> active = ArgumentCaptor.forClass(Boolean.class);
        verify(strategyMapper).updateActive(eq(9L), eq(ACCOUNT), active.capture());
        assertEquals(false, active.getValue());
    }

    private static void signInAs(long accountId) {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .claim("accountId", accountId)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(jwt, null, List.of()));
    }
}
