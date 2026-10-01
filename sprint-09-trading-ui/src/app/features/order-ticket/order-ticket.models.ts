/**
 * Local order-ticket-related types and interfaces
 */

import { OrderResponse, OrderSide } from '../../api/generated/trade';

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

const NETWORK_ERROR_MESSAGE = 'The service could not be reached. Check whether the API is running and allows this dev-server origin.';
const UNKNOWN_ERROR_MESSAGE = 'Something unexpected happened. Please try again.';

const TRADEABLE_SYMBOL_PATTERN = /^(?:[A-Z][A-Z0-9]*(?:\.(?:NS|BO))?|FX:[A-Z]{3}\/[A-Z]{3}|X:[A-Z][A-Z0-9-]*)$/;
const WHOLE_QUANTITY_PATTERN = /^[1-9]\d*$/;
const PRICE_PATTERN = /^(?:0|[1-9]\d*)(?:\.\d{1,2})?$/;

export function isValidTradeableSymbol(value: string): boolean {
  return TRADEABLE_SYMBOL_PATTERN.test(value.trim().toUpperCase());
}

export function isValidWholeQuantity(value: string): boolean {
  return WHOLE_QUANTITY_PATTERN.test(value.trim());
}

export function isValidLimitPrice(value: string): boolean {
  const normalized = value.trim();
  return PRICE_PATTERN.test(normalized) && Number(normalized) > 0;
}

export function normalizeOrderTicketError(error: unknown): OrderTicketErrorState {
  const response = error as {
    status?: number;
    error?: { errorCode?: string; message?: string };
    errorCode?: string;
  };

  if (response?.status === 0) {
    return {
      errorCode: 'NETWORK',
      message: NETWORK_ERROR_MESSAGE,
    };
  }

  const errorCode = response?.error?.errorCode ?? response?.errorCode ?? 'UNKNOWN';

  return {
    errorCode,
    message: getOrderTicketErrorMessage(errorCode),
  };
}

function getOrderTicketErrorMessage(errorCode: string): string {
  switch (errorCode) {
    case 'ACC-404':
      return 'The account could not be found.';
    case 'ACC-403':
      return 'This account is not active, or is not the one this sign-in may trade.';
    case 'INS-404':
      return 'The instrument is not one that can be traded.';
    case 'ORD-400':
      return 'There is not enough cash for this order.';
    case 'ORD-409':
      return 'There are not enough holdings to sell, or this order has already been placed.';
    case 'VAL-422':
      return 'A field is not acceptable.';
    case 'AUTH-401':
      return 'The session has expired or the sign-in was refused.';
    case 'AUTH-409':
      return 'That username is already taken.';
    case 'NETWORK':
      return NETWORK_ERROR_MESSAGE;
    default:
      return UNKNOWN_ERROR_MESSAGE;
  }
}
