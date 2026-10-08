import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AdminService } from './admin.service';
import { AdminAuditComplianceService, AdminConfigurationService } from '../../../api/generated/admin';

describe('AdminService', () => {
  let service: AdminService;
  let audit: { getCompleteOrderHistory: ReturnType<typeof vi.fn>; getCompleteTransactionHistory: ReturnType<typeof vi.fn> };
  let config: {
    getAllFees: ReturnType<typeof vi.fn>;
    getFeeByAssetType: ReturnType<typeof vi.fn>;
    updateFee: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    audit = {
      getCompleteOrderHistory: vi.fn(() => of([])),
      getCompleteTransactionHistory: vi.fn(() => of([])),
    };
    config = {
      getAllFees: vi.fn(() => of([])),
      getFeeByAssetType: vi.fn(() => of({ assetType: 'EQUITY', flatFee: 1, percentageFee: 0.001 })),
      updateFee: vi.fn(() => of({ assetType: 'EQUITY', flatFee: 7.5, percentageFee: 0.0003 })),
    };
    TestBed.configureTestingModule({
      providers: [
        { provide: AdminAuditComplianceService, useValue: audit },
        { provide: AdminConfigurationService, useValue: config },
      ],
    });
    service = TestBed.inject(AdminService);
  });

  it('passes the date range to the order history audit endpoint', () => {
    service.getCompleteOrderHistory('2026-01-01', '2026-02-01').subscribe();
    expect(audit.getCompleteOrderHistory).toHaveBeenCalledWith('2026-01-01', '2026-02-01');
  });

  it('passes the date range to the transaction history audit endpoint', () => {
    service.getCompleteTransactionHistory(undefined, '2026-02-01').subscribe();
    expect(audit.getCompleteTransactionHistory).toHaveBeenCalledWith(undefined, '2026-02-01');
  });

  it('reads all fee structures', () => {
    service.getAssetFees().subscribe();
    expect(config.getAllFees).toHaveBeenCalled();
  });

  it('reads the fee structure for one asset type', () => {
    let fee: unknown;
    service.getAssetFee('EQUITY').subscribe((f) => (fee = f));
    expect(config.getFeeByAssetType).toHaveBeenCalledWith('EQUITY');
    expect(fee).toEqual({ assetType: 'EQUITY', flatFee: 1, percentageFee: 0.001 });
  });

  it('updates the fee structure for one asset type', () => {
    let fee: unknown;
    service.updateAssetFee('EQUITY', { flatFee: 7.5, percentageFee: 0.0003 }).subscribe((f) => (fee = f));
    expect(config.updateFee).toHaveBeenCalledWith('EQUITY', { flatFee: 7.5, percentageFee: 0.0003 });
    expect(fee).toEqual({ assetType: 'EQUITY', flatFee: 7.5, percentageFee: 0.0003 });
  });
});
