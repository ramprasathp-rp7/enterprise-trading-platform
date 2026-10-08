package com.tradingplatform.api.services;

import com.tradingplatform.api.mappers.AccountMapper;
import com.tradingplatform.api.mappers.AssetFeeMapper;
import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.api.mappers.OrderMapper;
import com.tradingplatform.api.security.AccountAccessAuthorizer;
import com.tradingplatform.config.ExecutionMode;
import com.tradingplatform.domain.dto.OrderResponse;
import com.tradingplatform.domain.dto.PlaceOrderRequest;
import com.tradingplatform.domain.enums.OrderStatus;
import com.tradingplatform.domain.exception.DuplicateOrderException;
import com.tradingplatform.domain.exception.OptimisticLockException;
import com.tradingplatform.domain.exception.OrderNotCancellableException;
import com.tradingplatform.domain.exception.OrderNotFoundException;
import com.tradingplatform.domain.model.Account;
import com.tradingplatform.domain.model.Instrument;
import com.tradingplatform.domain.model.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderValidationService orderValidationService;
    private final OrderSettlementService orderSettlementService;
    private final OrderMapper orderMapper;
    private final AccountMapper accountMapper;
    private final InstrumentMapper instrumentMapper;
    private final AssetFeeMapper assetFeeMapper;
    private final AccountAccessAuthorizer accountAccessAuthorizer;
    private final OrderPublisher orderPublisher;
    
    @Value("${trading.execution.mode:ASYNC}")
    private ExecutionMode executionMode;
    
    public OrderService(OrderValidationService orderValidationService, 
                        OrderSettlementService orderSettlementService,
                        OrderMapper orderMapper,
                        AccountMapper accountMapper,
                        InstrumentMapper instrumentMapper,
                        AssetFeeMapper assetFeeMapper,
                        AccountAccessAuthorizer accountAccessAuthorizer,
                        OrderPublisher orderPublisher) {
        this.orderValidationService = orderValidationService;
        this.orderSettlementService = orderSettlementService;
        this.orderMapper = orderMapper;
        this.accountMapper = accountMapper;
        this.instrumentMapper = instrumentMapper;
        this.assetFeeMapper = assetFeeMapper;
        this.accountAccessAuthorizer = accountAccessAuthorizer;
        this.orderPublisher = orderPublisher;
    }

    /** Savepoint around SYNC-mode settlement; never used in ASYNC mode. */
    private TransactionTemplate settlementSavepoint;

    /**
     * Setter injection keeps the constructor unchanged for existing callers. The template opens a
     * nested transaction, which DataSourceTransactionManager implements as a JDBC savepoint inside
     * the placeOrder transaction.
     */
    @Autowired
    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
        this.settlementSavepoint = template;
    }

    @Transactional
    public OrderResponse placeOrder(PlaceOrderRequest orderRequest) {
        // Validate and create order (throws DomainException on validation failure)
        Order newOrder = orderValidationService.validateAndCreateOrder(orderRequest);

        // Persist order to database via OrderMapper
        // Rule 8 is enforced here: if idempotency key is duplicate, database UNIQUE constraint throws DuplicateKeyException
        try {
            orderMapper.insertOrder(
                newOrder.getId(),
                newOrder.getAccountId(),
                newOrder.getInstrumentId(),
                newOrder.getSide(),
                newOrder.getQuantity(),
                newOrder.getPrice(),
                OrderStatus.NEW,  // Order starts in NEW status
                newOrder.getIdempotencyKey(),
                newOrder.getPlacementFxRate(),
                executionMode != ExecutionMode.SYNC  // ASYNC: ORDER_PLACED pending until Kafka acknowledges it
            );
        } catch (DuplicateKeyException ex) {
            // Rule 8 violation: idempotency key already used
            // Mark order REJECTED and attach it to exception for audit trail
            newOrder.markRejected();
            throw new DuplicateOrderException(newOrder.getIdempotencyKey()).withRejectedOrder(newOrder);
        }

        // Fetch instrument to get symbol for ORDER_PLACED event (or for settlement in SYNC mode)
        Instrument instrument = instrumentMapper.findById(newOrder.getInstrumentId());
        if (instrument == null) {
            throw new IllegalStateException("Instrument not found for order: " + newOrder.getInstrumentId());
        }

        // Check execution mode: SYNC (Sprint 6) or ASYNC (Sprint 7)
        if (executionMode == ExecutionMode.SYNC) {
            // Sprint 6: SYNC execution mode - settle order immediately within the request
            if (settlementSavepoint == null) {
                throw new IllegalStateException("SYNC settlement needs a transaction manager");
            }
            // Try to settle the order immediately. Settlement and the fee run inside a savepoint
            // (PROPAGATION_NESTED): if any step fails, every write made so far (account debit,
            // position, settlement transaction, fee) is rolled back before the order is rejected,
            // and the outer transaction stays usable even after a database error.
            try {
                Order settledOrder = settlementSavepoint.execute(status -> {
                    // The fill happens within the placement request, so the placement rate is the fill rate
                    orderSettlementService.settle(newOrder, newOrder.getPrice(), newOrder.getPlacementFxRate());

                    // ← NEW: Deduct fee (SYNC mode - same transaction as settlement)
                    Account account = accountMapper.findByAccountId(newOrder.getAccountId());
                    BigDecimal fee = newOrder.getFee();
                    if (fee != null && fee.signum() > 0) {
                        account.debit(fee);
                        int feeRowsUpdated = accountMapper.updateAccount(account, account.getVersion() + 1);
                        if (feeRowsUpdated == 0) {
                            throw new OptimisticLockException("Account", account.getId());
                        }
                        // Update order with fee amount for audit trail
                        orderMapper.updateOrderFee(newOrder.getId(), fee);
                    }

                    // Settlement successful: order is now FILLED
                    // Fetch updated order from DB to confirm status and get executed price
                    return orderMapper.findOrderById(newOrder.getId());
                });
                return buildOrderResponse(settledOrder, instrument);
            } catch (Exception ex) {
                // Settlement failed (insufficient cash, optimistic lock, etc.) and was rolled back
                // to the savepoint. Mark order as REJECTED, with the reason the schema requires
                orderMapper.rejectOrder(newOrder.getId(), rejectionReason(ex));
                Order rejectedOrder = orderMapper.findOrderById(newOrder.getId());
                // Return error response for REJECTED order
                return buildOrderResponse(rejectedOrder, instrument);
            }
        } else {
            // Sprint 7: ASYNC execution mode (default) - publish to Kafka for async settlement
            // Register post-commit callback to publish ORDER_PLACED event to Kafka.
            // Publishing after commit ensures the order is persisted before notifying the executor.
            // If Kafka publish fails, the order remains NEW with event_pending set and
            // OrderReplayScheduler republishes it once the broker is back.
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        orderPublisher.publishOrderPlaced(newOrder, instrument);
                    }
                }
            );

            // Build and return OrderResponse with NEW status
            // The Trade Executor will fill this order asynchronously based on ORDER_PLACED event
            return buildOrderResponse(newOrder, instrument);
        }
    }

    public OrderResponse getOrderById(String orderId) {
        UUID id = UUID.fromString(orderId);

        // Fetch order from database via OrderMapper
        Order order = orderMapper.findOrderById(id);
        if (order == null) {
            throw new OrderNotFoundException(id, "Order not found: " + orderId);
        }

        // Fetch instrument for order response
        Instrument instrument = instrumentMapper.findById(order.getInstrumentId());
        if (instrument == null) {
            throw new IllegalStateException("Instrument not found for order: " + order.getInstrumentId());
        }

        // Build and return OrderResponse from entities
        return buildOrderResponse(order, instrument);
    }


    public OrderResponse cancelOrderById(String orderId) {
        UUID id = UUID.fromString(orderId);
        
        // Fetch the order first to check if it exists and get account ID
        Order order = orderMapper.findOrderById(id);
        if (order == null) {
            throw new OrderNotFoundException(id, "Order not found: " + orderId);
        }
        
        // Authorize access to this order's account
        accountAccessAuthorizer.assertTokenCanAccess(order.getAccountId());
        
        // Attempt to cancel the order atomically (only if in NEW status)
        int rowsUpdated = orderMapper.cancelOrderIfNew(id);

        if (rowsUpdated == 0) {
            throw new OrderNotCancellableException(orderId);
        }

        // Fetch the cancelled order from database
        Order cancelledOrder = orderMapper.findOrderById(id);
        if (cancelledOrder == null) {
            throw new OrderNotFoundException(id, "Order not found after cancellation: " + orderId);
        }

        // Fetch instrument for order response
        Instrument instrument = instrumentMapper.findById(cancelledOrder.getInstrumentId());
        if (instrument == null) {
            throw new IllegalStateException("Instrument not found for order: " + cancelledOrder.getInstrumentId());
        }

        // Record the outcome on trade-events (ORDER_CANCELLED), stamped with the stored cancel time; the
        // executor will skip this order. If Kafka is down, OrderReplayScheduler republishes it later.
        orderPublisher.publishOrderCancelled(id, cancelledOrder, instrument, cancelledOrder.getCancelledOn());

        // Build and return OrderResponse from entities
        return buildOrderResponse(cancelledOrder, instrument);
    }

    /** rejected_reason code for a SYNC settlement failure, in the same style as the seeded INSUFFICIENT_FUNDS */
    static String rejectionReason(Exception ex) {
        return ex instanceof OptimisticLockException ? "CONCURRENT_UPDATE" : "SETTLEMENT_FAILED";
    }

    private OrderResponse buildOrderResponse(Order order, Instrument instrument) {
        // Determine message based on order status
        String message = switch(order.getStatus()) {
            case NEW -> "Order accepted";
            case FILLED -> "Order executed";
            case REJECTED -> "Order rejected";
            case CANCELLED -> "Order cancelled";
            default -> "Unknown status";
        };

        // executedPrice is only populated for FILLED orders in SYNC mode
        // For other statuses (NEW, REJECTED, CANCELLED), it's empty
        java.util.Optional<BigDecimal> executedPrice = order.getStatus() == OrderStatus.FILLED
            ? java.util.Optional.ofNullable(order.getExecutedPrice())
            : java.util.Optional.empty();

        // Build and return OrderResponse
        return new OrderResponse(
            "ORD-" + order.getId().toString(),
            order.getStatus(),
            message,
            instrument.getSymbol(),
            order.getSide(),
            order.getQuantity(),
            order.getPrice(),
            order.getFee() != null ? order.getFee() : java.math.BigDecimal.ZERO,
            executedPrice
        );
    }
}
