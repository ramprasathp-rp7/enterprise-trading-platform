import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { TransactionsComponent } from './transactions.component';
import { TransactionService } from '../../services/transaction.service';
import { CoreAuthService } from '../../../../core/auth/core-auth.service';
import { TransactionHistoryEntry } from '../../models/transaction.models';

describe('TransactionsComponent', () => {
  let fixture: ComponentFixture<TransactionsComponent>;
  let component: TransactionsComponent;
  let service: { getTransactionHistory: ReturnType<typeof vi.fn> };
  let currentUser: ReturnType<typeof vi.fn>;

  const history: TransactionHistoryEntry[] = [
    { transactionId: 'abc-111', transactionType: 'DEPOSIT', actionType: 'CREDIT', amount: 1500, createdOn: '2026-10-01T10:00:00Z' },
    { transactionId: 'def-222', transactionType: 'WITHDRAW', actionType: 'DEBIT', amount: 200, createdOn: '2026-10-02T10:00:00Z' },
    { transactionId: 'abc-333', transactionType: 'FEE', actionType: 'DEBIT', amount: 4.95, createdOn: '2026-10-03T10:00:00Z' },
  ];

  async function create(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [TransactionsComponent],
      providers: [
        provideRouter([]),
        { provide: TransactionService, useValue: service },
        { provide: CoreAuthService, useValue: { currentUser } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(TransactionsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(() => {
    service = { getTransactionHistory: vi.fn(() => of(history)) };
    currentUser = vi.fn(() => ({ accountId: 7 }));
  });

  it('loads history for the signed-in account and renders a row per transaction', async () => {
    await create();

    expect(service.getTransactionHistory).toHaveBeenCalledWith(7, undefined);
    expect(fixture.nativeElement.querySelectorAll('tbody tr.transaction-row').length).toBe(3);
    expect(fixture.nativeElement.querySelector('.count-chip').textContent.trim()).toBe('3');
  });

  it('does not load anything without an account', async () => {
    currentUser.mockReturnValue(null);
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});

    await create();

    expect(service.getTransactionHistory).not.toHaveBeenCalled();
    expect(consoleError).toHaveBeenCalled();
    consoleError.mockRestore();
  });

  it('shows the empty state when there are no transactions', async () => {
    service.getTransactionHistory.mockReturnValue(of([]));

    await create();

    expect(fixture.nativeElement.querySelector('.empty-state').textContent).toContain('No transactions found');
  });

  it('filters by type and by partial id (case-insensitive)', async () => {
    await create();

    component.selectedTypeFilter.set('DEPOSIT');
    expect(component.getFilteredTransactions().map((t) => t.transactionId)).toEqual(['abc-111']);

    component.selectedTypeFilter.set('ALL');
    component.transactionIdQuery.set('  ABC ');
    expect(component.getFilteredTransactions().map((t) => t.transactionId)).toEqual(['abc-111', 'abc-333']);

    component.clearTransactionIdQuery();
    expect(component.transactionIdQuery()).toBe('');
    expect(component.getFilteredTransactions().length).toBe(3);
  });

  it('shows and dismisses a load error', async () => {
    service.getTransactionHistory.mockReturnValue(
      throwError(() => ({ error: { errorCode: 'ACC-404', message: 'Account not found' } }))
    );
    await create();

    const alert = fixture.nativeElement.querySelector('.alert-error');
    expect(alert.textContent).toContain('ACC-404');
    expect(alert.textContent).toContain('Account not found');

    alert.querySelector('.alert-close').click();
    fixture.detectChanges();

    expect(component.error()).toBeNull();
    expect(fixture.nativeElement.querySelector('.alert-error')).toBeNull();
  });

  describe('formatting helpers', () => {
    beforeEach(async () => create());

    it('formats currency with two decimals', () => {
      expect(component.formatCurrency(1234.5)).toBe('1,234.50');
    });

    it('maps each transaction type to an icon', () => {
      expect(component.getTypeIcon('DEPOSIT')).toBe('arrow-down');
      expect(component.getTypeIcon('WITHDRAW')).toBe('arrow-up');
      expect(component.getTypeIcon('FEE')).toBe('percent');
      expect(component.getTypeIcon('BUY')).toBe('shopping-cart');
      expect(component.getTypeIcon('SELL')).toBe('tag');
      expect(component.getTypeIcon('OTHER' as any)).toBe('exchange');
    });

    it('maps the action type to a pill class', () => {
      expect(component.getActionClass('CREDIT')).toBe('action-credit');
      expect(component.getActionClass('DEBIT')).toBe('action-debit');
    });

    it('formats ISO strings, epoch seconds and epoch milliseconds', () => {
      const iso = component.formatDate('2026-10-01T10:00:00Z');
      expect(iso).toContain('2026');
      expect(component.formatDate(1790000000)).toBe(component.formatDate(1790000000000));
      expect(component.formatDate('1790000000')).toContain('2026');
    });

    it('returns empty for missing values and the raw text for unparseable ones', () => {
      expect(component.formatDate(null)).toBe('');
      expect(component.formatDate(undefined)).toBe('');
      expect(component.formatDate('   ')).toBe('');
      expect(component.formatDate('not-a-date')).toBe('not-a-date');
    });
  });
});
