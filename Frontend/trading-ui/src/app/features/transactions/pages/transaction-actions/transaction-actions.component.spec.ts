import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { TransactionActionsComponent } from './transaction-actions.component';
import { TransactionService } from '../../services/transaction.service';
import { TransactionsStore } from '../../transactions.store';
import { CoreAuthService } from '../../../../core/auth/core-auth.service';
import { TransactionResponse } from '../../models/transaction.models';

describe('TransactionActionsComponent', () => {
  let fixture: ComponentFixture<TransactionActionsComponent>;
  let component: TransactionActionsComponent;
  let service: { deposit: ReturnType<typeof vi.fn>; withdraw: ReturnType<typeof vi.fn>; getTransactionHistory: ReturnType<typeof vi.fn> };
  let currentUser: ReturnType<typeof vi.fn>;
  let navigate: ReturnType<typeof vi.fn>;

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
    await create();
  });

  it('defaults to a credit (deposit) and exposes the account id', () => {
    expect(component.transactionType()).toBe('deposit');
    expect(component.accountId()).toBe(7);
    expect(component.getTransactionTitle()).toBe('Credit Account');
    expect(component.getTransactionDescription()).toContain('Add funds');
    expect(component.getActionLabel()).toBe('Credit');
  });

  it('switches to debit via the segmented control and ignores unknown values', () => {
    fixture.nativeElement.querySelector('.segment.debit').click();
    fixture.detectChanges();

    expect(component.transactionType()).toBe('withdraw');
    expect(component.getTransactionTitle()).toBe('Debit Account');
    expect(component.getTransactionDescription()).toContain('Move funds out');
    expect(component.getActionLabel()).toBe('Debit');

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
});
