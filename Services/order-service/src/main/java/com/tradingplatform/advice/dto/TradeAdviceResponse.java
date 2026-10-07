package com.tradingplatform.advice.dto;

import java.time.Instant;
import java.util.List;

/**
 * Trade advice for an account's held and watched instruments.
 *
 * @param accountId   the account
 * @param generatedAt when the advice was computed
 * @param disclaimer  the advice is educational, not investment advice
 * @param signals     one entry per held or watched instrument, signals first
 */
public record TradeAdviceResponse(long accountId, Instant generatedAt, String disclaimer, List<SignalResponse> signals) {
}
