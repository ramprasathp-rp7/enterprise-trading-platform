package com.tradingplatform.api.services;

import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.api.mappers.OrderMapper;
import com.tradingplatform.domain.model.Instrument;
import com.tradingplatform.domain.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

/**
 * Republishes the events this service owns that never reached Kafka (ASYNC mode only):
 * ORDER_PLACED for NEW orders and ORDER_CANCELLED for CANCELLED orders.
 *
 * Both are published after the database write, fire-and-forget, so an order placed or cancelled
 * while the broker is down keeps orders.event_pending set. Each run this job loads the oldest such
 * orders and publishes their event again; OrderPublisher clears event_pending on acknowledgement.
 *
 * - Orders whose event already reached Kafka have event_pending cleared and are left alone, so a
 *   stopped executor (market closed) never causes the queue to be republished.
 * - An order placed and then cancelled while Kafka was down only gets ORDER_CANCELLED: the cancel
 *   moved it out of NEW, and the executor never needs to see it.
 * - Only orders untouched for the grace period are picked up, so the live publish, still inside the
 *   producer's delivery timeout, is not raced. Keep the grace above delivery.timeout.ms.
 * - A run waits on each publish and stops at the first failure: the broker is still down, and the
 *   rest would only fail the same way. Oldest first keeps per-account order on the topic.
 * - A duplicate (acknowledged but not marked) is harmless: the executor only settles NEW orders, and
 *   ORDER_CANCELLED carries the same eventId each time, so notifications ignore the copy.
 *
 * FILLED / REJECTED events are the executor's, and its OutcomeReplayScheduler replays them.
 *
 * @see OrderPublisher#publishOrderPlaced
 * @see OrderPublisher#publishOrderCancelled
 */
@Component
@ConditionalOnProperty(name = "trading.execution.mode", havingValue = "ASYNC", matchIfMissing = true)
public class OrderReplayScheduler {

    private static final Logger logger = LoggerFactory.getLogger(OrderReplayScheduler.class);

    private final OrderMapper orderMapper;
    private final InstrumentMapper instrumentMapper;
    private final OrderPublisher orderPublisher;
    private final long graceMs;
    private final int batchSize;
    private final long publishTimeoutMs;

    /** Orders whose replayed publish is still pending in the producer; not sent again until it completes. */
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public OrderReplayScheduler(OrderMapper orderMapper,
                                InstrumentMapper instrumentMapper,
                                OrderPublisher orderPublisher,
                                @Value("${trading.order-replay.grace-ms:60000}") long graceMs,
                                @Value("${trading.order-replay.batch-size:100}") int batchSize,
                                @Value("${trading.order-replay.publish-timeout-ms:5000}") long publishTimeoutMs) {
        this.orderMapper = Objects.requireNonNull(orderMapper, "orderMapper cannot be null");
        this.instrumentMapper = Objects.requireNonNull(instrumentMapper, "instrumentMapper cannot be null");
        this.orderPublisher = Objects.requireNonNull(orderPublisher, "orderPublisher cannot be null");
        this.graceMs = graceMs;
        this.batchSize = batchSize;
        this.publishTimeoutMs = publishTimeoutMs;
    }

    @Scheduled(initialDelayString = "${trading.order-replay.initial-delay-ms:30000}",
               fixedDelayString = "${trading.order-replay.interval-ms:30000}")
    public void replayUnpublishedOrders() {
        List<Order> pending;
        try {
            pending = orderMapper.findPendingNewOrders(olderThan(), batchSize);
        } catch (Exception ex) {
            logger.warn("Order replay skipped: could not load pending NEW orders: {}", ex.getMessage());
            return;
        }
        replay("ORDER_PLACED", pending, orderPublisher::publishOrderPlaced);
    }

    @Scheduled(initialDelayString = "${trading.order-replay.initial-delay-ms:30000}",
               fixedDelayString = "${trading.order-replay.interval-ms:30000}")
    public void replayPendingCancellations() {
        List<Order> pending;
        try {
            pending = orderMapper.findPendingCancellations(olderThan(), batchSize);
        } catch (Exception ex) {
            logger.warn("Order replay skipped: could not load pending cancellations: {}", ex.getMessage());
            return;
        }
        replay("ORDER_CANCELLED", pending, (order, instrument) ->
            orderPublisher.publishOrderCancelled(order.getId(), order, instrument, order.getCancelledOn()));
    }

    private Instant olderThan() {
        return Instant.now().minusMillis(graceMs);
    }

    private void replay(String eventType, List<Order> pending,
                        BiFunction<Order, Instrument, CompletableFuture<Boolean>> publish) {
        if (pending == null || pending.isEmpty()) {
            return;
        }

        int replayed = 0;
        for (Order order : pending) {
            if (inFlight.contains(order.getId())) {
                continue;
            }
            Instrument instrument = instrumentMapper.findById(order.getInstrumentId());
            if (instrument == null) {
                logger.warn("Order replay skipped order {}: instrument {} not found", order.getId(), order.getInstrumentId());
                continue;
            }
            if (!publishAndWait(order.getId(), publish.apply(order, instrument))) {
                logger.warn("Order replay stopped: Kafka unavailable; {} of {} pending {} events not republished",
                    pending.size() - replayed, pending.size(), eventType);
                break;
            }
            replayed++;
        }
        if (replayed > 0) {
            logger.info("Order replay: republished {} for {} orders", eventType, replayed);
        }
    }

    private boolean publishAndWait(UUID orderId, CompletableFuture<Boolean> published) {
        inFlight.add(orderId);
        published.whenComplete((ok, ex) -> inFlight.remove(orderId));
        try {
            return Boolean.TRUE.equals(published.get(publishTimeoutMs, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception ex) {
            // Timed out: still buffered in the producer; it stays in flight until it completes
            return false;
        }
    }
}
