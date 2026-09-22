package com.tradingplatform.config;

/**
 * Trading execution mode enum.
 * 
 * Determines how orders are processed:
 * - SYNC (Sprint 6): Validates, settles in-process, returns FILLED/REJECTED immediately
 * - ASYNC (Sprint 7): Validates, publishes to Kafka topic, returns NEW for async settlement
 */
public enum ExecutionMode {
    SYNC,   // Synchronous execution: settle order within request, return FILLED/REJECTED
    ASYNC   // Asynchronous execution: publish order to Kafka, return NEW
}
