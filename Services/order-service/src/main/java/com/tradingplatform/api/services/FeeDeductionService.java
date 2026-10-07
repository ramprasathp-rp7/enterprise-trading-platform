package com.tradingplatform.api.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingplatform.api.events.EventType;
import com.tradingplatform.api.events.MessageEnvelope;
import com.tradingplatform.api.events.TradeEventPayload;
import com.tradingplatform.api.mappers.AccountMapper;
import com.tradingplatform.api.mappers.InstrumentMapper;
import com.tradingplatform.api.mappers.OrderMapper;
import com.tradingplatform.api.mappers.TransactionMapper;
import com.tradingplatform.domain.enums.OrderStatus;
import com.tradingplatform.domain.enums.TransactionAction;
import com.tradingplatform.domain.enums.TransactionType;
import com.tradingplatform.domain.model.Transaction;
import com.tradingplatform.domain.exception.OptimisticLockException;
import com.tradingplatform.domain.model.Account;
import com.tradingplatform.domain.model.AssetFeeStructure;
import com.tradingplatform.domain.model.Instrument;
import com.tradingplatform.domain.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * FeeDeductionService consumes ORDER_FILLED events from Kafka and deducts transaction fees.
 * 
 * This service operates in ASYNC mode (eventual consistency):
 * - ORDER_FILLED event signals that order has been settled and cash moved
 * - Fee is deducted asynchronously (not guaranteed to succeed immediately)
 * - If fee deduction fails due to optimistic lock, order remains FILLED (graceful failure)
 * - No rollback of settlement if fee fails (too late - already published to Kafka)
 * 
 * Design:
 * - Consumes from topic: trade-events
 * - Filters for OrderStatus.FILLED
 * - Fetches order to get fee amount
 * - Debits fee from account (with optimistic lock)
 * - Graceful failure: logs error and continues (doesn't throw exception)
 */
@Service
public class FeeDeductionService {
    
    private static final Logger logger = LoggerFactory.getLogger(FeeDeductionService.class);
    
    private final OrderMapper orderMapper;
    private final AccountMapper accountMapper;
    private final InstrumentMapper instrumentMapper;
    private final com.tradingplatform.api.mappers.AssetFeeMapper assetFeeMapper;
    private final TransactionMapper transactionMapper;
    private final ObjectMapper objectMapper;
    
    public FeeDeductionService(OrderMapper orderMapper, AccountMapper accountMapper, 
                               InstrumentMapper instrumentMapper, 
                               com.tradingplatform.api.mappers.AssetFeeMapper assetFeeMapper,
                               TransactionMapper transactionMapper,
                               ObjectMapper objectMapper) {
        this.orderMapper = orderMapper;
        this.accountMapper = accountMapper;
        this.instrumentMapper = instrumentMapper;
        this.assetFeeMapper = assetFeeMapper;
        this.transactionMapper = transactionMapper;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Kafka listener for ORDER_FILLED and ORDER_REJECTED events from the trade-events topic.
     * Only processes ORDER_FILLED events; deducts transaction fees asynchronously (eventual consistency model).
     * 
     * @param messageValue The full MessageEnvelope JSON from Kafka
     */
    @KafkaListener(topics = "trade-events", groupId = "fee-deduction-service")
    @Transactional
    public void handleOrderFilled(String messageValue, Acknowledgment ack) {
        try {
            // Step 1: Deserialize the MessageEnvelope from Kafka message value
            MessageEnvelope<TradeEventPayload> envelope = objectMapper.readValue(
                messageValue,
                new TypeReference<MessageEnvelope<TradeEventPayload>>() {}
            );
            
            // Step 2: Filter for ORDER_FILLED events only
            if (envelope.eventType() != EventType.ORDER_FILLED) {
                logger.debug("Skipping non-FILLED event: eventType={}", envelope.eventType());
                return;
            }
            
            // Step 3: Extract orderId from payload
            TradeEventPayload payload = envelope.payload();
            String orderIdStr = payload.orderId();
            UUID orderUuid = UUID.fromString(orderIdStr);
            
            // Step 4: Fetch order from database
            Order order = orderMapper.findOrderById(orderUuid);
            if (order == null) {
                logger.warn("Order not found for fee deduction: orderId={}", orderIdStr);
                if (ack != null) {
                    ack.acknowledge();
                }
                return;
            }
            
            // Step 5: Check if order is FILLED (guard against non-FILLED statuses)
            if (order.getStatus() != OrderStatus.FILLED) {
                logger.debug("Skipping fee deduction - order not FILLED: orderId={}, status={}", orderIdStr, order.getStatus());
                if (ack != null) {
                    ack.acknowledge();
                }
                return;
            }
            
            // Step 6: Extract executed price and original fee from the ORDER_FILLED event
            BigDecimal executedPrice = payload.executedPrice();
            BigDecimal originalFee = payload.originalFee();
            
            if (executedPrice == null) {
                logger.warn("No executed price in ORDER_FILLED event for fee deduction: orderId={}", orderIdStr);
                if (ack != null) {
                    ack.acknowledge();
                }
                return;
            }
            
            // Step 7: Recalculate actual fee based on executed price
            // Get instrument to determine asset type and fee structure
            Instrument instrument = instrumentMapper.findById(order.getInstrumentId());
            if (instrument == null) {
                logger.error("Instrument not found for fee recalculation: orderId={}, instrumentId={}", orderIdStr, order.getInstrumentId());
                if (ack != null) {
                    ack.acknowledge();
                }
                return;
            }
            
            // Calculate actual fee based on executed price, in account currency: the executed price is in
            // the instrument's currency and converts at the rate the executor fetched at fill
            BigDecimal fillFxRate = payload.fillFxRate() != null ? payload.fillFxRate()
                : order.getFillFxRate() != null ? order.getFillFxRate() : BigDecimal.ONE;
            BigDecimal actualNotional = executedPrice.multiply(BigDecimal.valueOf(order.getQuantity()))
                .multiply(fillFxRate)
                .setScale(2, java.math.RoundingMode.HALF_UP);
            String assetType = instrument.getAssetClass().name();
            AssetFeeStructure feeStructure = assetFeeMapper.findByAssetType(assetType);
            BigDecimal actualFee = (feeStructure != null) ? feeStructure.calculateFee(actualNotional) : BigDecimal.ZERO;

            // Step 7.1: Record BUY/SELL settlement transaction if missing.
            // This is required when settlement is performed by order-executor service.
            TransactionType settlementType = order.getSide().name().equals("BUY")
                ? TransactionType.BUY
                : TransactionType.SELL;
            TransactionAction settlementAction = settlementType == TransactionType.BUY
                ? TransactionAction.DEBIT
                : TransactionAction.CREDIT;
            if (!transactionMapper.existsByOrderIdAndTransactionType(orderUuid, settlementType)) {
                Transaction settlementTransaction = new Transaction(
                    UUID.randomUUID(),
                    order.getAccountId(),
                    orderUuid,
                    settlementType,
                    settlementAction,
                    actualNotional,
                    payload.executedOn() != null ? payload.executedOn() : Instant.now()
                );
                transactionMapper.insertTransaction(settlementTransaction);
                logger.info("Settlement transaction recorded: orderId={}, accountId={}, type={}, amount={}",
                    orderIdStr, order.getAccountId(), settlementType, actualNotional);
            }

            // If fee transaction already exists, this ORDER_FILLED event was already processed.
            // Skip fee debit to avoid charging fees twice on event replay.
            if (transactionMapper.existsByOrderIdAndTransactionType(orderUuid, TransactionType.FEE)) {
                logger.info("Fee transaction already exists, skipping duplicate fee deduction: orderId={}", orderIdStr);
                if (ack != null) {
                    ack.acknowledge();
                }
                return;
            }
            
            logger.debug("Fee recalculation: orderId={}, originalFee={}, actualFee={}, executed={}", 
                orderIdStr, originalFee, actualFee, executedPrice);
            
            // Step 8: If no fee to deduct, skip
            if (actualFee == null || actualFee.signum() <= 0) {
                logger.debug("No fee to deduct for order: orderId={}, fee={}", orderIdStr, actualFee);
                if (ack != null) {
                    ack.acknowledge();
                }
                return;
            }
            
            // Step 9: Fetch account
            Account account = accountMapper.findByAccountId(order.getAccountId());
            if (account == null) {
                logger.error("Account not found for fee deduction: orderId={}, accountId={}", orderIdStr, order.getAccountId());
                if (ack != null) {
                    ack.acknowledge();
                }
                return;
            }
            
            // Step 10: Debit actual fee from account (with optimistic lock)
            account.debit(actualFee);
            int feeRowsUpdated = accountMapper.updateAccount(account, account.getVersion() + 1);
            
            if (feeRowsUpdated == 0) {
                // Optimistic lock conflict - account was modified by another transaction
                // Graceful failure: log and skip (order remains FILLED, fee will be retried if message is replayed)
                logger.warn("Optimistic lock failure for fee deduction - account modified: orderId={}, accountId={}, actualFee={}", 
                    orderIdStr, order.getAccountId(), actualFee);
                if (ack != null) {
                    ack.acknowledge();
                }
                return;
            }
            
            // Step 11: Update order fee column with the actual fee that was deducted
            orderMapper.updateOrderFee(orderUuid, actualFee);
            
            // Step 12: Create a transaction record for the fee deduction
            UUID transactionId = UUID.randomUUID();
            Transaction feeTransaction = new Transaction(
                transactionId,
                order.getAccountId(),
                orderUuid,  // Link to the order that was filled
                TransactionType.FEE,
                TransactionAction.DEBIT,
                actualFee,
                Instant.now()
            );
            transactionMapper.insertTransaction(feeTransaction);
            logger.info("Fee deducted successfully: orderId={}, accountId={}, actualFee={}, originalFee={}, transactionId={}", 
                orderIdStr, order.getAccountId(), actualFee, originalFee, transactionId);

            if (ack != null) {
                ack.acknowledge();
            }
            
        } catch (IllegalArgumentException e) {
            // Invalid UUID format - log and skip
            logger.error("Invalid order ID format in fee deduction: messageValue={}, error={}", messageValue, e.getMessage());
            if (ack != null) {
                ack.acknowledge();
            }
        } catch (OptimisticLockException e) {
            // Optimistic locking conflict - log and continue without failing the listener
            logger.warn("OptimisticLockException during fee deduction: {}", e.getMessage());
            if (ack != null) {
                ack.acknowledge();
            }
        } catch (Exception e) {
            // Unexpected error - log and skip (graceful failure model)
            logger.error("Unexpected error during fee deduction: messageValue={}, error={}", messageValue, e.getMessage(), e);
            if (ack != null) {
                ack.acknowledge();
            }
        }
    }
}
