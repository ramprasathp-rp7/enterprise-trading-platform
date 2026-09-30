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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
                newOrder.getIdempotencyKey()
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
            // Try to settle the order immediately
            try {
                orderSettlementService.settle(newOrder, newOrder.getPrice());
                
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
                Order settledOrder = orderMapper.findOrderById(newOrder.getId());
                return buildOrderResponse(settledOrder, instrument);
            } catch (Exception ex) {
                // Settlement failed (insufficient cash, optimistic lock, etc.)
                // Mark order as REJECTED in the database
                orderMapper.updateOrderStatus(newOrder.getId(), OrderStatus.REJECTED, null);
                Order rejectedOrder = orderMapper.findOrderById(newOrder.getId());
                // Return error response for REJECTED order
                return buildOrderResponse(rejectedOrder, instrument);
            }
        } else {
            // Sprint 7: ASYNC execution mode (default) - publish to Kafka for async settlement
            // Register post-commit callback to publish ORDER_PLACED event to Kafka.
            // Publishing after commit ensures the order is persisted before notifying the executor.
            // If Kafka publish fails, the order remains in the database and can be replayed.
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

        // Build and return OrderResponse from entities
        return buildOrderResponse(cancelledOrder, instrument);
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
