package com.tradingplatform.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tradingplatform.domain.enums.AccountStatus;
import com.tradingplatform.domain.enums.OrderSide;
import com.tradingplatform.domain.enums.TransactionAction;
import com.tradingplatform.domain.enums.TransactionType;
import com.tradingplatform.instruments.model.InstrumentListing;
import com.tradingplatform.settings.model.AccountSettings;
import com.tradingplatform.strategy.model.StrategyExecution;
import com.tradingplatform.strategy.model.TradingStrategy;
import com.tradingplatform.watchlist.alerts.dto.PriceAlertTriggerResponse;
import com.tradingplatform.watchlist.alerts.model.AlertDirection;
import com.tradingplatform.watchlist.alerts.model.AlertMode;
import com.tradingplatform.watchlist.alerts.model.PriceAlert;
import com.tradingplatform.watchlist.alerts.model.PriceAlertTrigger;
import com.tradingplatform.watchlist.model.Watchlist;
import com.tradingplatform.watchlist.model.WatchlistItem;

/**
 * Invariants the domain models enforce, and the bean accessors MyBatis uses to map rows.
 */
class ModelGuardsAndAccessorsTest {

    private static final Instant NOW = Instant.parse("2026-10-09T10:00:00Z");

    @Test
    void ordersRejectNonPositiveQuantityPriceAndNegativeFees() {
        assertThatThrownBy(() -> new Order(UUID.randomUUID(), 1L, 2L, OrderSide.BUY, 0, BigDecimal.ONE, "k"))
            .hasMessageContaining("quantity");
        assertThatThrownBy(() -> new Order(UUID.randomUUID(), 1L, 2L, OrderSide.BUY, 1, BigDecimal.ZERO, "k"))
            .hasMessageContaining("price");
        Order order = new Order(1L, 2L, OrderSide.BUY, 1, BigDecimal.TEN, "k");
        assertThatThrownBy(() -> order.setPlacementFxRate(BigDecimal.ZERO)).hasMessageContaining("placementFxRate");
        assertThatThrownBy(() -> order.setFee(new BigDecimal("-1"))).hasMessageContaining("fee");
        assertThatThrownBy(() -> order.markFilled(BigDecimal.TEN, new BigDecimal("-1"))).hasMessageContaining("fee");
    }

    @Test
    void accountsAndPositionsRejectNegativeVersionsAndBadSells() {
        assertThatThrownBy(() -> new Account(1L, "ACC-1", "R", BigDecimal.TEN, "USD", AccountStatus.ACTIVE, -1, NOW, NOW))
            .hasMessageContaining("version");
        assertThat(new Account(1L, "ACC-1", "R", BigDecimal.TEN, "USD", AccountStatus.ACTIVE, 0, NOW, NOW).getCreatedOn())
            .isEqualTo(NOW);
        assertThatThrownBy(() -> new Position(1L, 2L, 1, BigDecimal.ONE, BigDecimal.ONE, -1)).hasMessageContaining("version");
        assertThatThrownBy(() -> new Position(1L, 2L, 5, BigDecimal.ONE).applySell(0)).hasMessageContaining("soldQuantity");
    }

    @Test
    void candlesMustBeConsistent() {
        BigDecimal one = BigDecimal.ONE;
        BigDecimal two = new BigDecimal("2");
        LocalDate day = LocalDate.of(2026, 10, 9);
        assertThatThrownBy(() -> new CandleData(1L, day, one, one, two, one, one, 0L, false)).hasMessageContaining("high must be >= low");
        assertThatThrownBy(() -> new CandleData(1L, day, two, one, one, one, one, 0L, false)).hasMessageContaining("open and close");
        assertThatThrownBy(() -> new CandleData(1L, day, one, two, two, two, two, 0L, false)).hasMessageContaining("low must be");
        assertThatThrownBy(() -> new CandleData(1L, day, one, one, one, one, one, -1L, false)).hasMessageContaining("volume");

        CandleData stored = new CandleData(7L, 1L, day, one, two, one, two, two, 10L, false, NOW, NOW.plusSeconds(1));
        assertThat(stored.getId()).isEqualTo(7L);
        assertThat(stored.getCreatedAt()).isEqualTo(NOW);
        assertThat(stored.getUpdatedAt()).isEqualTo(NOW.plusSeconds(1));

        CandleSyncWatermark watermark = new CandleSyncWatermark(3L, 1L, day, "SUCCESS", NOW, true);
        assertThat(watermark.getId()).isEqualTo(3L);
        assertThat(watermark.getLastSyncTime()).isEqualTo(NOW);
    }

    @Test
    void feesRejectNegativeVolumesAndPrintReadably() {
        AssetFeeStructure fees = new AssetFeeStructure("EQUITY", new BigDecimal("5.00"), new BigDecimal("0.001"));

        assertThatThrownBy(() -> fees.calculateFee(new BigDecimal("-1"))).hasMessageContaining("tradeVolume");
        assertThat(fees.toString()).contains("assetType='EQUITY'", "flatFee=5.00");
    }

    @Test
    void transactionsReadFromTheDatabaseKeepTheirIdAndAreEqualById() {
        UUID id = UUID.randomUUID();
        Transaction deposit = new Transaction(1L, null, TransactionType.DEPOSIT, TransactionAction.CREDIT,
            new BigDecimal("100"), NOW, id);
        Transaction same = new Transaction(1L, null, TransactionType.DEPOSIT, TransactionAction.CREDIT,
            new BigDecimal("50"), NOW, id);

        assertThat(deposit.getTransactionId()).isEqualTo(id);
        assertThat(deposit).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(deposit.toString()).contains("transactionType=DEPOSIT", "amount=100");
        assertThatThrownBy(() -> new Transaction(1L, null, TransactionType.DEPOSIT, TransactionAction.CREDIT,
            BigDecimal.ZERO, NOW, id)).hasMessageContaining("positive");
    }

    @Test
    void beanAccessorsUsedByMyBatis() {
        AccountSettings settings = new AccountSettings();
        settings.setAccountId(1L);
        settings.setOrderBudget(new BigDecimal("5000"));
        settings.setMarket("NSE");
        settings.setDefaultQuantity(10);
        settings.setUpdatedAt(NOW);
        assertThat(settings.getAccountId()).isEqualTo(1L);
        assertThat(settings.getOrderBudget()).isEqualByComparingTo("5000");
        assertThat(settings.getMarket()).isEqualTo("NSE");
        assertThat(settings.getDefaultQuantity()).isEqualTo(10);
        assertThat(settings.getUpdatedAt()).isEqualTo(NOW);

        InstrumentListing listing = new InstrumentListing();
        listing.setSymbol("SHOP");
        listing.setName("Shopify");
        listing.setAssetClass("EQUITY");
        listing.setCurrency("USD");
        listing.setTradable(true);
        assertThat(listing.getSymbol()).isEqualTo("SHOP");
        assertThat(listing.getName()).isEqualTo("Shopify");
        assertThat(listing.getAssetClass()).isEqualTo("EQUITY");
        assertThat(listing.getCurrency()).isEqualTo("USD");
        assertThat(listing.isTradable()).isTrue();

        Watchlist watchlist = new Watchlist();
        watchlist.setName("Tech");
        watchlist.setAccountId(1L);
        assertThat(watchlist.getName()).isEqualTo("Tech");
        assertThat(watchlist.getAccountId()).isEqualTo(1L);
        WatchlistItem item = new WatchlistItem(5L, 1L, "AAPL");
        item.setWatchlistId(6L);
        assertThat(item.getWatchlistId()).isEqualTo(6L);

        PriceAlert alert = new PriceAlert();
        alert.setAccountId(1L);
        alert.setSymbol("AAPL");
        alert.setDirection(AlertDirection.ABOVE);
        alert.setThreshold(new BigDecimal("333"));
        alert.setMode(AlertMode.ONCE);
        assertThat(alert.getSymbol()).isEqualTo("AAPL");
        assertThat(alert.getDirection()).isEqualTo(AlertDirection.ABOVE);
        assertThat(alert.getThreshold()).isEqualByComparingTo("333");
        assertThat(alert.getMode()).isEqualTo(AlertMode.ONCE);
        assertThat(alert.getAccountId()).isEqualTo(1L);

        StrategyExecution execution = new StrategyExecution();
        execution.setAccountId(1L);
        execution.setCreatedAt(NOW);
        assertThat(execution.getAccountId()).isEqualTo(1L);
        assertThat(execution.getCreatedAt()).isEqualTo(NOW);

        TradingStrategy strategy = new TradingStrategy();
        strategy.setDisabledReason(TradingStrategy.DISABLED_BY_USER);
        strategy.setCreatedAt(NOW);
        strategy.setUpdatedAt(NOW);
        assertThat(strategy.getDisabledReason()).isEqualTo("USER");
        assertThat(strategy.getCreatedAt()).isEqualTo(NOW);
        assertThat(strategy.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void triggerResponsesCopyEveryField() {
        PriceAlertTrigger trigger = new PriceAlertTrigger();
        trigger.setTriggerId(9L);
        trigger.setAlertId(4L);
        trigger.setSymbol("AAPL");
        trigger.setDirection(AlertDirection.ABOVE);
        trigger.setThreshold(new BigDecimal("333"));
        trigger.setMode(AlertMode.EVERY_QUOTE);
        trigger.setPrice(new BigDecimal("336.67"));
        trigger.setQuoteAt(NOW);
        trigger.setTriggeredAt(NOW.plusSeconds(5));
        trigger.setReason("AAPL 336.67 >= 333");

        PriceAlertTriggerResponse response = PriceAlertTriggerResponse.from(trigger);

        assertThat(response.triggerId()).isEqualTo(9L);
        assertThat(response.alertId()).isEqualTo(4L);
        assertThat(response.price()).isEqualByComparingTo("336.67");
        assertThat(response.reason()).isEqualTo("AAPL 336.67 >= 333");
    }
}
