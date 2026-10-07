import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, effect, inject, signal, untracked } from '@angular/core';
import { Observable, catchError, map, tap, throwError } from 'rxjs';
import {
  AlertChannel,
  AlertChannelEntry,
  PreferencesResponse,
  PreferencesService,
} from '../../api/generated/preferences';
import { CoreAuthService } from '../../core/auth/core-auth.service';

/**
 * The signed-in customer's preferences: one or more alert channels, each with its contact detail.
 * All calls go through the generated Preferences API client (contracts/preferences-api.yaml).
 */
@Injectable({ providedIn: 'root' })
export class PreferencesStore {
  private readonly api = inject(PreferencesService);
  private readonly authService = inject(CoreAuthService);

  private readonly preferencesSignal = signal<PreferencesResponse | null>(null);
  private readonly loadingSignal = signal(false);
  private readonly errorSignal = signal<string | null>(null);

  readonly preferences = this.preferencesSignal.asReadonly();
  readonly isLoading = this.loadingSignal.asReadonly();
  readonly error = this.errorSignal.asReadonly();

  constructor() {
    // Never show one user's contact details to the next user who signs in on this tab
    let previous: number | null | undefined;
    effect(() => {
      const accountId = this.authService.currentUser()?.accountId ?? null;
      if (previous !== undefined && previous !== accountId) {
        untracked(() => this.clear());
      }
      previous = accountId;
    });
  }

  private get accountId(): number | null {
    return this.authService.currentUser()?.accountId ?? null;
  }

  /** Fetch the signed-in account's preferences (the platform default when none are stored) */
  load(): void {
    const accountId = this.accountId;
    if (accountId == null) {
      return;
    }
    this.loadingSignal.set(true);
    this.errorSignal.set(null);
    this.api.getPreferences(accountId).subscribe({
      next: (preferences) => {
        this.preferencesSignal.set(preferences);
        this.loadingSignal.set(false);
      },
      error: (err) => {
        this.errorSignal.set(messageOf(err, 'Could not load your preferences.'));
        this.loadingSignal.set(false);
      },
    });
  }

  /** Replace the chosen channels; in-app entries never carry a contact detail */
  save(channels: AlertChannelEntry[]): Observable<PreferencesResponse> {
    const body = {
      alertChannels: channels.map((entry) => ({
        channel: entry.channel,
        contactDetail: entry.channel === AlertChannel.InApp ? null : (entry.contactDetail?.trim() || null),
      })),
    };
    return this.call((accountId) => this.api.updatePreferences(accountId, body)).pipe(
      tap((saved) => this.preferencesSignal.set(saved)),
    );
  }

  /** Remove stored preferences; the account goes back to in-app notifications */
  resetToDefault(): Observable<void> {
    return this.call((accountId) => this.api.resetPreferences(accountId)).pipe(
      tap(() => this.load()),
      map(() => undefined),
    );
  }

  /** Clears cached state (e.g. on sign-out) */
  clear(): void {
    this.preferencesSignal.set(null);
    this.errorSignal.set(null);
  }

  /** Runs a call for the signed-in account and turns HTTP failures into readable Error messages */
  private call<T>(request: (accountId: number) => Observable<T>): Observable<T> {
    const accountId = this.accountId;
    if (accountId == null) {
      return throwError(() => new Error('You need to be signed in to change your preferences.'));
    }
    return request(accountId).pipe(
      catchError((err) => throwError(() => new Error(messageOf(err, 'Something went wrong. Please try again.')))),
    );
  }
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
