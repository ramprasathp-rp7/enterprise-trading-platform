package com.tradingplatform.watchlist.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.watchlist.mapper.WatchlistMapper;
import com.tradingplatform.watchlist.dto.WatchlistItemResponse;
import com.tradingplatform.watchlist.dto.WatchlistResponse;
import com.tradingplatform.domain.enums.AssetClass;
import com.tradingplatform.domain.exception.InstrumentNotFoundException;
import com.tradingplatform.domain.exception.InvalidOrderException;
import com.tradingplatform.watchlist.exception.WatchlistConflictException;
import com.tradingplatform.watchlist.exception.WatchlistNotFoundException;
import com.tradingplatform.domain.model.Instrument;
import com.tradingplatform.watchlist.model.Watchlist;
import com.tradingplatform.watchlist.model.WatchlistItem;

class WatchlistServiceTest {

    private static final long ACCOUNT = 1L;
    private static final long OTHER_ACCOUNT = 2L;

    private FakeWatchlistMapper mapper;
    private WatchlistService service;

    @BeforeEach
    void setUp() {
        mapper = new FakeWatchlistMapper();
        InstrumentMapper instruments = new FakeInstrumentMapper(Map.of(
            "AAPL", Instrument.tradable(1L, "AAPL", "Apple Inc.", AssetClass.EQUITY, "USD"),
            "FX:EURUSD", Instrument.tradable(2L, "FX:EURUSD", "Euro / US Dollar", AssetClass.FX, "USD")));
        service = new WatchlistService(mapper, instruments);
    }

    @Test
    void createWatchlist_trimsNameAndStartsEmpty() {
        WatchlistResponse created = service.createWatchlist(ACCOUNT, "  Tech  ");

        assertEquals("Tech", created.name());
        assertEquals(ACCOUNT, created.accountId());
        assertTrue(created.items().isEmpty());
    }

    @Test
    void createWatchlist_rejectsDuplicateNameIgnoringCase() {
        service.createWatchlist(ACCOUNT, "Tech");

        assertThrows(WatchlistConflictException.class, () -> service.createWatchlist(ACCOUNT, "tech"));
    }

    @Test
    void createWatchlist_sameNameAllowedForAnotherAccount() {
        service.createWatchlist(ACCOUNT, "Tech");

        assertEquals("Tech", service.createWatchlist(OTHER_ACCOUNT, "Tech").name());
    }

    @Test
    void createWatchlist_rejectsBlankName() {
        assertThrows(InvalidOrderException.class, () -> service.createWatchlist(ACCOUNT, "   "));
    }

    @Test
    void addItem_normalisesSymbolAndAppearsInList() {
        long id = service.createWatchlist(ACCOUNT, "Tech").watchlistId();

        WatchlistItemResponse item = service.addItem(ACCOUNT, id, " aapl ");

        assertEquals("AAPL", item.symbol());
        List<WatchlistResponse> lists = service.getWatchlists(ACCOUNT);
        assertEquals(1, lists.size());
        assertEquals(List.of("AAPL"), lists.get(0).items().stream().map(WatchlistItemResponse::symbol).toList());
    }

    @Test
    void addItem_rejectsUnknownSymbol() {
        long id = service.createWatchlist(ACCOUNT, "Tech").watchlistId();

        assertThrows(InstrumentNotFoundException.class, () -> service.addItem(ACCOUNT, id, "NOPE"));
    }

    @Test
    void addItem_rejectsDuplicateSymbol() {
        long id = service.createWatchlist(ACCOUNT, "Tech").watchlistId();
        service.addItem(ACCOUNT, id, "AAPL");

        assertThrows(WatchlistConflictException.class, () -> service.addItem(ACCOUNT, id, "AAPL"));
    }

    @Test
    void otherAccountsWatchlistIsNotFound() {
        long id = service.createWatchlist(ACCOUNT, "Tech").watchlistId();

        assertThrows(WatchlistNotFoundException.class, () -> service.getWatchlist(OTHER_ACCOUNT, id));
        assertThrows(WatchlistNotFoundException.class, () -> service.addItem(OTHER_ACCOUNT, id, "AAPL"));
        assertThrows(WatchlistNotFoundException.class, () -> service.deleteWatchlist(OTHER_ACCOUNT, id));
    }

    @Test
    void removeItem_deletesOnlyThatItem() {
        long id = service.createWatchlist(ACCOUNT, "Tech").watchlistId();
        long aapl = service.addItem(ACCOUNT, id, "AAPL").watchlistItemId();
        service.addItem(ACCOUNT, id, "FX:EURUSD");

        service.removeItem(ACCOUNT, id, aapl);

        assertEquals(List.of("FX:EURUSD"),
            service.getWatchlist(ACCOUNT, id).items().stream().map(WatchlistItemResponse::symbol).toList());
        assertThrows(WatchlistNotFoundException.class, () -> service.removeItem(ACCOUNT, id, aapl));
    }

    @Test
    void deleteWatchlist_removesListAndItems() {
        long id = service.createWatchlist(ACCOUNT, "Tech").watchlistId();
        service.addItem(ACCOUNT, id, "AAPL");

        service.deleteWatchlist(ACCOUNT, id);

        assertTrue(service.getWatchlists(ACCOUNT).isEmpty());
        assertTrue(mapper.items.isEmpty());
    }

    /** In-memory WATCHLISTS / WATCHLIST_ITEMS with the same account scoping and cascade as the SQL */
    private static final class FakeWatchlistMapper implements WatchlistMapper {
        final List<Watchlist> lists = new ArrayList<>();
        final List<WatchlistItem> items = new ArrayList<>();
        private final AtomicLong ids = new AtomicLong();

        @Override
        public List<Watchlist> findByAccountId(Long accountId) {
            return lists.stream().filter(w -> w.getAccountId().equals(accountId)).toList();
        }

        @Override
        public Watchlist findById(Long watchlistId, Long accountId) {
            return lists.stream()
                .filter(w -> w.getWatchlistId().equals(watchlistId) && w.getAccountId().equals(accountId))
                .findFirst().orElse(null);
        }

        @Override
        public boolean existsByName(Long accountId, String name) {
            return findByAccountId(accountId).stream().anyMatch(w -> w.getName().equalsIgnoreCase(name));
        }

        @Override
        public int insertWatchlist(Watchlist watchlist) {
            watchlist.setWatchlistId(ids.incrementAndGet());
            watchlist.setCreatedAt(Instant.now());
            watchlist.setUpdatedAt(watchlist.getCreatedAt());
            lists.add(watchlist);
            return 1;
        }

        @Override
        public int deleteWatchlist(Long watchlistId, Long accountId) {
            Watchlist w = findById(watchlistId, accountId);
            if (w == null) {
                return 0;
            }
            lists.remove(w);
            items.removeIf(i -> i.getWatchlistId().equals(watchlistId));
            return 1;
        }

        @Override
        public int touch(Long watchlistId) {
            lists.stream().filter(w -> w.getWatchlistId().equals(watchlistId)).forEach(w -> w.setUpdatedAt(Instant.now()));
            return 1;
        }

        @Override
        public List<WatchlistItem> findItemsByAccountId(Long accountId) {
            return items.stream().filter(i -> i.getAccountId().equals(accountId)).toList();
        }

        @Override
        public List<WatchlistItem> findItemsByWatchlistId(Long watchlistId) {
            return items.stream().filter(i -> i.getWatchlistId().equals(watchlistId)).toList();
        }

        @Override
        public WatchlistItem findItemById(Long watchlistItemId) {
            return items.stream().filter(i -> i.getWatchlistItemId().equals(watchlistItemId)).findFirst().orElse(null);
        }

        @Override
        public boolean itemExists(Long watchlistId, String symbol) {
            return findItemsByWatchlistId(watchlistId).stream().anyMatch(i -> i.getSymbol().equals(symbol));
        }

        @Override
        public int insertItem(WatchlistItem item) {
            item.setWatchlistItemId(ids.incrementAndGet());
            item.setCreatedAt(Instant.now());
            items.add(item);
            return 1;
        }

        @Override
        public int deleteItem(Long watchlistItemId, Long watchlistId, Long accountId) {
            boolean removed = items.removeIf(i -> i.getWatchlistItemId().equals(watchlistItemId)
                && i.getWatchlistId().equals(watchlistId) && i.getAccountId().equals(accountId));
            return removed ? 1 : 0;
        }
    }

    private record FakeInstrumentMapper(Map<String, Instrument> bySymbol) implements InstrumentMapper {
        @Override
        public Instrument findBySymbol(String symbol) {
            return bySymbol.get(symbol);
        }

        @Override
        public Instrument findById(Long id) {
            return bySymbol.values().stream().filter(i -> i.getId().equals(id)).findFirst().orElse(null);
        }

        @Override
        public List<Instrument> findAllInstruments() {
            return bySymbol.values().stream().toList();
        }

        @Override
        public List<Instrument> findTradableInstruments() {
            return bySymbol.values().stream().filter(Instrument::isTradable).toList();
        }

        @Override
        public List<String> findAllSymbols() {
            return bySymbol.keySet().stream().sorted().toList();
        
        }

        @Override
        public List<String> findConversionFxSymbols() {
            return List.of();
        }

        @Override
        public List<com.tradingplatform.instruments.model.InstrumentListing> findAllListings() {
            return List.of();
        }

        @Override
        public com.tradingplatform.instruments.model.InstrumentListing findListingBySymbol(String symbol) {
            return null;
        }

        @Override
        public int insertDiscovered(com.tradingplatform.instruments.model.InstrumentListing instrument) {
            return 0;
        }
    }
}
