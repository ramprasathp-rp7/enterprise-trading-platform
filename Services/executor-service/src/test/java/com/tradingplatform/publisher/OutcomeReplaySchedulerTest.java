package com.tradingplatform.publisher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.tradingplatform.domain.PendingOutcome;
import com.tradingplatform.enums.OrderStatus;
import com.tradingplatform.mappers.OrderMapper;

/**
 * OutcomeReplayScheduler: republishes FILLED / REJECTED trade events that never reached Kafka,
 * oldest first, past the grace period, stopping at the first failure.
 */
class OutcomeReplaySchedulerTest {

    private static final Instant EXECUTED_ON = Instant.parse("2026-10-08T09:30:00Z");

    private OrderMapper orderMapper;
    private TradeEventPublisher publisher;
    private PendingOutcome filled;
    private PendingOutcome rejected;

    @BeforeEach
    void setUp() {
        orderMapper = mock(OrderMapper.class);
        publisher = mock(TradeEventPublisher.class);
        filled = new PendingOutcome(UUID.randomUUID(), 42L, OrderStatus.FILLED, new BigDecimal("100.00"),
            new BigDecimal("99.50"), new BigDecimal("1.25"), null, EXECUTED_ON, BigDecimal.ONE);
        rejected = new PendingOutcome(UUID.randomUUID(), 42L, OrderStatus.REJECTED, new BigDecimal("100.00"),
            null, BigDecimal.ZERO, "No price available", EXECUTED_ON, null);
    }

    private OutcomeReplayScheduler scheduler(long publishTimeoutMs) {
        return new OutcomeReplayScheduler(orderMapper, publisher, 60_000, 100, publishTimeoutMs);
    }

    @Test
    void republishesPendingOutcomesOldestFirstPastTheGracePeriod() {
        when(orderMapper.findPendingOutcomes(any(Instant.class), anyInt())).thenReturn(List.of(filled, rejected));
        when(publisher.publishOutcome(any())).thenReturn(CompletableFuture.completedFuture(true));

        Instant before = Instant.now();
        scheduler(1_000).replayPendingOutcomes();

        ArgumentCaptor<Instant> olderThan = ArgumentCaptor.forClass(Instant.class);
        verify(orderMapper).findPendingOutcomes(olderThan.capture(), eq(100));
        assertFalse(olderThan.getValue().isAfter(before.minusMillis(60_000)));
        InOrder inOrder = inOrder(publisher);
        inOrder.verify(publisher).publishOutcome(filled);
        inOrder.verify(publisher).publishOutcome(rejected);
    }

    @Test
    void stopsAtTheFirstFailedPublishWhileKafkaIsDown() {
        when(orderMapper.findPendingOutcomes(any(Instant.class), anyInt())).thenReturn(List.of(filled, rejected));
        when(publisher.publishOutcome(any())).thenReturn(CompletableFuture.completedFuture(false));

        scheduler(1_000).replayPendingOutcomes();

        verify(publisher).publishOutcome(filled);
        verify(publisher, never()).publishOutcome(rejected);
    }

    @Test
    void skipsARowThatCannotBeSerializedAndCarriesOn() {
        when(orderMapper.findPendingOutcomes(any(Instant.class), anyInt())).thenReturn(List.of(filled, rejected));
        when(publisher.publishOutcome(filled)).thenThrow(new RuntimeException("Trade event serialization failed"));
        when(publisher.publishOutcome(rejected)).thenReturn(CompletableFuture.completedFuture(true));

        scheduler(1_000).replayPendingOutcomes();

        verify(publisher).publishOutcome(rejected);
    }

    @Test
    void doesNotResendAnOutcomeWhoseReplayIsStillInTheProducer() {
        when(orderMapper.findPendingOutcomes(any(Instant.class), anyInt())).thenReturn(List.of(filled));
        CompletableFuture<Boolean> pending = new CompletableFuture<>();
        when(publisher.publishOutcome(any())).thenReturn(pending);
        OutcomeReplayScheduler scheduler = scheduler(10);

        scheduler.replayPendingOutcomes();   // times out, still buffered
        scheduler.replayPendingOutcomes();   // skipped: in flight
        verify(publisher, times(1)).publishOutcome(filled);

        pending.complete(false);             // delivery finally failed
        scheduler.replayPendingOutcomes();   // retried
        verify(publisher, times(2)).publishOutcome(filled);
    }

    @Test
    void doesNothingWhenNoTradeEventIsPending() {
        when(orderMapper.findPendingOutcomes(any(Instant.class), anyInt())).thenReturn(List.of());

        scheduler(1_000).replayPendingOutcomes();

        verifyNoInteractions(publisher);
    }
}
