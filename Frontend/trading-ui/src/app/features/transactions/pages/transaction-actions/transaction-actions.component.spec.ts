import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { TransactionActionsComponent } from './transaction-actions.component';
import { TransactionService } from '../../services/transaction.service';
import { CoreAuthService } from '../../../../core/auth/core-auth.service';
import { TransactionResponse } from '../../models/transaction.models';

describe('TransactionActionsComponent', () => {
  let fixture: ComponentFixture<TransactionActionsComponent>;
  let component: TransactionActionsComponent;
  let service: { deposit: ReturnType<typeof vi.fn>; withdraw: ReturnType<typeof vi.fn>; getTransactionHistory: ReturnType<typeof vi.fn> };
  let currentUser: ReturnType<typeof vi.fn>;

  const response = (overrides: Partial<TransactionResponse> = {}): TransactionResponse => ({
    transactionId: 'tx-9',
    accountId: 7,
    transactionType: 'DEPOSIT',
    actionType: 'CREDIT',
    amount: 250,
    createdOn: '2026-10-05T10:00:00Z',
    ...overrides,
  });

  beforeEach(async () => {
    vi.useFakeTimers();
    service = {
      deposit: vi.fn(() => of(response())),
      withdraw: vi.fn(() => of(response({ transactionType: 'WITHDRAW', actionType: 'DEBIT', amount: 40 }))),
      getTransactionHistory: vi.fn(() => of([])),
    };
    currentUser = vi.fn(() => ({ accountId: 7 }));

    await TestBed.configureTestingModule({
      imports: [TransactionActionsComponent],
      providers: [
        provideRouter([]),
        { provide: TransactionService, useValue: service },
        { provide: CoreAuthService, useValue: { currentUser } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TransactionActionsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('defaults to a credit (deposit) and exposes the account id', () => {
    expect(component.transactionType()).toBe('deposit');
    expect(component.accountId()).toBe(7);
    expect(component.getTransactionTitle()).toBe('Credit Account');
    expect(component.getTransactionDescription()).toContain('Add funds');
    expect(component.getActionLabel()).toBe('Credit');
  });

  it('switches to debit via the segmented control and ignores unknown values', () => {
    const debit = fixture.nativeElement.querySelector('.segment.debit');
    debit.click();
    fixture.detectChanges();

    expect(component.transactionType()).toBe('withdraw');
    expect(component.getTransactionTitle()).toBe('Debit Account');
    expect(component.getTransactionDescription()).toContain('Move funds out');
    expect(component.getActionLabel()).toBe('Debit');

    component.onTransactionTypeChange('bogus');
    expect(component.transactionType()).toBe('withdraw');
  });

  it('does nothing when the amount is missing, zero or negative', () => {
    component.amount.set(null);
    component.onSubmit();
    component.amount.set(0);
    component.onSubmit();
    component.amount.set(-5);
    component.onSubmit();

    expect(service.deposit).not.toHaveBeenCalled();
    expect(service.withdraw).not.toHaveBeenCalled();
  });

  it('does nothing without a signed-in account', () => {
    currentUser.mockReturnValue(null);
    component.amount.set(10);

    component.onSubmit();

    expect(service.deposit).not.toHaveBeenCalled();
  });

  it('deposits, clears the amount and shows the success message for 3.5s', () => {
    component.amount.set(250);

    component.onSubmit();
    fixture.detectChanges();

    expect(service.deposit).toHaveBeenCalledWith(7, 250);
    expect(component.amount()).toBeNull();
    expect(component.lastSubmittedAmount()).toBe(250);
    expect(component.showSuccess()).toBe(true);
    expect(component.getSuccessMessage()).toBe('250.00 credited successfully.');
    expect(component.getLatestSuccessfulAmountLabel()).toBe('250.00');
    expect(fixture.nativeElement.querySelector('[data-testid="transaction-complete-message"]')).not.toBeNull();

    vi.advanceTimersByTime(3500);
    fixture.detectChanges();

    expect(component.showSuccess()).toBe(false);
  });

  it('withdraws when debit is selected', () => {
    component.onTransactionTypeChange('withdraw');
    component.amount.set(40);

    component.onSubmit();
    fixture.detectChanges();

    expect(service.withdraw).toHaveBeenCalledWith(7, 40);
    expect(component.getSuccessMessage()).toBe('40.00 debited successfully.');
    expect(component.getLatestSuccessfulTransaction()?.transactionType).toBe('WITHDRAW');
  });

  it('shows the rejection with the attempted action and amount, then dismisses it', () => {
    service.withdraw.mockReturnValue(
      throwError(() => ({ error: { errorCode: 'TRANS-400', message: 'Insufficient funds' } }))
    );
    component.onTransactionTypeChange('withdraw');
    component.amount.set(1000);

    component.onSubmit();
    fixture.detectChanges();

    const rejected = fixture.nativeElement.querySelector('[data-testid="transaction-rejected-message"]');
    expect(rejected.textContent).toContain('TRANS-400');
    expect(rejected.textContent).toContain('Insufficient funds');
    expect(component.getLastAttemptedActionLabel()).toBe('Withdrawal');
    expect(component.getLastAttemptedAmountLabel()).toBe('1,000.00');
    expect(component.showSuccess()).toBe(false);

    component.dismissError();
    fixture.detectChanges();

    expect(component.error()).toBeNull();
  });

  it('falls back to generic labels before anything is submitted', () => {
    expect(component.getLastAttemptedActionLabel()).toBe('Deposit');
    expect(component.getLastAttemptedAmountLabel()).toBe('N/A');
    expect(component.getLatestSuccessfulTransaction()).toBeNull();
    expect(component.getLatestSuccessfulAmountLabel()).toBe('N/A');
    expect(component.getSuccessMessage()).toBe('');
  });

  it('formats the created-on label for ISO, epoch and invalid input', () => {
    expect(component.getCreatedOnLabel(null)).toBe('N/A');
    expect(component.getCreatedOnLabel(undefined)).toBe('N/A');
    expect(component.getCreatedOnLabel('  ')).toBe('N/A');
    expect(component.getCreatedOnLabel('not-a-date')).toBe('not-a-date');
    expect(component.getCreatedOnLabel('2026-10-05T10:00:00Z')).toContain('2026');
    expect(component.getCreatedOnLabel(1790000000)).toBe(component.getCreatedOnLabel(1790000000000));
  });

  it('clears the pending success timer on destroy', () => {
    component.amount.set(250);
    component.onSubmit();
    fixture.detectChanges();
    expect(component.showSuccess()).toBe(true);

    fixture.destroy();
    vi.advanceTimersByTime(5000);

    expect(component.showSuccess()).toBe(true);
  });
});
