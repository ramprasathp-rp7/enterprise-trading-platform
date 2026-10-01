import { Injectable, computed, signal } from '@angular/core';
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

  isSubmitting = computed(() => this.isSubmittingSignal());
  submissionResult = computed(() => this.submissionResultSignal());
  errorCode = computed(() => this.errorCodeSignal());
  errorMessage = computed(() => this.errorMessageSignal());
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
    private authService: CoreAuthService
  ) {}

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
