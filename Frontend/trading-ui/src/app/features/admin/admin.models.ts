/**
 * Data models for Admin Dashboard
 */

export interface AnalyticsMetric {
  timestamp: Date;
  value: number;
  label?: string;
}

export interface TradeVolumeData {
  date: string;
  volume: number;
  value: number;
}

export interface FillRateData {
  date: string;
  totalOrders: number;
  filledOrders: number;
  fillRate: number;
}

export interface AccountActivityData {
  accountId: string;
  holderName: string;
  orderCount: number;
  totalTradeValue: number;
}

export interface ExposureData {
  symbol: string;
  assetClass: string;
  buyVolume: number;
  sellVolume: number;
  netExposure: number;
  totalValue: number;
}

export interface AverageTradeSize {
  date: string;
  averageSize: number;
  minSize: number;
  maxSize: number;
}

export interface AssetFee {
  assetType: string;
  flatFee: number;
  percentageFee: number;
  updatedOn: Date;
  updatedBy?: string;
}

export interface FeeUpdate {
  assetType: string;
  flatFee: number;
  percentageFee: number;
}

export interface AdminUser {
  id: string;
  name: string;
  email: string;
  role: 'SUPER_ADMIN' | 'ADMIN' | 'ANALYST';
  permissions: string[];
}

export interface AuditLog {
  id: string;
  action: string;
  entity: string;
  entityId: string;
  oldValue?: any;
  newValue?: any;
  performedBy: string;
  performedAt: Date;
  ipAddress?: string;
}

export interface AccountStatus {
  accountId: string;
  status: 'ACTIVE' | 'SUSPENDED' | 'CLOSED';
  reason?: string;
  changedOn: Date;
  changedBy: string;
}

export interface SystemHealth {
  etlLastRun: Date;
  etlStatus: 'RUNNING' | 'SUCCESS' | 'FAILED';
  dataFreshness: number; // minutes old
  analyticsRowCount: number;
  lastReconciliation: Date;
  isHealthy: boolean;
}

/**
 * Account Management Models
 */

export interface AccountDetails {
  id: number;
  accountId: string;
  holderName: string;
  cashBalance: number;
  status: 'ACTIVE' | 'SUSPENDED' | 'CLOSED';
  version: number;
  lastUpdated: Date;
}

export interface Position {
  accountId: number;
  symbol: string;
  quantity: number;
  averageCost: number;
}

export interface Order {
  orderId: string;
  accountId: number;
  symbol: string;
  side: 'BUY' | 'SELL';
  quantity: number;
  price: number;
  executedPrice?: number;
  status: 'PENDING' | 'FILLED' | 'PARTIALLY_FILLED' | 'REJECTED' | 'CANCELLED';
  idempotencyKey: string;
  createdOn: Date;
}
