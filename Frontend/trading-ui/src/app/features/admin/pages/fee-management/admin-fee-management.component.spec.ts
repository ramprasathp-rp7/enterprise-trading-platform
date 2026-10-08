import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { AdminFeeManagementComponent } from './admin-fee-management.component';
import { AdminService } from '../../services/admin.service';

describe('AdminFeeManagementComponent', () => {
  let fixture: ComponentFixture<AdminFeeManagementComponent>;
  let component: AdminFeeManagementComponent;
  let admin: { getAssetFees: ReturnType<typeof vi.fn>; updateAssetFee: ReturnType<typeof vi.fn> };

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
    admin = {
      getAssetFees: vi.fn(() => of(fees)),
      updateAssetFee: vi.fn((assetType: string, body: { flatFee: number; percentageFee: number }) =>
        of({ assetType, ...body })
      ),
    };
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

  it('formats fine-grained percentages without rounding them away', async () => {
    await create();

    expect(component.formatPercentage(0.001)).toBe('0.10%');
    expect(component.formatPercentage(0.00005)).toBe('0.005%');
  });

  it('edits a row in place and reflects the saved fees in the table', async () => {
    await create();

    const editButtons = fixture.nativeElement.querySelectorAll('.edit-btn');
    editButtons[0].click();
    fixture.detectChanges();

    expect(component.editingAssetType()).toBe('EQUITY');
    expect(component.draft).toEqual({ flatFee: 4.95, percentFee: 0.1 });
    expect(fixture.nativeElement.querySelectorAll('.fee-table tbody tr.editing input').length).toBe(2);

    component.draft = { flatFee: 7.5, percentFee: 0.03 };
    fixture.nativeElement.querySelector('.save-btn').click();
    fixture.detectChanges();

    expect(admin.updateAssetFee).toHaveBeenCalledWith('EQUITY', { flatFee: 7.5, percentageFee: 0.0003 });
    expect(component.editingAssetType()).toBeNull();
    const row = fixture.nativeElement.querySelectorAll('.fee-table tbody tr')[0];
    expect(row.textContent).toContain('$7.50');
    expect(row.textContent).toContain('0.03%');
    expect(component.assetFees()[1]).toEqual(fees[1]);
    expect(fixture.nativeElement.querySelector('.alert-success').textContent).toContain('Equity fees updated');
  });

  it('only lets one row be edited at a time', async () => {
    await create();

    component.startEdit(fees[0]);
    fixture.detectChanges();

    const otherButtons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('.edit-btn'));
    expect(otherButtons.length).toBe(2);
    expect(otherButtons.every((b) => b.disabled)).toBe(true);
  });

  it('cancelling an edit leaves the fees unchanged', async () => {
    await create();

    component.startEdit(fees[1]);
    component.draft = { flatFee: 99, percentFee: 5 };
    component.cancelEdit();
    fixture.detectChanges();

    expect(admin.updateAssetFee).not.toHaveBeenCalled();
    expect(component.assetFees()).toEqual(fees);
    expect(fixture.nativeElement.querySelectorAll('.fee-table tbody tr')[1].textContent).toContain('$2.50');
  });

  it('rejects invalid fees before calling the server', async () => {
    await create();
    component.startEdit(fees[0]);

    const cases: Array<[FeeDraftLike, string]> = [
      [{ flatFee: 0, percentFee: 0 }, 'At least one fee must be greater than zero.'],
      [{ flatFee: -1, percentFee: 0.1 }, 'Fees cannot be negative.'],
      [{ flatFee: 1, percentFee: 150 }, 'Percentage fee cannot exceed 100%.'],
      [{ flatFee: 1.234, percentFee: 0.1 }, 'Flat fee can have at most 2 decimal places.'],
      [{ flatFee: null, percentFee: 0.1 }, 'Enter both a flat fee and a percentage fee.'],
    ];
    for (const [draft, message] of cases) {
      component.draft = draft;
      component.saveEdit();
      expect(component.editError()).toBe(message);
    }
    expect(admin.updateAssetFee).not.toHaveBeenCalled();
    expect(component.editingAssetType()).toBe('EQUITY');
  });

  it('keeps the row in edit mode and shows the error when saving fails', async () => {
    admin.updateAssetFee.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 403, error: { errorCode: 'AUTH-403' } }))
    );
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});
    await create();

    component.startEdit(fees[2]);
    component.saveEdit();
    fixture.detectChanges();

    expect(component.editingAssetType()).toBe('CRYPTO');
    expect(component.isSaving()).toBe(false);
    expect(component.editError()).toBe('You do not have permission to change fees.');
    expect(component.assetFees()).toEqual(fees);
    consoleError.mockRestore();
  });
});

type FeeDraftLike = { flatFee: number | null; percentFee: number | null };
