export * from './accountHistory.service';
import { AccountHistoryService } from './accountHistory.service';
export * from './adminAccountHistory.service';
import { AdminAccountHistoryService } from './adminAccountHistory.service';
export const APIS = [AccountHistoryService, AdminAccountHistoryService];
