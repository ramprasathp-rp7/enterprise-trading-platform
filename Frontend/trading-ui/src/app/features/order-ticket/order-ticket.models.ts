/**
 * Local order-ticket-related types and interfaces
 */

import { OrderResponse, OrderSide } from '../../api/generated/trade';
import { errorCodeCatalogue } from '../../core/errors/error-codes';

export interface OrderTicketFormValue {
  symbol: string;
  side: OrderSide;
  quantity: string;
  price: string;
}

export interface OrderTicketSubmitInput {
  symbol: string;
  side: OrderSide;
  quantity: number;
  price: number;
}

export interface OrderTicketErrorState {
  errorCode: string | null;
  message: string;
}

export type OrderTicketResponse = OrderResponse;

/**
 * Network error message - displayed when service is unreachable (status 0)
 */
const NETWORK_ERROR_MESSAGE = 'The service could not be reached. Check whether the API is running and allows this dev-server origin.';

const TRADEABLE_SYMBOL_PATTERN = /^(?:[A-Z][A-Z0-9]*(?:\.(?:NS|BO))?|FX:[A-Z]{6}|X:[A-Z][A-Z0-9-]*)$/;
const WHOLE_QUANTITY_PATTERN = /^[1-9]\d*$/;
const PRICE_PATTERN = /^(?:0|[1-9]\d*)(?:\.\d{1,2})?$/;

export function isValidTradeableSymbol(value: string): boolean {
  const v = value.trim().toUpperCase();
  // enforce UI/business max length and pattern
  if (!v || v.length > 20) return false;
  return TRADEABLE_SYMBOL_PATTERN.test(v);
}

export function isValidWholeQuantity(value: string): boolean {
  return WHOLE_QUANTITY_PATTERN.test(value.trim());
}

export function isValidLimitPrice(value: string): boolean {
  const normalized = value.trim();
  return PRICE_PATTERN.test(normalized) && Number(normalized) > 0;
}

/**
 * Normalize HTTP error response to OrderTicketErrorState.
 * Uses the centralized ErrorCodeCatalogue for message mapping.
 */
export function normalizeOrderTicketError(error: unknown): OrderTicketErrorState {
  const response = error as {
    status?: number;
    error?: { errorCode?: string; message?: string };
    errorCode?: string;
  };

  // Network error - service unreachable
  if (response?.status === 0) {
    return {
      errorCode: 'NETWORK',
      message: NETWORK_ERROR_MESSAGE,
    };
  }

  // Extract error code from API response
  const errorCode = response?.error?.errorCode ?? response?.errorCode ?? 'UNKNOWN';

  // Use centralized error catalogue for trader-friendly message
  return {
    errorCode,
    message: errorCodeCatalogue.getMessage(errorCode),
  };
}
