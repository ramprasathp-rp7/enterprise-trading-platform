/**
 * Local blotter-related types and interfaces
 */

import { OrderHistoryEntry, OrderStatus } from '../../api/generated/trade';

/**
 * Order history entry with derived metadata
 */
export interface BlotterOrder extends OrderHistoryEntry {
  // Use OrderHistoryEntry from generated API types
}

/**
 * Order filters for blotter display
 */
export interface OrderFilters {
  status?: OrderStatus;
  symbol?: string;
}

/**
 * Outcome of the most recent cancel request, shown as a banner on the blotter
 */
export interface CancelResult {
  type: 'success' | 'error';
  orderId: string;
  message: string;
  errorCode?: string;
}
