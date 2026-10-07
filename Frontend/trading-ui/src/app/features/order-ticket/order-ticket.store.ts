import { Injectable, computed, effect, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { AccountsService } from '../../api/generated/trade';
import { CoreAuthService } from '../../core/auth/core-auth.service';
import {
  normalizeOrderTicketError,
  OrderTicketResponse,
  OrderTicketSubmitInput,
} from './order-ticket.models';
import { OrderTicketService } from './services/order-ticket.service';

/**
 * Order ticket state store
 * Manages order ticket form state with Angular signals
 */
@Injectable({
  providedIn: 'root',
})
export class OrderTicketStore {
  private readonly isSubmittingSignal = signal<boolean>(false);
  private readonly submissionResultSignal = signal<OrderTicketResponse | null>(null);
  private readonly errorCodeSignal = signal<string | null>(null);
  private readonly errorMessageSignal = signal<string | null>(null);
  private readonly accountDisplayIdSignal = signal<string | null>(null);
  private readonly accountCurrencySignal = signal<string | null>(null);
  private accountLookupSubscription: Subscription | null = null;
  private lastFetchedAccountId: number | null = null;

  isSubmitting = computed(() => this.isSubmittingSignal());
  submissionResult = computed(() => this.submissionResultSignal());
  errorCode = computed(() => this.errorCodeSignal());
  errorMessage = computed(() => this.errorMessageSignal());
  accountDisplayId = computed(() => this.accountDisplayIdSignal());
  /** The account's currency: cash is debited in it, at the FX rate fetched when the order fills */
  accountCurrency = computed(() => this.accountCurrencySignal());
  accountId = computed(() => {
    const currentUser = this.authService.currentUser();
    if (currentUser) {
      return currentUser.accountId;
    }

    const accessToken = this.authService.accessToken();
    if (!accessToken) {
      return null;
    }

    try {
      return this.authService.decodeJWT(accessToken).accountId;
    } catch {
      return null;
    }
  });

  constructor(
    private orderTicketService: OrderTicketService,
    private authService: CoreAuthService,
    private accountsService: AccountsService
  ) {
    effect(() => {
      const accountId = this.accountId();

      if (accountId === null) {
        this.accountDisplayIdSignal.set(null);
        this.lastFetchedAccountId = null;
        this.accountLookupSubscription?.unsubscribe();
        this.accountLookupSubscription = null;
        return;
      }

      if (this.lastFetchedAccountId === accountId && this.accountDisplayIdSignal()) {
        return;
      }

      this.lastFetchedAccountId = accountId;
      this.accountLookupSubscription?.unsubscribe();
      this.accountLookupSubscription = this.accountsService.getAccount(accountId).subscribe({
        next: (account) => {
          this.accountDisplayIdSignal.set(account.accountId || null);
        },
        error: () => {
          this.accountDisplayIdSignal.set(null);
        },
      });
      // The account's currency comes from the balance (the account record does not carry one)
      this.accountsService.getBalance(accountId).subscribe({
        next: (balance) => this.accountCurrencySignal.set(balance.currency || null),
        error: () => this.accountCurrencySignal.set(null),
      });
    });
  }

  /**
   * Clear error state (used when retrying or dismissing errors)
   */
  clearErrors(): void {
    this.errorCodeSignal.set(null);
    this.errorMessageSignal.set(null);
  }

  /**
   * Clear successful submission result (used by UI auto-dismiss)
   */
  clearSubmissionResult(): void {
    this.submissionResultSignal.set(null);
  }

  submitOrder(input: OrderTicketSubmitInput): Promise<boolean> {
    const accountId = this.accountId();
    if (accountId === null) {
      this.submissionResultSignal.set(null);
      this.errorCodeSignal.set('AUTH-401');
      this.errorMessageSignal.set('The session has expired or the sign-in was refused.');
      return Promise.resolve(false);
    }

    this.isSubmittingSignal.set(true);
    this.submissionResultSignal.set(null);
    this.errorCodeSignal.set(null);
    this.errorMessageSignal.set(null);

    return this.orderTicketService
      .placeOrder(accountId, input)
      .toPromise()
      .then((response) => {
        this.submissionResultSignal.set(response ?? null);
        return true;
      })
      .catch((error) => {
        const mapped = normalizeOrderTicketError(error);
        this.submissionResultSignal.set(null);
        this.errorCodeSignal.set(mapped.errorCode);
        this.errorMessageSignal.set(mapped.message);
        return false;
      })
      .finally(() => {
        this.isSubmittingSignal.set(false);
      });
  }
}
