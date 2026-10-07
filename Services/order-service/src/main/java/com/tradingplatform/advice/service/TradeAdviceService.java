package com.tradingplatform.advice.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.tradingplatform.advice.dto.SignalResponse;
import com.tradingplatform.advice.dto.TradeAdviceResponse;
import com.tradingplatform.advice.engine.SignalEngine;
import com.tradingplatform.api.mappers.AccountMapper;
import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.api.services.CandleStorageService;
import com.tradingplatform.api.services.MarketDataCache;
import com.tradingplatform.domain.model.CandleData;
import com.tradingplatform.domain.model.Instrument;
import com.tradingplatform.domain.model.Position;
import com.tradingplatform.watchlist.mapper.WatchlistMapper;
import com.tradingplatform.watchlist.model.WatchlistItem;

/**
 * Trade Advice: research-driven BUY / SELL / HOLD signals, with the reasons behind each, for the
 * instruments an account holds and watches.
 *
 * Inputs:
 * - Portfolio: the account's open positions (quantity, average cost)
 * - Watchlists: every symbol on the account's watchlists
 * - Market and price data (S4): daily candles from CANDLE_DATA for history, and the latest quote
 *   from the market-data cache for the current price (falling back to the last close)
 *
 * The signal rules live in {@link SignalEngine}. An instrument with too little history gets no
 * signal, only an explanation.
 */
@Service
public class TradeAdviceService {

    private static final Logger logger = LoggerFactory.getLogger(TradeAdviceService.class);

    /** Daily candles read per instrument: enough for the 50-day average with a margin */
    static final int HISTORY_DAYS = 120;
    static final String DISCLAIMER =
        "Signals come from simple technical rules on recent prices. They are educational, not investment advice.";

    private final AccountMapper accountMapper;
    private final InstrumentMapper instrumentMapper;
    private final WatchlistMapper watchlistMapper;
    private final CandleStorageService candleStorage;
    private final MarketDataCache marketDataCache;

    public TradeAdviceService(AccountMapper accountMapper, InstrumentMapper instrumentMapper,
                              WatchlistMapper watchlistMapper, CandleStorageService candleStorage,
                              MarketDataCache marketDataCache) {
        this.accountMapper = Objects.requireNonNull(accountMapper);
        this.instrumentMapper = Objects.requireNonNull(instrumentMapper);
        this.watchlistMapper = Objects.requireNonNull(watchlistMapper);
        this.candleStorage = Objects.requireNonNull(candleStorage);
        this.marketDataCache = Objects.requireNonNull(marketDataCache);
    }

    /**
     * Advice for every held and watched instrument of the account. Signals first (SELL, BUY,
     * HOLD), then instruments without enough data.
     */
    public TradeAdviceResponse adviceFor(long accountId) {
        Map<String, Target> targets = new LinkedHashMap<>();

        // Holdings (portfolio)
        List<Position> positions = accountMapper.findPositionByAccountId(accountId);
        for (Position position : positions == null ? List.<Position>of() : positions) {
            if (position.getQuantity() <= 0) {
                continue;
            }
            Instrument instrument = instrumentMapper.findById(position.getInstrumentId());
            if (instrument == null) {
                continue;
            }
            targets.computeIfAbsent(instrument.getSymbol(), s -> new Target(instrument)).holding =
                new SignalEngine.Holding(position.getQuantity(), position.getAverageCost());
        }

        // Watchlists
        List<WatchlistItem> items = watchlistMapper.findItemsByAccountId(accountId);
        for (WatchlistItem item : items == null ? List.<WatchlistItem>of() : items) {
            Target target = targets.get(item.getSymbol());
            if (target == null) {
                Instrument instrument = instrumentMapper.findBySymbol(item.getSymbol());
                if (instrument == null) {
                    continue;
                }
                target = new Target(instrument);
                targets.put(item.getSymbol(), target);
            }
            target.watched = true;
        }

        List<SignalResponse> signals = new ArrayList<>();
        for (Target target : targets.values()) {
            signals.add(evaluate(target));
        }
        signals.sort(SignalResponse.DISPLAY_ORDER);

        logger.info("Trade advice for accountId={}: {} instruments, {} with a signal",
            accountId, signals.size(), signals.stream().filter(s -> s.signal() != null).count());
        return new TradeAdviceResponse(accountId, Instant.now(), DISCLAIMER, signals);
    }

    private SignalResponse evaluate(Target target) {
        Instrument instrument = target.instrument;
        List<BigDecimal> closes = candleStorage.getLatestCandles(instrument.getId(), HISTORY_DAYS).stream()
            .filter(c -> !c.isSynthetic())
            .map(CandleData::getClose)
            .toList();
        BigDecimal livePrice = marketDataCache.getLatestPrice(instrument.getSymbol()).orElse(null);

        SignalEngine.Evaluation evaluation = SignalEngine.evaluate(closes, livePrice, target.holding);
        return SignalResponse.from(instrument, target.holding != null, target.watched, evaluation);
    }

    /** One instrument to advise on, and why it is included */
    private static final class Target {
        final Instrument instrument;
        SignalEngine.Holding holding;
        boolean watched;

        Target(Instrument instrument) {
            this.instrument = instrument;
        }
    }
}
