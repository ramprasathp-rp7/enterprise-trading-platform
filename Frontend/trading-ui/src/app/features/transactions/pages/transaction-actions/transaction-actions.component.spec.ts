import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { TransactionActionsComponent } from './transaction-actions.component';
import { TransactionService } from '../../services/transaction.service';
import { TransactionsStore } from '../../transactions.store';
import { CoreAuthService } from '../../../../core/auth/core-auth.service';
import { TransactionResponse } from '../../models/transaction.models';
import { AccountsService } from '../../../../api/generated/trade';

describe('TransactionActionsComponent', () => {
  let fixture: ComponentFixture<TransactionActionsComponent>;
  let component: TransactionActionsComponent;
  let service: { deposit: ReturnType<typeof vi.fn>; withdraw: ReturnType<typeof vi.fn>; getTransactionHistory: ReturnType<typeof vi.fn> };
  let currentUser: ReturnType<typeof vi.fn>;
  let navigate: ReturnType<typeof vi.fn>;
  let accounts: { getBalance: ReturnType<typeof vi.fn>; getAccount: ReturnType<typeof vi.fn> };

  const response = (overrides: Partial<TransactionResponse> = {}): TransactionResponse => ({
    transactionId: 'tx-9',
    accountId: 7,
    transactionType: 'DEPOSIT',
    actionType: 'CREDIT',
    amount: 250,
    createdOn: '2026-10-05T10:00:00Z',
    ...overrides,
  });

  async function create(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [TransactionActionsComponent],
      providers: [
        provideRouter([]),
        { provide: TransactionService, useValue: service },
        { provide: CoreAuthService, useValue: { currentUser } },
        { provide: AccountsService, useValue: accounts },
      ],
    }).compileComponents();

    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true) as any;
    fixture = TestBed.createComponent(TransactionActionsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(async () => {
    service = {
      deposit: vi.fn(() => of(response())),
      withdraw: vi.fn(() => of(response({ transactionId: 'tx-10', transactionType: 'WITHDRAW', actionType: 'DEBIT', amount: 40 }))),
      getTransactionHistory: vi.fn(() => of([])),
    };
    currentUser = vi.fn(() => ({ accountId: 7 }));
    accounts = {
      getBalance: vi.fn(() => of({ accountId: 7, cashBalance: 1000, currency: 'USD', asOf: '' })),
      getAccount: vi.fn(() => of({ id: 7, accountId: 'ACC-000007', holderName: 'R', cashBalance: 1000, status: 'ACTIVE', version: 1, lastUpdated: '' })),
    };
    await create();
  });

  it('defaults to a credit (deposit) and exposes the account id', () => {
    expect(component.transactionType()).toBe('deposit');
    expect(component.accountId()).toBe(7);
    expect(component.getTransactionTitle()).toBe('Deposit funds');
    expect(component.getTransactionDescription()).toContain('Add funds');
    expect(component.getActionLabel()).toBe('Deposit');
  });

  it('switches to debit via the segmented control and ignores unknown values', () => {
    fixture.nativeElement.querySelector('.segment.debit').click();
    fixture.detectChanges();

    expect(component.transactionType()).toBe('withdraw');
    expect(component.getTransactionTitle()).toBe('Withdraw funds');
    expect(component.getTransactionDescription()).toContain('Move funds out');
    expect(component.getActionLabel()).toBe('Withdraw');

    component.onTransactionTypeChange('bogus');
    expect(component.transactionType()).toBe('withdraw');
  });

  it('does nothing when the amount is missing, zero or negative', () => {
    for (const value of [null, 0, -5]) {
      component.amount.set(value);
      component.onSubmit();
    }
    fixture.detectChanges();

    expect(service.deposit).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('does nothing without a signed-in account', () => {
    currentUser.mockReturnValue(null);
    component.amount.set(10);

    component.onSubmit();

    expect(service.deposit).not.toHaveBeenCalled();
  });

  it('deposits and opens the confirmation page for the new transaction', () => {
    component.amount.set(250);

    component.onSubmit();
    fixture.detectChanges();

    expect(service.deposit).toHaveBeenCalledWith(7, 250);
    expect(component.amount()).toBeNull();
    expect(component.lastSubmittedAmount()).toBe(250);
    expect(navigate).toHaveBeenCalledWith(['/transaction-confirmation', 'tx-9']);
  });

  it('rounds a typed amount to cents before sending, as the API takes at most two decimals', () => {
    component.amount.set(10.555);

    component.onSubmit();

    expect(service.deposit).toHaveBeenCalledWith(7, 10.56);
    expect(component.lastSubmittedAmount()).toBe(10.56);
  });

  it('rounds half up on the decimal value, not the binary float', () => {
    component.amount.set(1.005);

    component.onSubmit();

    expect(service.deposit).toHaveBeenCalledWith(7, 1.01);
  });

  it('does nothing when the amount rounds to zero cents', () => {
    component.amount.set(0.004);

    component.onSubmit();

    expect(service.deposit).not.toHaveBeenCalled();
  });

  it('withdraws when debit is selected and opens its confirmation', () => {
    component.onTransactionTypeChange('withdraw');
    component.amount.set(40);

    component.onSubmit();
    fixture.detectChanges();

    expect(service.withdraw).toHaveBeenCalledWith(7, 40);
    expect(navigate).toHaveBeenCalledWith(['/transaction-confirmation', 'tx-10']);
  });

  it('stays on the page and shows the rejection with the attempted action and amount', () => {
    service.withdraw.mockReturnValue(
      throwError(() => ({ error: { errorCode: 'TRANS-400', message: 'Insufficient funds' } }))
    );
    component.onTransactionTypeChange('withdraw');
    component.amount.set(1000);

    component.onSubmit();
    fixture.detectChanges();

    expect(navigate).not.toHaveBeenCalled();
    const rejected = fixture.nativeElement.querySelector('[data-testid="transaction-rejected-message"]');
    expect(rejected.textContent).toContain('TRANS-400');
    expect(rejected.textContent).toContain('Insufficient funds');
    expect(component.getLastAttemptedActionLabel()).toBe('Withdrawal');
    expect(component.getLastAttemptedAmountLabel()).toBe('1,000.00');

    component.dismissError();
    fixture.detectChanges();
    expect(component.error()).toBeNull();
  });

  it('does not redirect on arrival when an earlier transaction already succeeded', async () => {
    // A deposit completed on a previous visit leaves the app-wide store in a success state
    TestBed.inject(TransactionsStore).deposit(7, 250);
    navigate.mockClear();

    fixture.destroy();
    fixture = TestBed.createComponent(TransactionActionsComponent);
    fixture.detectChanges();

    expect(navigate).not.toHaveBeenCalled();
  });

  it('falls back to generic labels before anything is submitted', () => {
    expect(component.getLastAttemptedActionLabel()).toBe('Deposit');
    expect(component.getLastAttemptedAmountLabel()).toBe('N/A');
  });

  it('no longer renders a success card on the page itself', () => {
    component.amount.set(250);
    component.onSubmit();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="transaction-complete-message"]')).toBeNull();
  });

  describe('balance, quick amounts and recent activity', () => {
    const q = (sel: string) => fixture.nativeElement.querySelector(sel);

    it('shows the cash balance and the customer-facing account number', () => {
      expect(accounts.getBalance).toHaveBeenCalledWith(7);
      expect(q('[data-testid="balance-value"]').textContent).toContain('1,000.00');
      expect(q('[data-testid="balance-card"]').textContent).toContain('ACC-000007');
    });

    it('projects the balance after a deposit or withdrawal', () => {
      component.amount.set(250);
      fixture.detectChanges();
      expect(component.balanceAfter()).toBe(1250);
      expect(q('[data-testid="balance-after"]').textContent).toContain('1,250.00');

      component.onTransactionTypeChange('withdraw');
      fixture.detectChanges();
      expect(component.balanceAfter()).toBe(750);
    });

    it('warns (without blocking) when a withdrawal is more than the available cash', () => {
      component.onTransactionTypeChange('withdraw');
      component.amount.set(1500);
      fixture.detectChanges();

      expect(component.exceedsBalance()).toBe(true);
      expect(q('[data-testid="exceeds-balance"]').textContent).toContain('1,000.00');
      expect(q('[data-testid="submit-transaction-btn"]').disabled).toBe(false);

      component.onTransactionTypeChange('deposit');
      fixture.detectChanges();
      expect(q('[data-testid="exceeds-balance"]')).toBeNull();
    });

    it('adds quick amounts and fills the full balance with Max', () => {
      const chips = (): HTMLButtonElement[] => Array.from(fixture.nativeElement.querySelectorAll('.quick-amounts .chip'));
      chips()[0].click();
      chips()[2].click();
      expect(component.amount()).toBe(1100);

      expect(q('[data-testid="use-full-balance"]')).toBeNull();
      component.onTransactionTypeChange('withdraw');
      fixture.detectChanges();
      q('[data-testid="use-full-balance"]').click();
      expect(component.amount()).toBe(1000);
    });

    it('lists the latest transactions, newest first, capped at four', async () => {
      const rows = [1, 2, 3, 4, 5].map((i) => ({
        transactionId: `t${i}`, transactionType: i === 2 ? 'FEE' : 'DEPOSIT', actionType: i === 2 ? 'DEBIT' : 'CREDIT', amount: i * 10, createdOn: '2026-10-05T09:00:00Z',
      }));
      service.getTransactionHistory.mockReturnValue(of(rows));
      fixture.destroy();
      fixture = TestBed.createComponent(TransactionActionsComponent);
      fixture.detectChanges();

      const items = fixture.nativeElement.querySelectorAll('[data-testid="recent-transaction"]');
      expect(items.length).toBe(4);
      expect(items[0].textContent).toContain('+10.00');
      expect(items[1].textContent).toContain('Fee');
      expect(items[1].textContent).toContain('−20.00');
    });

    it('shows a placeholder when the balance or history cannot be loaded', () => {
      accounts.getBalance.mockReturnValue(throwError(() => new Error('down')));
      accounts.getAccount.mockReturnValue(throwError(() => new Error('down')));
      service.getTransactionHistory.mockReturnValue(throwError(() => new Error('down')));
      fixture.destroy();
      fixture = TestBed.createComponent(TransactionActionsComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      expect(component.balance()).toBeNull();
      expect(component.balanceAfter()).toBeNull();
      expect(q('[data-testid="balance-value"]').textContent).toContain('—');
      expect(q('[data-testid="balance-card"]').textContent).toContain('Account 7');
      expect(q('.recent-empty')).not.toBeNull();
    });

    it('formats dates safely and picks an icon per type', () => {
      expect(component.shortDate('2026-10-05T09:00:00Z')).toMatch(/^Oct 5, \d{1,2}:\d{2} [AP]M$/);
      expect(component.shortDate(1759654800)).toMatch(/^[A-Z][a-z]{2} \d{1,2}, /);
      expect(component.shortDate('not-a-date')).toBe('');
      expect(component.shortDate(null)).toBe('');
      expect(['DEPOSIT', 'WITHDRAW', 'FEE', 'OTHER'].map((t) => component.transactionIcon(t))).toEqual([
        'fa-arrow-down', 'fa-arrow-up', 'fa-receipt', 'fa-right-left',
      ]);
    });
  });
});
