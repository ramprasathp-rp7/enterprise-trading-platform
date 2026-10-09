package com.tradingplatform.api.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tradingplatform.api.mappers.AccountMapper;
import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.api.services.FauxnanceApiClient.QuoteData;
import com.tradingplatform.api.services.QuoteResolver.ResolvedQuote;
import com.tradingplatform.domain.dto.PnlResponse;
import com.tradingplatform.domain.dto.PricedPosition;
import com.tradingplatform.domain.enums.AccountStatus;
import com.tradingplatform.domain.enums.AssetClass;
import com.tradingplatform.domain.model.Account;
import com.tradingplatform.domain.model.Instrument;
import com.tradingplatform.domain.model.Position;

/**
 * P&L totals and the pricing corner cases: missing instruments, missing quotes, odd quote
 * currencies and filtering by symbol.
 */
class PortfolioPnlServiceTest {

    private static final Instant AS_OF = Instant.parse("2026-10-09T10:00:00Z");

    private final AccountMapper accounts = mock(AccountMapper.class);
    private final InstrumentMapper instruments = mock(InstrumentMapper.class);
    private final QuoteResolver quotes = mock(QuoteResolver.class);
    private final RealisedPnlCalculator realised = mock(RealisedPnlCalculator.class);
    private final PortfolioPnlService service = new PortfolioPnlService(accounts, instruments, quotes, realised);
    private final Map<String, ResolvedQuote> book = new HashMap<>();

    private static ResolvedQuote quote(String symbol, String price, String currency, boolean stale) {
        BigDecimal p = new BigDecimal(price);
        return new ResolvedQuote(new QuoteData(symbol, p, p, p, currency, AS_OF, stale), stale);
    }

    @BeforeEach
    void setUp() {
        when(accounts.findByAccountId(1L))
            .thenReturn(new Account(1L, "ACC-1", "Rahul", new BigDecimal("1000.00"), "USD", AccountStatus.ACTIVE, 0));
        when(instruments.findById(10L)).thenReturn(Instrument.tradable(10L, "AAPL", "Apple", AssetClass.EQUITY, "USD"));
        when(instruments.findById(11L)).thenReturn(Instrument.tradable(11L, "MSFT", "Microsoft", AssetClass.EQUITY, "USD"));
        when(quotes.resolve(anyCollection())).thenAnswer(inv -> book);
        when(accounts.findPositionByAccountId(1L)).thenReturn(List.of(
            new Position(1L, 10L, 10, new BigDecimal("100")),
            new Position(1L, 11L, 5, new BigDecimal("200")),
            new Position(1L, 12L, 3, new BigDecimal("50")),   // instrument no longer listed
            new Position(1L, 10L, 0, new BigDecimal("1"))));  // sold out
    }

    @Test
    void pnlAddsRealisedAndUnrealisedWithAPerSymbolBreakdown() {
        book.put("AAPL", quote("AAPL", "110", "USD", false));
        book.put("MSFT", quote("MSFT", "190", "USD", false));
        when(realised.calculateRealisedPnlBySymbol(eq(1L), any(), any(), eq(null)))
            .thenReturn(Map.of("AAPL", new BigDecimal("25.00")));

        PnlResponse pnl = service.getPnl(1L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 9), true);

        // AAPL +100, MSFT -50 unrealised; AAPL +25 realised
        assertThat(pnl.getUnrealisedPnl()).isEqualByComparingTo("50.00");
        assertThat(pnl.getRealisedPnl()).isEqualByComparingTo("25.00");
        assertThat(pnl.getTotalPnl()).isEqualByComparingTo("75.00");
        assertThat(pnl.getBaseCurrency()).isEqualTo("USD");
        assertThat(pnl.getBySymbol()).extracting(s -> s.getSymbol() + "=" + s.getTotalPnl().stripTrailingZeros().toPlainString())
            .containsExactly("AAPL=125", "MSFT=-50");
    }

    @Test
    void pnlWithoutBreakdownAndWithoutPricesCountsOnlyRealised() {
        when(realised.calculateRealisedPnlBySymbol(eq(1L), any(), any(), eq(null))).thenReturn(Map.of());

        PnlResponse pnl = service.getPnl(1L, null, null, false);

        assertThat(pnl.getBySymbol()).isNull();
        assertThat(pnl.getTotalPnl()).isEqualByComparingTo("0");
    }

    @Test
    void pnlRejectsABackwardsRange() {
        assertThatThrownBy(() -> service.getPnl(1L, LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 1), false))
            .isInstanceOf(PortfolioPnlService.PortfolioPnlException.class);
    }

    @Test
    void unpricedHoldingsAreStaleAndHaveNoMarketValue() {
        List<PricedPosition> positions = service.getPricedPositions(1L, null);

        assertThat(positions).extracting(PricedPosition::getSymbol).containsExactly("AAPL", "MSFT");
        assertThat(positions).allSatisfy(p -> {
            assertThat(p.getStale()).isTrue();
            assertThat(p.getLastPrice()).isNull();
            assertThat(p.getMarketValue()).isNull();
        });
    }

    @Test
    void filteringBySymbolKeepsOnlyThatHolding() {
        book.put("MSFT", quote("MSFT", "190", "USD", false));

        assertThat(service.getPricedPositions(1L, "MSFT")).extracting(PricedPosition::getSymbol).containsExactly("MSFT");
        assertThat(service.getPricedPositions(1L, "NOPE")).isEmpty();
    }

    @Test
    void aQuoteInAnotherCurrencyIsConvertedWithItsOwnFxPair() {
        book.put("AAPL", quote("AAPL", "100", "EUR", false));
        book.put("FX:EURUSD", quote("FX:EURUSD", "1.10", "USD", false));

        PricedPosition aapl = service.getPricedPositions(1L, "AAPL").get(0);

        assertThat(aapl.getCurrency()).isEqualTo("EUR");
        assertThat(aapl.getFxRate()).isEqualByComparingTo("1.10");
        assertThat(aapl.getMarketValueAccount()).isEqualByComparingTo("1100.00");
    }

    @Test
    void aMissingFxRateOrAStaleOneMarksTheHoldingStale() {
        book.put("AAPL", quote("AAPL", "100", "GBP", false));
        book.put("MSFT", quote("MSFT", "100", " ", false));

        List<PricedPosition> positions = service.getPricedPositions(1L, null);

        PricedPosition aapl = positions.get(0);
        assertThat(aapl.getStale()).isTrue();
        assertThat(aapl.getFxRate()).isNull();
        assertThat(aapl.getMarketValueAccount()).isNull();
        // A blank quote currency falls back to the instrument's
        assertThat(positions.get(1).getCurrency()).isEqualTo("USD");
        assertThat(positions.get(1).getStale()).isFalse();
    }

    @Test
    void anAccountWithoutPositionsHasNothingToPrice() {
        when(accounts.findPositionByAccountId(1L)).thenReturn(null);

        assertThat(service.getPricedPositions(1L, null)).isEmpty();
    }
}
