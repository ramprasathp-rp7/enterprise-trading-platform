package com.tradingplatform.domain.dto;

import java.time.Instant;

/**
 * Summary of one account history snapshot run (scheduled or manually triggered).
 */
public record AccountHistorySnapshotResponse(
    Instant snapshotDate,
    int totalAccounts,
    int succeeded,
    int failed
) {
}
