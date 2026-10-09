import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, Injector, computed, effect, inject, signal, untracked } from '@angular/core';
import { Observable, catchError, map, tap, throwError } from 'rxjs';
import {
  AccountSettingsRequest,
  AccountSettingsResponse,
  AccountSettingsService,
  MarketPreference,
} from '../../api/generated/account-settings';
import { CoreAuthService } from '../auth/core-auth.service';
import { storageOwner } from '../notifications/notification.service';

export type Market = MarketPreference;

/** The customer's trading settings (contracts/account-settings-api.yaml) */
export interface AccountSettings {
  /** Most one order should cost, in the account's currency; null = no budget */
  orderBudget: number | null;
  /** Default market for instrument lists and searches */
  market: Market;
  /** Prefilled order quantity; null = none */
  defaultQuantity: number | null;
  /** False while the defaults are shown (nothing stored) */
  configured: boolean;
  updatedAt: string | null;
}

export const DEFAULT_ACCOUNT_SETTINGS: AccountSettings = {
  orderBudget: null,
  market: 'ALL',
  defaultQuantity: null,
  configured: false,
  updatedAt: null,
};

/**
 * The signed-in customer's trading settings: order budget, default market, default quantity.
 * Everything else reads them through the signals here; until they load (and for admins, who have
 * no trading account) the defaults apply. The API client is resolved lazily, so views that only
 * read a setting do not need HTTP.
 */
@Injectable({ providedIn: 'root' })
export class AccountSettingsStore {
  private readonly injector = inject(Injector);
  private readonly authService = inject(CoreAuthService);

  private readonly settingsSignal = signal<AccountSettings>(DEFAULT_ACCOUNT_SETTINGS);
  private readonly loadedSignal = signal(false);
  private readonly loadingSignal = signal(false);
  private readonly errorSignal = signal<string | null>(null);

  readonly settings = this.settingsSignal.asReadonly();
  readonly isLoaded = this.loadedSignal.asReadonly();
  readonly isLoading = this.loadingSignal.asReadonly();
  readonly error = this.errorSignal.asReadonly();
  readonly orderBudget = computed(() => this.settingsSignal().orderBudget);
  readonly market = computed(() => this.settingsSignal().market);
  readonly defaultQuantity = computed(() => this.settingsSignal().defaultQuantity);

  constructor() {
    // Never apply one user's settings to the next user who signs in on this tab
    let previous: string | null | undefined;
    effect(() => {
      const owner = storageOwner(this.authService.currentUser());
      if (previous !== undefined && previous !== owner) {
        untracked(() => this.clear());
      }
      previous = owner;
    });
  }

  private get accountId(): number | null {
    if (this.authService.isAdmin()) {
      return null;
    }
    return this.authService.currentUser()?.accountId ?? null;
  }

  private get api(): AccountSettingsService {
    return this.injector.get(AccountSettingsService);
  }

  /** Loads once per sign-in; later calls do nothing unless forced */
  ensureLoaded(force = false): void {
    if ((this.loadedSignal() && !force) || this.loadingSignal()) {
      return;
    }
    const accountId = this.accountId;
    if (accountId == null) {
      return;
    }
    this.loadingSignal.set(true);
    this.errorSignal.set(null);
    this.api.getAccountSettings(accountId).subscribe({
      next: (response) => {
        this.settingsSignal.set(toSettings(response));
        this.loadedSignal.set(true);
        this.loadingSignal.set(false);
      },
      error: (err) => {
        this.errorSignal.set(messageOf(err, 'Could not load your trading settings.'));
        this.loadingSignal.set(false);
      },
    });
  }

  /** Replaces the stored settings */
  save(input: Pick<AccountSettings, 'orderBudget' | 'market' | 'defaultQuantity'>): Observable<AccountSettings> {
    const accountId = this.accountId;
    if (accountId == null) {
      return throwError(() => new Error('You need to be signed in to change your settings.'));
    }
    const body: AccountSettingsRequest = {
      orderBudget: input.orderBudget,
      market: input.market,
      defaultQuantity: input.defaultQuantity,
    };
    return this.api.updateAccountSettings(accountId, body).pipe(
      map(toSettings),
      tap((saved) => {
        this.settingsSignal.set(saved);
        this.loadedSignal.set(true);
      }),
      catchError((err) => throwError(() => new Error(messageOf(err, 'Something went wrong. Please try again.')))),
    );
  }

  /** Back to the defaults */
  reset(): Observable<void> {
    const accountId = this.accountId;
    if (accountId == null) {
      return throwError(() => new Error('You need to be signed in to change your settings.'));
    }
    return this.api.resetAccountSettings(accountId).pipe(
      tap(() => this.settingsSignal.set(DEFAULT_ACCOUNT_SETTINGS)),
      map(() => undefined),
      catchError((err) => throwError(() => new Error(messageOf(err, 'Something went wrong. Please try again.')))),
    );
  }

  clear(): void {
    this.settingsSignal.set(DEFAULT_ACCOUNT_SETTINGS);
    this.loadedSignal.set(false);
    this.errorSignal.set(null);
  }
}

function toSettings(r: AccountSettingsResponse): AccountSettings {
  return {
    orderBudget: r.orderBudget ?? null,
    market: r.market ?? 'ALL',
    defaultQuantity: r.defaultQuantity ?? null,
    configured: r.configured,
    updatedAt: r.updatedAt ?? null,
  };
}

function messageOf(err: unknown, fallback: string): string {
  if (err instanceof HttpErrorResponse) {
    const body = err.error as { message?: string } | null;
    if (body?.message) {
      return body.message;
    }
    if (err.status === 0) {
      return 'Cannot reach the trading service.';
    }
  }
  return fallback;
}
