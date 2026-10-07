/**
 * Transaction API Contract Models
 * Re-exported from the OpenAPI-generated client (contracts/transactions-api.yaml).
 * Regenerate with `npm run generate:transactions` rather than editing these shapes by hand.
 */
import { TransactionHistoryEntry } from '../../../api/generated/transactions';

export type {
  TransactionRequest,
  TransactionResponse,
  TransactionHistoryEntry,
  ErrorResponse,
} from '../../../api/generated/transactions';

export type TransactionType = TransactionHistoryEntry.TransactionTypeEnum;
export type ActionType = TransactionHistoryEntry.ActionTypeEnum;

/**
 * Query parameters for transaction history endpoint
 * GET /api/v1/accounts/{id}/transactions
 */
export interface TransactionHistoryQuery {
  type?: TransactionType;
  from?: string; // ISO 8601 date-time
  to?: string;   // ISO 8601 date-time
}
