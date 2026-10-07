import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { AdminFeeManagementComponent } from './admin-fee-management.component';
import { AdminService } from '../../services/admin.service';

describe('AdminFeeManagementComponent', () => {
  let fixture: ComponentFixture<AdminFeeManagementComponent>;
  let component: AdminFeeManagementComponent;
  let admin: { getAssetFees: ReturnType<typeof vi.fn> };

  const fees = [
    { assetType: 'EQUITY', flatFee: 4.95, percentageFee: 0.001 },
    { assetType: 'FX', flatFee: 2.5, percentageFee: 0.0005 },
    { assetType: 'CRYPTO', flatFee: 1, percentageFee: 0.0025 },
  ];

  async function create(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [AdminFeeManagementComponent],
      providers: [{ provide: AdminService, useValue: admin }],
    }).compileComponents();
    fixture = TestBed.createComponent(AdminFeeManagementComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(() => {
    admin = { getAssetFees: vi.fn(() => of(fees)) };
  });

  it('loads and renders the fee schedule', async () => {
    await create();

    expect(admin.getAssetFees).toHaveBeenCalled();
    expect(component.isLoading()).toBe(false);
    const rows = fixture.nativeElement.querySelectorAll('.fee-table tbody tr');
    expect(rows.length).toBe(3);
    expect(rows[0].textContent).toContain('Equity');
    expect(rows[0].textContent).toContain('$4.95');
    expect(rows[0].textContent).toContain('0.10%');
    expect(fixture.nativeElement.querySelector('.count-chip').textContent.trim()).toBe('3');
  });

  it('shows the empty state when no fees are configured', async () => {
    admin.getAssetFees.mockReturnValue(of([]));
    await create();

    expect(fixture.nativeElement.querySelector('.empty-state').textContent).toContain('No fees found');
  });

  it('shows an error when loading fails', async () => {
    admin.getAssetFees.mockReturnValue(throwError(() => new Error('down')));
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});

    await create();

    expect(component.errorMessage()).toBe('Failed to load fees. Please try again.');
    expect(component.isLoading()).toBe(false);
    expect(fixture.nativeElement.querySelector('.alert-error')).not.toBeNull();
    consoleError.mockRestore();
  });

  it('formats amounts and asset class names', async () => {
    await create();

    expect(component.formatCurrency(4.95)).toBe('$4.95');
    expect(component.formatCurrency(0.000125)).toBe('$0.000125');
    expect(component.formatAssetClass('EQUITY')).toBe('Equity');
    expect(component.formatAssetClass('fx')).toBe('Fx');
  });

  it('maps each asset class to an icon, including the brand bitcoin icon', async () => {
    await create();

    expect(component.getAssetIcon('equity')).toBe('fas fa-chart-pie');
    expect(component.getAssetIcon('FX')).toBe('fas fa-money-bill');
    expect(component.getAssetIcon('CRYPTO')).toBe('fab fa-bitcoin');
    expect(component.getAssetIcon('BOND')).toBe('fas fa-coins');
  });
});
