import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { of, throwError } from 'rxjs';
import { TransactionsComponent } from './transactions.component';
import { TransactionService } from '../../services/transaction.service';
import { CoreAuthService } from '../../../../core/auth/core-auth.service';
import { StatementPdfService } from '../../../../shared/statement-pdf.service';
import { AccountsService } from '../../../../api/generated/trade';
import { TransactionHistoryEntry } from '../../models/transaction.models';

describe('TransactionsComponent', () => {
  let fixture: ComponentFixture<TransactionsComponent>;
  let component: TransactionsComponent;
  let service: { getTransactionHistory: ReturnType<typeof vi.fn> };
  let currentUser: ReturnType<typeof vi.fn>;
  let statementPdf: { download: ReturnType<typeof vi.fn> };
  let accounts: { getAccount: ReturnType<typeof vi.fn>; getBalance: ReturnType<typeof vi.fn> };

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
        { provide: StatementPdfService, useValue: statementPdf },
        { provide: AccountsService, useValue: accounts },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(TransactionsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(() => {
    service = { getTransactionHistory: vi.fn(() => of(history)) };
    currentUser = vi.fn(() => ({ accountId: 7, username: 'rkumar' }));
    statementPdf = { download: vi.fn().mockResolvedValue(undefined) };
    accounts = {
      getAccount: vi.fn(() => of({ id: 7, accountId: 'ACC-000007', holderName: 'Rahul Kumar', cashBalance: 1, status: 'ACTIVE', version: 1, lastUpdated: '' })),
      getBalance: vi.fn(() => of({ accountId: 7, cashBalance: 1, currency: 'USD', asOf: '' })),
    };
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

    it("formats amounts in the account's currency with two decimals", () => {
      expect(component.formatCurrency(1234.5)).toBe('$1,234.50');
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

  describe('transaction statement download', () => {
    it('downloads all transactions with credit/debit totals when no filter is set', async () => {
      await create();

      const notify = vi.spyOn(TestBed.inject(NotificationService), 'notify');
      fixture.nativeElement.querySelector('[data-testid="download-statement-btn"]').click();
      await fixture.whenStable();

      const def = statementPdf.download.mock.calls[0][0];
      expect(def.title).toBe('Transaction Statement');
      expect(accounts.getAccount).toHaveBeenCalledWith(7);
      expect(def.details).toContainEqual(['Account', 'ACC-000007']);
      expect(def.details).toContainEqual(['Filters', 'None (all transactions)']);
      expect(def.summary).toEqual([
        ['Transactions', '3'],
        ['Total credits', '$1,500.00'],
        ['Total debits', '$204.95'],
        ['Net', '$1,295.05'],
      ]);
      expect(def.rows.length).toBe(3);
      expect(def.rows[0].slice(1)).toEqual(['DEPOSIT', 'CREDIT', '$1,500.00', 'abc-111']);
      expect(def.fileName).toMatch(/^transaction-statement-ACC-000007-\d{4}-\d{2}-\d{2}$/);
      expect(notify).toHaveBeenCalledWith({
        kind: 'report',
        title: 'Report exported',
        message: `Transaction statement downloaded as ${def.fileName}.pdf.`,
        link: { path: '/transaction-history' },
      });
    });

    it('applies the on-screen filters to the statement', async () => {
      await create();
      component.selectedTypeFilter.set('FEE');
      component.transactionIdQuery.set(' abc ');

      await component.downloadStatement();

      const def = statementPdf.download.mock.calls[0][0];
      expect(def.details).toContainEqual(['Filters', 'Type FEE, ID contains "abc"']);
      expect(def.rows.map((r: string[]) => r[4])).toEqual(['abc-333']);
      expect(def.summary[0]).toEqual(['Transactions', '1']);
    });

    it('shows an error if the statement cannot be generated', async () => {
      statementPdf.download.mockRejectedValue(new Error('boom'));
      const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});
      await create();
      const notify = vi.spyOn(TestBed.inject(NotificationService), 'notify');

      await component.downloadStatement();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('[data-testid="download-error"]')).not.toBeNull();
      expect(component.downloading()).toBe(false);
      expect(notify).not.toHaveBeenCalled();
      consoleError.mockRestore();
    });

    it('does not download when the customer account number cannot be loaded', async () => {
      await create();
      accounts.getAccount.mockReturnValue(of({ id: 7, accountId: '' }));
      const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});

      await component.downloadStatement();

      expect(statementPdf.download).not.toHaveBeenCalled();
      expect(component.downloadError()).not.toBeNull();
      consoleError.mockRestore();
    });
  });
});
