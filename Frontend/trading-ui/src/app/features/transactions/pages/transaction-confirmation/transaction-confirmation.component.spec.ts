import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { TransactionConfirmationComponent } from './transaction-confirmation.component';
import { TransactionsStore } from '../../transactions.store';
import { TransactionService } from '../../services/transaction.service';
import { AccountsService } from '../../../../api/generated/trade';
import { CoreAuthService } from '../../../../core/auth/core-auth.service';
import { TransactionHistoryEntry } from '../../models/transaction.models';

describe('TransactionConfirmationComponent', () => {
  let fixture: ComponentFixture<TransactionConfirmationComponent>;
  let component: TransactionConfirmationComponent;
  let transactions: ReturnType<typeof signal<TransactionHistoryEntry[]>>;
  let service: { getTransaction: ReturnType<typeof vi.fn> };
  let currentUser: ReturnType<typeof vi.fn>;

  const deposit: TransactionHistoryEntry = {
    transactionId: 'tx-dep-1',
    transactionType: 'DEPOSIT',
    actionType: 'CREDIT',
    amount: 2500,
    createdOn: '2026-10-05T10:00:00Z',
  };

  async function create(routeId: string | null): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [TransactionConfirmationComponent],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap(routeId ? { transactionId: routeId } : {}) } } },
        { provide: TransactionsStore, useValue: { transactions } },
        { provide: TransactionService, useValue: service },
        { provide: AccountsService, useValue: { getBalance: () => of({ accountId: 7, cashBalance: 1, currency: 'USD', asOf: '' }) } },
        { provide: CoreAuthService, useValue: { currentUser } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(TransactionConfirmationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  const text = (selector: string) => fixture.nativeElement.querySelector(selector)?.textContent?.trim() ?? '';

  beforeEach(() => {
    transactions = signal<TransactionHistoryEntry[]>([]);
    service = { getTransaction: vi.fn() };
    currentUser = vi.fn(() => ({ accountId: 7 }));
  });

  it('shows the just-completed deposit from the store without calling the API', async () => {
    transactions.set([deposit]);

    await create('tx-dep-1');

    expect(service.getTransaction).not.toHaveBeenCalled();
    expect(text('.hero-title')).toBe('Deposit completed');
    expect(text('.hero-message')).toBe('2,500.00 credited to your account.');
    expect(text('[data-testid="transaction-amount"]')).toContain('+2,500.00');
    expect(text('[data-testid="transaction-direction"]')).toBe('CREDIT');
    expect(text('[data-testid="transaction-id"]')).toBe('tx-dep-1');
    expect(fixture.nativeElement.textContent).toContain('2026');
  });

  it('shows a withdrawal as a debit', async () => {
    transactions.set([{ ...deposit, transactionId: 'tx-wd-1', transactionType: 'WITHDRAW', actionType: 'DEBIT', amount: 40 }]);

    await create('tx-wd-1');

    expect(component.isCredit()).toBe(false);
    expect(text('.hero-title')).toBe('Withdrawal completed');
    expect(text('.hero-message')).toBe('40.00 debited from your account.');
    expect(text('[data-testid="transaction-amount"]')).toContain('−40.00');
    expect(fixture.nativeElement.querySelector('.pill').classList).toContain('action-debit');
  });

  it('links to Transaction History and back to a new transaction', async () => {
    transactions.set([deposit]);

    await create('tx-dep-1');

    expect(fixture.nativeElement.querySelector('[data-testid="view-history-btn"]').getAttribute('href')).toBe('/transaction-history');
    expect(fixture.nativeElement.querySelector('[data-testid="new-transaction-btn"]').getAttribute('href')).toBe('/transactions');
  });

  it('fetches the transaction by id after a reload', async () => {
    service.getTransaction.mockReturnValue(of({ ...deposit, accountId: 7, orderId: null }));

    await create('tx-dep-1');

    expect(service.getTransaction).toHaveBeenCalledWith(7, 'tx-dep-1');
    expect(component.loading()).toBe(false);
    expect(text('.hero-title')).toBe('Deposit completed');
  });

  it('shows a not-found state with a history link when the lookup fails', async () => {
    service.getTransaction.mockReturnValue(throwError(() => ({ status: 404 })));

    await create('tx-missing');

    expect(component.notFound()).toBe(true);
    expect(text('[data-testid="transaction-not-found"]')).toContain("We couldn't find this transaction");
    expect(fixture.nativeElement.querySelector('[data-testid="view-history-btn"]').getAttribute('href')).toBe('/transaction-history');
  });

  it('does not call the API without an id or a signed-in account', async () => {
    currentUser.mockReturnValue(null);

    await create('tx-dep-1');

    expect(service.getTransaction).not.toHaveBeenCalled();
    expect(component.notFound()).toBe(true);
  });

  it('titles other transaction types and formats dates defensively', async () => {
    transactions.set([deposit]);
    await create('tx-dep-1');

    component.transaction.set({ ...deposit, transactionType: 'FEE', actionType: 'DEBIT' });
    expect(component.title()).toBe('Transaction completed');
    component.transaction.set(null);
    expect(component.summary()).toBe('');
    expect(component.formatDate(null)).toBe('');
    expect(component.formatDate('not-a-date')).toBe('not-a-date');
  });
});
