package com.tradingplatform.api.mappers;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;

import com.tradingplatform.domain.dto.AccountHistoryResponse;

public interface AccountHistoryMapper {

    /**
     * Find all account history entries for a given account, ordered by snapshot date descending (latest first).
     */
    @ConstructorArgs({
        @Arg(column = "account_id", javaType = Long.class),
        @Arg(column = "snapshot_date", javaType = Instant.class),
        @Arg(column = "cash_balance", javaType = java.math.BigDecimal.class),
        @Arg(column = "networth", javaType = java.math.BigDecimal.class)
    })
    @Select("""
        SELECT account_id, snapshot_date, cash_balance, networth
        FROM account_history
        WHERE account_id = #{accountId}
        ORDER BY snapshot_date DESC
        LIMIT #{limit}
        """)
    List<AccountHistoryResponse> findByAccountId(@Param("accountId") Long accountId, @Param("limit") int limit);

    /**
     * Find account history entries for a given account within [startDate, endDate).
     */
    @ConstructorArgs({
        @Arg(column = "account_id", javaType = Long.class),
        @Arg(column = "snapshot_date", javaType = Instant.class),
        @Arg(column = "cash_balance", javaType = java.math.BigDecimal.class),
        @Arg(column = "networth", javaType = java.math.BigDecimal.class)
    })
    @Select("""
        SELECT account_id, snapshot_date, cash_balance, networth
        FROM account_history
        WHERE account_id = #{accountId}
        AND snapshot_date >= #{startDate}
        AND snapshot_date < #{endDate}
        ORDER BY snapshot_date DESC
        """)
    List<AccountHistoryResponse> findByAccountIdAndDateRange(
        @Param("accountId") Long accountId,
        @Param("startDate") Instant startDate,
        @Param("endDate") Instant endDate);

    /**
     * Insert an account history snapshot, or overwrite it if one already exists for that date
     * (so re-running the end-of-day job on the same day is safe).
     */
    @Insert("""
        INSERT INTO account_history (account_id, snapshot_date, cash_balance, networth, created_on)
        VALUES (#{accountId}, #{snapshotDate}, #{cashBalance}, #{networth}, now())
        ON CONFLICT (account_id, snapshot_date)
        DO UPDATE SET cash_balance = EXCLUDED.cash_balance,
                      networth = EXCLUDED.networth,
                      created_on = now()
        """)
    int insertAccountHistory(
        @Param("accountId") Long accountId,
        @Param("snapshotDate") Instant snapshotDate,
        @Param("cashBalance") java.math.BigDecimal cashBalance,
        @Param("networth") java.math.BigDecimal networth);
}


