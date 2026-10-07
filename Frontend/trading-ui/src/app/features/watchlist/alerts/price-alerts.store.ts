import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { Observable, catchError, map, tap, throwError } from 'rxjs';
import {
  AlertDirection,
  AlertMode,
  PriceAlertResponse,
  PriceAlertTriggerResponse,
  PriceAlertsService,
} from '../../../api/generated/watchlist';
import { CoreAuthService } from '../../../core/auth/core-auth.service';

/** Active alerts an account may hold (price-alerts.max-active-per-account on the server) */
export const MAX_ACTIVE_ALERTS = 20;

export interface NewPriceAlert {
  symbol: string;
  direction: AlertDirection;
  threshold: number;
  mode: AlertMode;
}

/**
 * Price alert state shared by the bell button on each watchlist row and the alerts panel
 * on the Watchlists page. All calls go through the generated client (contracts/watchlist-api.yaml).
 */
@Injectable({ providedIn: 'root' })
export class PriceAlertsStore {
  private readonly api = inject(PriceAlertsService);
  private readonly authService = inject(CoreAuthService);

  private readonly alertsSignal = signal<PriceAlertResponse[]>([]);
  private readonly loadingSignal = signal(false);
  private readonly loadedSignal = signal(false);
  private readonly errorSignal = signal<string | null>(null);

  /** Newest first */
  readonly alerts = this.alertsSignal.asReadonly();
  readonly isLoading = this.loadingSignal.asReadonly();
  readonly isLoaded = this.loadedSignal.asReadonly();
  readonly error = this.errorSignal.asReadonly();
  readonly activeCount = computed(() => this.alertsSignal().filter((a) => a.active).length);
  readonly atCap = computed(() => this.activeCount() >= MAX_ACTIVE_ALERTS);

  constructor() {
    // Never show one user's alerts to the next user who signs in on this tab
    let previous: number | null | undefined;
    effect(() => {
      const accountId = this.authService.currentUser()?.accountId ?? null;
      if (previous !== undefined && previous !== accountId) {
        untracked(() => this.reset());
      }
      previous = accountId;
    });
  }

  private get accountId(): number | null {
    return this.authService.currentUser()?.accountId ?? null;
  }

  load(): void {
    const accountId = this.accountId;
    if (!accountId) {
      return;
    }
    this.loadingSignal.set(true);
    this.errorSignal.set(null);
    this.api.getPriceAlerts(accountId).subscribe({
      next: (alerts) => {
        this.alertsSignal.set(alerts ?? []);
        this.loadingSignal.set(false);
        this.loadedSignal.set(true);
      },
      error: (err) => {
        this.errorSignal.set(messageOf(err, 'Could not load your price alerts.'));
        this.loadingSignal.set(false);
        this.loadedSignal.set(true);
      },
    });
  }

  ensureLoaded(): void {
    if (!this.loadedSignal() && !this.loadingSignal()) {
      this.load();
    }
  }

  /** Alerts on one symbol, newest first */
  alertsFor(symbol: string): PriceAlertResponse[] {
    return this.alertsSignal().filter((a) => a.symbol === symbol);
  }

  hasActiveAlert(symbol: string): boolean {
    return this.alertsSignal().some((a) => a.symbol === symbol && a.active);
  }

  create(alert: NewPriceAlert): Observable<PriceAlertResponse> {
    return this.call((accountId) => this.api.createPriceAlert(accountId, alert)).pipe(
      tap((created) => this.alertsSignal.update((alerts) => [created, ...alerts])),
    );
  }

  /** Pause (false) or resume (true) */
  setActive(alertId: number, active: boolean): Observable<PriceAlertResponse> {
    return this.call((accountId) => this.api.updatePriceAlert(accountId, alertId, { active })).pipe(
      tap((updated) => this.replace(updated)),
    );
  }

  delete(alertId: number): Observable<void> {
    return this.call((accountId) => this.api.deletePriceAlert(accountId, alertId)).pipe(
      tap(() => this.alertsSignal.update((alerts) => alerts.filter((a) => a.alertId !== alertId))),
      map(() => undefined),
    );
  }

  triggers(alertId: number): Observable<PriceAlertTriggerResponse[]> {
    return this.call((accountId) => this.api.getPriceAlertTriggers(accountId, alertId));
  }

  reset(): void {
    this.alertsSignal.set([]);
    this.loadedSignal.set(false);
    this.errorSignal.set(null);
  }

  private replace(updated: PriceAlertResponse): void {
    this.alertsSignal.update((alerts) => alerts.map((a) => (a.alertId === updated.alertId ? updated : a)));
  }

  private call<T>(request: (accountId: number) => Observable<T>): Observable<T> {
    const accountId = this.accountId;
    if (!accountId) {
      return throwError(() => new Error('You need to be signed in to manage price alerts.'));
    }
    return request(accountId).pipe(
      catchError((err) => throwError(() => new Error(messageOf(err, 'Something went wrong. Please try again.')))),
    );
  }
}

function messageOf(err: unknown, fallback: string): string {
  if (err instanceof HttpErrorResponse) {
    const body = err.error as { errorCode?: string; message?: string } | null;
    // An unmapped route comes back as 404 VAL-422 "Invalid input": the server has no price-alert API
    if (err.status === 404 && body?.errorCode !== 'ALR-404' && body?.errorCode !== 'INS-404') {
      return 'Price alerts are not available on the trading service yet. Restart the order service with the latest build.';
    }
    if (body?.message) {
      return body.message;
    }
    if (err.status === 0) {
      return 'Cannot reach the trading service.';
    }
  }
  return fallback;
}
