package com.tradingplatform.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.tradingplatform.enums.OrderSide;
import com.tradingplatform.enums.OrderStatus;

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
    /** Instrument-to-account FX rate at placement; prices the funds check and the reservation. */
    private BigDecimal placementFxRate = BigDecimal.ONE;
    /** Instrument-to-account FX rate fetched at fill; the rate of record for settlement. */
    private BigDecimal fillFxRate;
    /** Realised P&L in account currency, booked when a SELL fills. */
    private BigDecimal realisedPnl;

    public Order(Long accountId, Long instrumentId, OrderSide side,
                 int quantity, BigDecimal price, String idempotencyKey) {
        this(UUID.randomUUID(), accountId, instrumentId, side, quantity, price, idempotencyKey);
    }

    public Order(UUID id, Long accountId, Long instrumentId, OrderSide side,
                 int quantity, BigDecimal price, String idempotencyKey) {
        this.id = Objects.requireNonNull(id, "id");
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

    /**
     * Reconstruction constructor, for mapping an existing row back from the
     * database (e.g. OrderMapper/AccountMapper read queries). Unlike the
     * two constructors above, this does NOT default status/createdOn — a
     * FILLED order fetched from the DB must come back as FILLED, not NEW,
     * and with its real created_on, not the moment of this call.
     * One parameter per mapped column: OrderMapper binds the row to it positionally via @Arg.
     */
    @SuppressWarnings("java:S107")
    public Order(UUID id, Long accountId, Long instrumentId, OrderSide side, int quantity,
                 BigDecimal price, String idempotencyKey, Instant createdOn,
                 OrderStatus status, BigDecimal executedPrice, BigDecimal fee) {
        this.id = Objects.requireNonNull(id, "id");
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.instrumentId = Objects.requireNonNull(instrumentId, "instrumentId");
        this.side = Objects.requireNonNull(side, "side");
        this.quantity = quantity;
        this.price = Objects.requireNonNull(price, "price");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        this.createdOn = createdOn;
        this.status = Objects.requireNonNull(status, "status");
        this.executedPrice = executedPrice;
        this.fee = fee;
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

    public BigDecimal getPlacementFxRate() {
        return placementFxRate;
    }

    public void setPlacementFxRate(BigDecimal placementFxRate) {
        if (placementFxRate != null && placementFxRate.signum() <= 0) {
            throw new IllegalArgumentException("placementFxRate must be greater than zero: " + placementFxRate);
        }
        this.placementFxRate = placementFxRate != null ? placementFxRate : BigDecimal.ONE;
    }

    public BigDecimal getFillFxRate() {
        return fillFxRate;
    }

    public void setFillFxRate(BigDecimal fillFxRate) {
        this.fillFxRate = fillFxRate;
    }

    public BigDecimal getRealisedPnl() {
        return realisedPnl;
    }

    public void setRealisedPnl(BigDecimal realisedPnl) {
        this.realisedPnl = realisedPnl;
    }

    /**
     * Cash a NEW order holds back, in account currency: limit price times quantity at the placement
     * rate, plus the fee. A BUY fills at or below its limit, so the fill can only differ from this by
     * the FX move between placement and fill.
     */
    public BigDecimal reservedAmount() {
        BigDecimal notional = price.multiply(BigDecimal.valueOf(quantity)).multiply(placementFxRate)
            .setScale(2, java.math.RoundingMode.HALF_UP);
        return notional.add(fee != null ? fee : BigDecimal.ZERO);
    }

    /**
     * Set the fee for this order.
     * Used during validation phase to store the calculated fee.
     *
     * @param fee The fee amount (must not be negative)
     */
    public void setFee(BigDecimal fee) {
        if (fee != null && fee.signum() < 0) {
            throw new IllegalArgumentException("fee must not be negative: " + fee);
        }
        this.fee = fee;
    }

    /**
     * Mark order as FILLED with executed price only (no fee).
     * Used in settlement phase when fee is deducted separately.
     *
     * @throws IllegalStateException if the order is not currently NEW
     */
    public void markFilled(BigDecimal executedPrice) {
        requireNew("fill");
        this.executedPrice = Objects.requireNonNull(executedPrice, "executedPrice");
        this.status = OrderStatus.FILLED;
    }

    /**
     * Mark order as FILLED with executed price and fee.
     * Legacy method for backward compatibility.
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