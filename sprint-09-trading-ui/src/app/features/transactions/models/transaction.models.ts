/**
 * Transaction API Contract Models
 * Based on Sprint 07 Transaction API OpenAPI specification
 */

export type TransactionType = 'DEPOSIT' | 'WITHDRAW' | 'FEE' | 'BUY' | 'SELL';
export type ActionType = 'DEBIT' | 'CREDIT';

/**
 * Request DTO for deposits and withdrawals
 * POST /api/v1/accounts/{id}/deposits
 * POST /api/v1/accounts/{id}/withdrawals
 */
export interface TransactionRequest {
  amount: number;
}

/**
 * Full transaction response DTO
 * GET /api/v1/accounts/{id}/transactions/{transactionId}
 * POST /api/v1/accounts/{id}/deposits (201 response)
 * POST /api/v1/accounts/{id}/withdrawals (201 response)
 */
export interface TransactionResponse {
  transactionId: string; // UUID
  accountId: number;
  orderId?: string | null; // UUID, only for trade-related transactions
  transactionType: TransactionType;
  actionType: ActionType;
  amount: number;
  createdOn: string; // ISO 8601 date-time
}

/**
 * Compact transaction DTO for history listing
 * GET /api/v1/accounts/{id}/transactions?[type=X&from=X&to=X]
 * Omits orderId and accountId for cleaner responses
 */
export interface TransactionHistoryEntry {
  transactionId: string;
  transactionType: TransactionType;
  actionType: ActionType;
  amount: number;
  createdOn: string;
}

/**
 * Error response from API
 * Returned on 400, 404 status codes
 */
export interface ErrorResponse {
  errorCode: string; // e.g., TRANS_400, TRANS_404, AUTH-401
  message: string;
}

/**
 * Query parameters for transaction history endpoint
 * GET /api/v1/accounts/{id}/transactions
 */
export interface TransactionHistoryQuery {
  type?: TransactionType;
  from?: string; // ISO 8601 date-time
  to?: string;   // ISO 8601 date-time
}
