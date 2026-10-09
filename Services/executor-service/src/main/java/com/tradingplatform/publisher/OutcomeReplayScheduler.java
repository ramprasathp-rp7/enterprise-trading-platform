package com.tradingplatform.publisher;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.tradingplatform.domain.PendingOutcome;
import com.tradingplatform.mappers.OrderMapper;

/**
 * Republishes ORDER_FILLED / ORDER_REJECTED for settled orders whose trade event never reached Kafka.
 *
 * The executor settles an order, commits, and then publishes its trade event, fire-and-forget. If
 * Kafka is down at that moment the order keeps orders.event_pending set, and without this job fee
 * deduction and the customer notification would never happen (a redelivered ORDER_PLACED is skipped,
 * because the order is no longer NEW). Each run loads the oldest pending outcomes and publishes them
 * again; TradeEventPublisher clears event_pending on acknowledgement.
 *
 * - Only orders untouched for the grace period are picked up, so the live publish, still inside the
 *   producer's delivery timeout, is not raced. Keep the grace above delivery.timeout.ms.
 * - A run waits on each publish and stops at the first failure: the broker is still down, and the
 *   rest would only fail the same way. Oldest first keeps per-account order on the topic.
 * - A duplicate (acknowledged but not marked) is harmless: the event carries the same eventId, so
 *   notifications ignore it, and fee deduction skips an order whose fee transaction exists.
 *
 * ORDER_PLACED and ORDER_CANCELLED belong to the Trade REST API, whose OrderReplayScheduler replays them.
 *
 * @see TradeEventPublisher#publishOutcome
 */
@Component
public class OutcomeReplayScheduler {

    private static final Logger logger = LoggerFactory.getLogger(OutcomeReplayScheduler.class);

    private final OrderMapper orderMapper;
    private final TradeEventPublisher tradeEventPublisher;
    private final long graceMs;
    private final int batchSize;
    private final long publishTimeoutMs;

    /** Orders whose replayed publish is still pending in the producer; not sent again until it completes. */
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public OutcomeReplayScheduler(OrderMapper orderMapper,
                                  TradeEventPublisher tradeEventPublisher,
                                  @Value("${trading.outcome-replay.grace-ms:60000}") long graceMs,
                                  @Value("${trading.outcome-replay.batch-size:100}") int batchSize,
                                  @Value("${trading.outcome-replay.publish-timeout-ms:5000}") long publishTimeoutMs) {
        this.orderMapper = Objects.requireNonNull(orderMapper, "orderMapper cannot be null");
        this.tradeEventPublisher = Objects.requireNonNull(tradeEventPublisher, "tradeEventPublisher cannot be null");
        this.graceMs = graceMs;
        this.batchSize = batchSize;
        this.publishTimeoutMs = publishTimeoutMs;
    }

    @Scheduled(initialDelayString = "${trading.outcome-replay.initial-delay-ms:30000}",
               fixedDelayString = "${trading.outcome-replay.interval-ms:30000}")
    public void replayPendingOutcomes() {
        List<PendingOutcome> pending;
        try {
            pending = orderMapper.findPendingOutcomes(Instant.now().minusMillis(graceMs), batchSize);
        } catch (Exception ex) {
            logger.warn("Outcome replay skipped: could not load pending trade events: {}", ex.getMessage());
            return;
        }
        if (pending == null || pending.isEmpty()) {
            return;
        }

        int replayed = 0;
        for (PendingOutcome outcome : pending) {
            ReplayResult result = replay(outcome);
            if (result == ReplayResult.KAFKA_UNAVAILABLE) {
                logger.warn("Outcome replay stopped: Kafka unavailable; {} of {} pending trade events not republished",
                    pending.size() - replayed, pending.size());
                break;
            }
            if (result == ReplayResult.PUBLISHED) {
                replayed++;
            }
        }
        if (replayed > 0) {
            logger.info("Outcome replay: republished trade events for {} orders", replayed);
        }
    }

    private enum ReplayResult { PUBLISHED, SKIPPED, KAFKA_UNAVAILABLE }

    private ReplayResult replay(PendingOutcome outcome) {
        if (inFlight.contains(outcome.id())) {
            return ReplayResult.SKIPPED;
        }
        CompletableFuture<Boolean> published;
        try {
            published = tradeEventPublisher.publishOutcome(outcome);
        } catch (RuntimeException ex) {
            // Serialization failure: this row cannot be published as it is; carry on with the rest
            logger.error("Outcome replay could not build trade event: orderId={}, reason={}", outcome.id(), ex.getMessage());
            return ReplayResult.SKIPPED;
        }
        return awaitPublish(outcome.id(), published) ? ReplayResult.PUBLISHED : ReplayResult.KAFKA_UNAVAILABLE;
    }

    private boolean awaitPublish(UUID orderId, CompletableFuture<Boolean> published) {
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
