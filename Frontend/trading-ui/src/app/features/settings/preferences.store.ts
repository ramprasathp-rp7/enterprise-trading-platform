import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, effect, inject, signal, untracked } from '@angular/core';
import { Observable, catchError, map, tap, throwError } from 'rxjs';
import {
  AdminPreferencesService,
  AlertChannel,
  AlertChannelEntry,
  PreferencesRequest,
  PreferencesResponse,
  PreferencesService,
} from '../../api/generated/preferences';
import { CoreAuthService } from '../../core/auth/core-auth.service';
import { storageOwner } from '../../core/notifications/notification.service';

/** What the Preferences page shows: the same for customers and admins */
export type Preferences = Pick<PreferencesResponse, 'alertChannels' | 'configured' | 'updatedAt'>;

/**
 * The signed-in user's preferences: one or more alert channels, each with its contact detail.
 * Customers' are kept per account; admins have no trading account, so theirs are kept per admin
 * user on the admin routes. All calls go through the generated Preferences API client
 * (contracts/preferences-api.yaml).
 */
@Injectable({ providedIn: 'root' })
export class PreferencesStore {
  private readonly api = inject(PreferencesService);
  private readonly adminApi = inject(AdminPreferencesService);
  private readonly authService = inject(CoreAuthService);

  private readonly preferencesSignal = signal<Preferences | null>(null);
  private readonly loadingSignal = signal(false);
  private readonly errorSignal = signal<string | null>(null);

  readonly preferences = this.preferencesSignal.asReadonly();
  readonly isLoading = this.loadingSignal.asReadonly();
  readonly error = this.errorSignal.asReadonly();

  constructor() {
    // Never show one user's contact details to the next user who signs in on this tab
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
    return this.authService.currentUser()?.accountId ?? null;
  }

  private get isAdmin(): boolean {
    return this.authService.isAdmin();
  }

  /** Fetch the signed-in user's preferences (the platform default when none are stored) */
  load(): void {
    const request = this.isAdmin
      ? this.adminApi.getAdminPreferences()
      : this.accountId != null
        ? this.api.getPreferences(this.accountId)
        : null;
    if (!request) {
      return;
    }
    this.loadingSignal.set(true);
    this.errorSignal.set(null);
    request.subscribe({
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
  save(channels: AlertChannelEntry[]): Observable<Preferences> {
    const body: PreferencesRequest = {
      alertChannels: channels.map((entry) => ({
        channel: entry.channel,
        contactDetail: entry.channel === AlertChannel.InApp ? null : (entry.contactDetail?.trim() || null),
      })),
    };
    return this.call<Preferences>(
      () => this.adminApi.updateAdminPreferences(body),
      (accountId) => this.api.updatePreferences(accountId, body),
    ).pipe(tap((saved) => this.preferencesSignal.set(saved)));
  }

  /** Remove stored preferences; the user goes back to in-app notifications */
  resetToDefault(): Observable<void> {
    return this.call<unknown>(
      () => this.adminApi.resetAdminPreferences(),
      (accountId) => this.api.resetPreferences(accountId),
    ).pipe(
      tap(() => this.load()),
      map(() => undefined),
    );
  }

  /** Clears cached state (e.g. on sign-out) */
  clear(): void {
    this.preferencesSignal.set(null);
    this.errorSignal.set(null);
  }

  /**
   * Runs the admin call for an admin, or the customer call for the signed-in account, and turns
   * HTTP failures into readable Error messages
   */
  private call<T>(adminRequest: () => Observable<T>, request: (accountId: number) => Observable<T>): Observable<T> {
    let call: Observable<T>;
    if (this.isAdmin) {
      call = adminRequest();
    } else if (this.accountId != null) {
      call = request(this.accountId);
    } else {
      return throwError(() => new Error('You need to be signed in to change your preferences.'));
    }
    return call.pipe(
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
