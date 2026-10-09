package com.tradingplatform.domain.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

/**
 * Accessors, value equality and readable toString for the portfolio and health DTOs.
 */
class ValueObjectContractsTest {

    private static final Instant AS_OF = Instant.parse("2026-10-09T10:00:00Z");

    /** equal to an identical copy, not to a different one, null or another type */
    private static <T> void assertValueSemantics(Supplier<T> same, T different) {
        T a = same.get();
        T b = same.get();
        assertThat(a).isEqualTo(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(different).isNotEqualTo(null).isNotEqualTo("other");
    }

    private static SymbolPnl symbolPnl(String symbol) {
        return new SymbolPnl(symbol, new BigDecimal("10"), new BigDecimal("5"), new BigDecimal("15"));
    }

    @Test
    void symbolPnl() {
        SymbolPnl pnl = symbolPnl("AAPL");

        assertThat(pnl.getSymbol()).isEqualTo("AAPL");
        assertThat(pnl.getRealisedPnl()).isEqualByComparingTo("10");
        assertThat(pnl.getUnrealisedPnl()).isEqualByComparingTo("5");
        assertThat(pnl.getTotalPnl()).isEqualByComparingTo("15");
        assertThat(pnl.toString()).contains("symbol='AAPL'", "totalPnl=15");
        assertValueSemantics(() -> symbolPnl("AAPL"), symbolPnl("MSFT"));
        assertThatThrownBy(() -> new SymbolPnl(null, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE))
            .isInstanceOf(NullPointerException.class);
    }

    private static PnlResponse pnl(long accountId) {
        return new PnlResponse(accountId, "USD", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 9),
            new BigDecimal("10"), new BigDecimal("5"), new BigDecimal("15"), List.of(symbolPnl("AAPL")), AS_OF);
    }

    @Test
    void pnlResponse() {
        PnlResponse response = pnl(1);

        assertThat(response.getAccountId()).isEqualTo(1L);
        assertThat(response.getBaseCurrency()).isEqualTo("USD");
        assertThat(response.getFrom()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(response.getTo()).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(response.getRealisedPnl()).isEqualByComparingTo("10");
        assertThat(response.getUnrealisedPnl()).isEqualByComparingTo("5");
        assertThat(response.getTotalPnl()).isEqualByComparingTo("15");
        assertThat(response.getBySymbol()).containsExactly(symbolPnl("AAPL"));
        assertThat(response.getAsOf()).isEqualTo(AS_OF);
        assertThat(response.toString()).contains("accountId=1", "baseCurrency='USD'", "bySymbol=[SymbolPnl{");
        assertValueSemantics(() -> pnl(1), pnl(2));
        assertThatThrownBy(() -> new PnlResponse(1L, "USD", null, null, BigDecimal.ONE, BigDecimal.ONE,
            BigDecimal.ONE, List.of(), null)).isInstanceOf(NullPointerException.class);
    }

    private static PortfolioSummary summary(boolean partial) {
        return new PortfolioSummary(1L, "USD", new BigDecimal("1000"), new BigDecimal("500"), new BigDecimal("400"),
            new BigDecimal("100"), new BigDecimal("25"), new BigDecimal("12"), new BigDecimal("1500"), 3, partial, AS_OF);
    }

    @Test
    void portfolioSummary() {
        PortfolioSummary summary = summary(false);

        assertThat(summary.getAccountId()).isEqualTo(1L);
        assertThat(summary.getBaseCurrency()).isEqualTo("USD");
        assertThat(summary.getCashBalance()).isEqualByComparingTo("1000");
        assertThat(summary.getMarketValue()).isEqualByComparingTo("500");
        assertThat(summary.getCostBasis()).isEqualByComparingTo("400");
        assertThat(summary.getUnrealisedPnl()).isEqualByComparingTo("100");
        assertThat(summary.getUnrealisedPnlPercent()).isEqualByComparingTo("25");
        assertThat(summary.getRealisedPnl()).isEqualByComparingTo("12");
        assertThat(summary.getTotalValue()).isEqualByComparingTo("1500");
        assertThat(summary.getPositionCount()).isEqualTo(3);
        assertThat(summary.getPartial()).isFalse();
        assertThat(summary.getAsOf()).isEqualTo(AS_OF);
        assertThat(summary.toString()).contains("accountId=1", "positionCount=3", "partial=false");
        assertValueSemantics(() -> summary(false), summary(true));
    }

    private static PricedPosition position(String symbol) {
        return new PricedPosition(1L, symbol, 10, new BigDecimal("100"), new BigDecimal("1000"), new BigDecimal("110"),
            new BigDecimal("1100"), new BigDecimal("100"), new BigDecimal("10"), "USD", AS_OF, false,
            new BigDecimal("1"), new BigDecimal("100"), new BigDecimal("1000"), new BigDecimal("1100"), new BigDecimal("100"));
    }

    @Test
    void pricedPosition() {
        PricedPosition position = position("AAPL");

        assertThat(position.getAccountId()).isEqualTo(1L);
        assertThat(position.getSymbol()).isEqualTo("AAPL");
        assertThat(position.getQuantity()).isEqualTo(10);
        assertThat(position.getAverageCost()).isEqualByComparingTo("100");
        assertThat(position.getCostBasis()).isEqualByComparingTo("1000");
        assertThat(position.getLastPrice()).isEqualByComparingTo("110");
        assertThat(position.getMarketValue()).isEqualByComparingTo("1100");
        assertThat(position.getUnrealisedPnl()).isEqualByComparingTo("100");
        assertThat(position.getUnrealisedPnlPercent()).isEqualByComparingTo("10");
        assertThat(position.getCurrency()).isEqualTo("USD");
        assertThat(position.getPriceAsOf()).isEqualTo(AS_OF);
        assertThat(position.getStale()).isFalse();
        assertThat(position.getFxRate()).isEqualByComparingTo("1");
        assertThat(position.getAverageCostAccount()).isEqualByComparingTo("100");
        assertThat(position.getCostBasisAccount()).isEqualByComparingTo("1000");
        assertThat(position.getMarketValueAccount()).isEqualByComparingTo("1100");
        assertThat(position.getUnrealisedPnlAccount()).isEqualByComparingTo("100");
        assertThat(position.toString()).contains("symbol='AAPL'", "quantity=10", "currency='USD'");
        assertValueSemantics(() -> position("AAPL"), position("MSFT"));
    }

    @Test
    void healthResponse() {
        HealthResponse.DependencyStatus db = new HealthResponse.DependencyStatus("postgres", "UP");
        HealthResponse.DependencyStatus quotes = new HealthResponse.DependencyStatus("fauxnance", "UP", 1800);

        assertThat(db.getName()).isEqualTo("postgres");
        assertThat(db.getStatus()).isEqualTo("UP");
        assertThat(db.getQuotaRemaining()).isNull();
        assertThat(quotes.getQuotaRemaining()).isEqualTo(1800);
        assertThat(quotes.toString()).contains("fauxnance", "1800");
        assertValueSemantics(() -> new HealthResponse.DependencyStatus("postgres", "UP"),
            new HealthResponse.DependencyStatus("postgres", "DOWN"));

        HealthResponse health = new HealthResponse("UP", List.of(db, quotes), AS_OF);
        assertThat(health.getStatus()).isEqualTo("UP");
        assertThat(health.getDependencies()).containsExactly(db, quotes);
        assertThat(health.getAsOf()).isEqualTo(AS_OF);
        assertThat(health.toString()).contains("status='UP'", "postgres");
        assertValueSemantics(() -> new HealthResponse("UP", List.of(db), AS_OF),
            new HealthResponse("DEGRADED", List.of(db), AS_OF));
    }
}
