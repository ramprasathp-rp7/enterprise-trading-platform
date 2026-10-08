import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { Observable, catchError, map, tap, throwError } from 'rxjs';
import {
  CreateStrategyRequest,
  StrategiesService,
  StrategyExecutionResponse,
  StrategyResponse,
} from '../../api/generated/strategy';
import { CoreAuthService } from '../../core/auth/core-auth.service';
import { NotificationService } from '../../core/notifications/notification.service';

/** Active strategies an account may hold (trading.strategy.max-active-per-account on the server) */
export const MAX_ACTIVE_STRATEGIES = 10;

/**
 * The signed-in account's automated strategies, through the generated client (contracts/strategy-api.yaml).
 */
@Injectable({ providedIn: 'root' })
export class StrategiesStore {
  private readonly api = inject(StrategiesService);
  private readonly authService = inject(CoreAuthService);
  private readonly notifications = inject(NotificationService);

  private readonly strategiesSignal = signal<StrategyResponse[]>([]);
  private readonly loadingSignal = signal(false);
  private readonly loadedSignal = signal(false);
  private readonly errorSignal = signal<string | null>(null);

  /** Newest first */
  readonly strategies = this.strategiesSignal.asReadonly();
  readonly isLoading = this.loadingSignal.asReadonly();
  readonly isLoaded = this.loadedSignal.asReadonly();
  readonly error = this.errorSignal.asReadonly();
  readonly activeCount = computed(() => this.strategiesSignal().filter((s) => s.active).length);
  readonly atCap = computed(() => this.activeCount() >= MAX_ACTIVE_STRATEGIES);

  constructor() {
    // Never show one user's strategies to the next user who signs in on this tab
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

  /** Loads the list; a quiet reload (polling) keeps the current list on screen while it runs */
  load(quiet = false): void {
    const accountId = this.accountId;
    if (!accountId) {
      return;
    }
    if (!quiet) {
      this.loadingSignal.set(true);
    }
    this.api.getStrategies(accountId).subscribe({
      next: (strategies) => {
        this.strategiesSignal.set(strategies ?? []);
        this.errorSignal.set(null);
        this.loadingSignal.set(false);
        this.loadedSignal.set(true);
      },
      error: (err) => {
        this.errorSignal.set(messageOf(err, 'Could not load your strategies.'));
        this.loadingSignal.set(false);
        this.loadedSignal.set(true);
      },
    });
  }

  create(request: CreateStrategyRequest): Observable<StrategyResponse> {
    return this.call((accountId) => this.api.createStrategy(accountId, request)).pipe(
      tap((created) => {
        this.strategiesSignal.update((list) => [created, ...list]);
        this.notifications.notify({
          kind: 'strategy-created',
          title: `Strategy on ${created.symbol} is on`,
          message: `${created.description} It places these orders without asking you; turn it off on the Strategies page.`,
          link: { path: '/strategies' },
          dedupeKey: `strategy-created:${created.strategyId}`,
        });
      }),
    );
  }

  /** Turn off (takes effect immediately on the server) or back on */
  setActive(strategyId: number, active: boolean): Observable<StrategyResponse> {
    return this.call((accountId) => this.api.updateStrategy(accountId, strategyId, { active })).pipe(
      tap((updated) => this.replace(updated)),
    );
  }

  delete(strategyId: number): Observable<void> {
    return this.call((accountId) => this.api.deleteStrategy(accountId, strategyId)).pipe(
      tap(() => this.strategiesSignal.update((list) => list.filter((s) => s.strategyId !== strategyId))),
      map(() => undefined),
    );
  }

  executions(strategyId: number, limit = 50): Observable<StrategyExecutionResponse[]> {
    return this.call((accountId) => this.api.getStrategyExecutions(accountId, strategyId, limit));
  }

  reset(): void {
    this.strategiesSignal.set([]);
    this.loadedSignal.set(false);
    this.errorSignal.set(null);
  }

  private replace(updated: StrategyResponse): void {
    this.strategiesSignal.update((list) => list.map((s) => (s.strategyId === updated.strategyId ? updated : s)));
  }

  private call<T>(request: (accountId: number) => Observable<T>): Observable<T> {
    const accountId = this.accountId;
    if (!accountId) {
      return throwError(() => new Error('You need to be signed in to manage strategies.'));
    }
    return request(accountId).pipe(
      catchError((err) => throwError(() => new Error(messageOf(err, 'Something went wrong. Please try again.')))),
    );
  }
}

export function messageOf(err: unknown, fallback: string): string {
  if (err instanceof HttpErrorResponse) {
    const body = err.error as { errorCode?: string; message?: string } | null;
    // An unmapped route comes back as 404 VAL-422: the server has no strategy API yet
    if (err.status === 404 && !['STR-404', 'INS-404'].includes(body?.errorCode ?? '')) {
      return 'Strategies are not available on the trading service yet. Restart the order service with the latest build.';
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
