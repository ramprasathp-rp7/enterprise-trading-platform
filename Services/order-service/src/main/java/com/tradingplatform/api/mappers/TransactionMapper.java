package com.tradingplatform.api.mappers;

import com.tradingplatform.domain.model.Transaction;
import com.tradingplatform.domain.enums.TransactionAction;
import com.tradingplatform.domain.enums.TransactionType;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * MyBatis mapper for transaction data access.
 * Handles persistence and retrieval of transactions.
 */
public interface TransactionMapper {

    /**
     * Inserts a new transaction record.
     *
     * @param transaction the transaction to persist
     */
    @Insert("INSERT INTO transactions (transaction_id, account_id, order_id, transaction_type, action_type, amount, created_on) " +
            "VALUES (#{transaction.transactionId}, #{transaction.accountId}, #{transaction.orderId}, " +
            "#{transaction.transactionType}, #{transaction.actionType}, #{transaction.amount}, #{transaction.createdOn})")
    void insertTransaction(@Param("transaction") Transaction transaction);

    /**
     * Finds a transaction by its ID.
     *
     * @param transactionId the transaction ID (UUID)
     * @return the transaction if found, null otherwise
     */
    @ConstructorArgs({
        @Arg(column = "account_id", javaType = Long.class),
        @Arg(column = "order_id", javaType = UUID.class),
        @Arg(column = "transaction_type", javaType = TransactionType.class),
        @Arg(column = "action_type", javaType = TransactionAction.class),
        @Arg(column = "amount", javaType = java.math.BigDecimal.class),
        @Arg(column = "created_on", javaType = Instant.class),
        @Arg(column = "transaction_id", javaType = UUID.class)
    })
    @Select("SELECT transaction_id, account_id, order_id, transaction_type, action_type, amount, created_on " +
            "FROM transactions WHERE transaction_id = #{transactionId}")
    Transaction findByTransactionId(@Param("transactionId") UUID transactionId);

    /**
     * Finds all transactions for an account.
     *
     * @param accountId the account ID
     * @return list of transactions ordered by creation time (most recent first)
     */
    @ConstructorArgs({
        @Arg(column = "account_id", javaType = Long.class),
        @Arg(column = "order_id", javaType = UUID.class),
        @Arg(column = "transaction_type", javaType = TransactionType.class),
        @Arg(column = "action_type", javaType = TransactionAction.class),
        @Arg(column = "amount", javaType = java.math.BigDecimal.class),
        @Arg(column = "created_on", javaType = Instant.class),
        @Arg(column = "transaction_id", javaType = UUID.class)
    })
    @Select("SELECT transaction_id, account_id, order_id, transaction_type, action_type, amount, created_on " +
            "FROM transactions WHERE account_id = #{accountId} ORDER BY created_on DESC")
    List<Transaction> findByAccountId(@Param("accountId") Long accountId);

    /**
     * Finds transactions for an account within a date range.
     *
     * @param accountId the account ID
     * @param from the start date (inclusive)
     * @param to the end date (inclusive)
     * @return list of transactions ordered by creation time (most recent first)
     */
    @ConstructorArgs({
        @Arg(column = "account_id", javaType = Long.class),
        @Arg(column = "order_id", javaType = UUID.class),
        @Arg(column = "transaction_type", javaType = TransactionType.class),
        @Arg(column = "action_type", javaType = TransactionAction.class),
        @Arg(column = "amount", javaType = java.math.BigDecimal.class),
        @Arg(column = "created_on", javaType = Instant.class),
        @Arg(column = "transaction_id", javaType = UUID.class)
    })
    @Select("SELECT transaction_id, account_id, order_id, transaction_type, action_type, amount, created_on " +
            "FROM transactions WHERE account_id = #{accountId} " +
            "AND created_on BETWEEN #{from} AND #{to} ORDER BY created_on DESC")
    List<Transaction> findByAccountIdAndDateRange(@Param("accountId") Long accountId,
                                                   @Param("from") Instant from,
                                                   @Param("to") Instant to);

    /**
     * Finds transactions for an account filtered by transaction type.
     *
     * @param accountId the account ID
     * @param transactionType the transaction type
     * @return list of transactions of the specified type ordered by creation time (most recent first)
     */
    @ConstructorArgs({
        @Arg(column = "account_id", javaType = Long.class),
        @Arg(column = "order_id", javaType = UUID.class),
        @Arg(column = "transaction_type", javaType = TransactionType.class),
        @Arg(column = "action_type", javaType = TransactionAction.class),
        @Arg(column = "amount", javaType = java.math.BigDecimal.class),
        @Arg(column = "created_on", javaType = Instant.class),
        @Arg(column = "transaction_id", javaType = UUID.class)
    })
    @Select("SELECT transaction_id, account_id, order_id, transaction_type, action_type, amount, created_on " +
            "FROM transactions WHERE account_id = #{accountId} AND transaction_type = #{transactionType} " +
            "ORDER BY created_on DESC")
    List<Transaction> findByAccountIdAndTransactionType(@Param("accountId") Long accountId,
                                                        @Param("transactionType") TransactionType transactionType);

    /**
     * Checks if a transaction already exists for a given order and transaction type.
     * Used for idempotent processing of replayed ORDER_FILLED events.
     *
     * @param orderId the order ID
     * @param transactionType the transaction type to check
     * @return true if matching transaction exists, false otherwise
     */
    @Select("SELECT COUNT(1) > 0 FROM transactions WHERE order_id = #{orderId} AND transaction_type = #{transactionType}")
    boolean existsByOrderIdAndTransactionType(@Param("orderId") UUID orderId,
                                              @Param("transactionType") TransactionType transactionType);
    
    /**
     * Retrieves all transactions across all accounts (admin only).
     * Used for system-wide auditing and compliance reporting.
     *
     * @return list of all transactions ordered by creation time (most recent first)
     */
    @ConstructorArgs({
        @Arg(column = "account_id", javaType = Long.class),
        @Arg(column = "order_id", javaType = UUID.class),
        @Arg(column = "transaction_type", javaType = TransactionType.class),
        @Arg(column = "action_type", javaType = TransactionAction.class),
        @Arg(column = "amount", javaType = java.math.BigDecimal.class),
        @Arg(column = "created_on", javaType = Instant.class),
        @Arg(column = "transaction_id", javaType = UUID.class)
    })
    @Select("SELECT transaction_id, account_id, order_id, transaction_type, action_type, amount, created_on " +
            "FROM transactions ORDER BY created_on DESC")
    List<Transaction> findAllTransactions();
    
    /**
     * Retrieves all transactions within a date range across all accounts (admin only).
     * Used for system-wide auditing and compliance reporting.
     *
     * @param from the start date (inclusive)
     * @param to the end date (inclusive)
     * @return list of transactions ordered by creation time (most recent first)
     */
    @ConstructorArgs({
        @Arg(column = "account_id", javaType = Long.class),
        @Arg(column = "order_id", javaType = UUID.class),
        @Arg(column = "transaction_type", javaType = TransactionType.class),
        @Arg(column = "action_type", javaType = TransactionAction.class),
        @Arg(column = "amount", javaType = java.math.BigDecimal.class),
        @Arg(column = "created_on", javaType = Instant.class),
        @Arg(column = "transaction_id", javaType = UUID.class)
    })
    @Select("SELECT transaction_id, account_id, order_id, transaction_type, action_type, amount, created_on " +
            "FROM transactions WHERE created_on BETWEEN #{from} AND #{to} ORDER BY created_on DESC")
    List<Transaction> findAllTransactionsByDateRange(@Param("from") Instant from,
                                                      @Param("to") Instant to);
}
