package com.tradingplatform.domain.model;

import com.tradingplatform.domain.enums.OrderSide;
import com.tradingplatform.domain.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 Order with unique Idempotency key
 */
public final class Order {

    private final UUID id;
    private final Long accountId;
    private final Long instrumentId;
    private final OrderSide side;
    private final int quantity;
    private final BigDecimal price;
    private final String idempotencyKey;
    private final Instant createdOn;

    private OrderStatus status;
    private BigDecimal executedPrice;
    private BigDecimal fee;

    public Order(Long accountId, Long instrumentId, OrderSide side,
                 int quantity, BigDecimal price, String idempotencyKey) {
        this.id = UUID.randomUUID();
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.instrumentId = Objects.requireNonNull(instrumentId, "instrumentId");
        this.side = Objects.requireNonNull(side, "side");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be greater than zero: " + quantity);
        }
        this.quantity = quantity;
        this.price = Objects.requireNonNull(price, "price");
        if (price.signum() <= 0) {
            throw new IllegalArgumentException("price must be greater than zero: " + price);
        }
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        this.createdOn = Instant.now();
        this.status = OrderStatus.NEW;
        this.executedPrice = null;
        this.fee = null;
    }

     public UUID getId() {
        return id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }


    public OrderSide getSide() {
        return side;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedOn() {
        return createdOn;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getExecutedPrice() {
        return executedPrice;
    }

    public BigDecimal getFee() {
        return fee;
    }

    /**
     * Set the transaction fee for this order.
     * Called during validation after fee calculation.
     */
    public void setFee(BigDecimal fee) {
        if (fee != null && fee.signum() < 0) {
            throw new IllegalArgumentException("fee must not be negative: " + fee);
        }
        this.fee = fee;
    }

    /**
     * Mark order as FILLED with executed price only.
     * Used by settlement when fees are deducted separately.
     *
     * @throws IllegalStateException if the order is not currently NEW
     */
    public void markFilled(BigDecimal executedPrice) {
        requireNew("fill");
        this.executedPrice = Objects.requireNonNull(executedPrice, "executedPrice");
        this.status = OrderStatus.FILLED;
    }

    /**
      if new, status can be changed to FILLED
     *
     * @throws IllegalStateException if the order is not currently NEW
     */
    public void markFilled(BigDecimal executedPrice, BigDecimal fee) {
        requireNew("fill");
        this.executedPrice = Objects.requireNonNull(executedPrice, "executedPrice");
        this.fee = Objects.requireNonNull(fee, "fee");
        if (fee.signum() < 0) {
            throw new IllegalArgumentException("fee must not be negative: " + fee);
        }
        this.status = OrderStatus.FILLED;
    }

    /**
     * if new, status can be changed to REJECTED
     *
     * @throws IllegalStateException if the order is not currently NEW
     */
    public void markRejected() {
        requireNew("reject");
        this.status = OrderStatus.REJECTED;
    }

    /**
     * if new, status can be changed to CANCELLED
     *
     * @throws IllegalStateException if the order is not currently NEW
     */
    public void cancel() {
        requireNew("cancel");
        this.status = OrderStatus.CANCELLED;
    }

    // checks if the order status is NEW
    private void requireNew(String action) {
        if (status != OrderStatus.NEW) {
            throw new IllegalStateException(
                "cannot " + action + " order " + id + ": already " + status + ", which is terminal");
        }
    }

   
}
