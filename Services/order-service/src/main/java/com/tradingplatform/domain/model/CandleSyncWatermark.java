package com.tradingplatform.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public final class CandleSyncWatermark {

    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_FAILED = "FAILED";

    private final Long id;
    private final Long instrumentId;
    private final LocalDate lastSyncedDate;
    private final String syncStatus;
    private final Instant lastSyncTime;
    private final boolean bootstrapCompleted;

    public CandleSyncWatermark(Long id,
                               Long instrumentId,
                               LocalDate lastSyncedDate,
                               String syncStatus,
                               Instant lastSyncTime,
                               boolean bootstrapCompleted) {
        this.id = id;
        this.instrumentId = Objects.requireNonNull(instrumentId, "instrumentId");
        this.lastSyncedDate = Objects.requireNonNull(lastSyncedDate, "lastSyncedDate");
        this.syncStatus = Objects.requireNonNull(syncStatus, "syncStatus");
        this.lastSyncTime = lastSyncTime;
        this.bootstrapCompleted = bootstrapCompleted;
    }

    public CandleSyncWatermark(Long instrumentId,
                               LocalDate lastSyncedDate,
                               String syncStatus,
                               boolean bootstrapCompleted) {
        this(null, instrumentId, lastSyncedDate, syncStatus, null, bootstrapCompleted);
    }

    public Long getId() {
        return id;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public LocalDate getLastSyncedDate() {
        return lastSyncedDate;
    }

    public String getSyncStatus() {
        return syncStatus;
    }

    public Instant getLastSyncTime() {
        return lastSyncTime;
    }

    public boolean isBootstrapCompleted() {
        return bootstrapCompleted;
    }
}
