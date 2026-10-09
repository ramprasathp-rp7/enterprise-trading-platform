import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { firstValueFrom, of, throwError } from 'rxjs';
import { AccountSettingsStore, DEFAULT_ACCOUNT_SETTINGS } from './account-settings.store';
import { AccountSettingsRequest, AccountSettingsService } from '../../api/generated/account-settings';
import { CoreAuthService } from '../auth/core-auth.service';

describe('AccountSettingsStore', () => {
  let api: Record<string, ReturnType<typeof vi.fn>>;
  let user: ReturnType<typeof signal<{ id: string; accountId: number } | null>>;
  let admin: boolean;
  let store: AccountSettingsStore;

  beforeEach(() => {
    api = {
      getAccountSettings: vi.fn(() =>
        of({ accountId: 7, orderBudget: 5000, market: 'USA', defaultQuantity: 10, configured: true, updatedAt: '2026-10-09T08:00:00Z' }),
      ),
      updateAccountSettings: vi.fn((accountId: number, body: AccountSettingsRequest) =>
        of({ accountId, ...body, configured: true, updatedAt: '2026-10-09T09:00:00Z' }),
      ),
      resetAccountSettings: vi.fn(() => of(null)),
    };
    user = signal<{ id: string; accountId: number } | null>({ id: 'u-7', accountId: 7 });
    admin = false;
    TestBed.configureTestingModule({
      providers: [
        { provide: AccountSettingsService, useValue: api },
        { provide: CoreAuthService, useValue: { currentUser: user, isAdmin: () => admin } },
      ],
    });
    store = TestBed.inject(AccountSettingsStore);
  });

  it('starts with the defaults, which apply until the settings load', () => {
    expect(store.settings()).toEqual(DEFAULT_ACCOUNT_SETTINGS);
    expect(store.market()).toBe('ALL');
    expect(store.orderBudget()).toBeNull();
  });

  it('loads the signed-in account’s settings once', () => {
    store.ensureLoaded();
    store.ensureLoaded();

    expect(api['getAccountSettings']).toHaveBeenCalledTimes(1);
    expect(api['getAccountSettings']).toHaveBeenCalledWith(7);
    expect(store.orderBudget()).toBe(5000);
    expect(store.market()).toBe('USA');
    expect(store.defaultQuantity()).toBe(10);
    expect(store.isLoaded()).toBe(true);
  });

  it('saves and keeps the saved value', async () => {
    const saved = await firstValueFrom(store.save({ orderBudget: 2500, market: 'NSE', defaultQuantity: null }));

    expect(api['updateAccountSettings']).toHaveBeenCalledWith(7, { orderBudget: 2500, market: 'NSE', defaultQuantity: null });
    expect(saved.market).toBe('NSE');
    expect(store.market()).toBe('NSE');
  });

  it('resets to the defaults', async () => {
    store.ensureLoaded();
    await firstValueFrom(store.reset());

    expect(api['resetAccountSettings']).toHaveBeenCalledWith(7);
    expect(store.settings()).toEqual(DEFAULT_ACCOUNT_SETTINGS);
  });

  it('reports load failures and surfaces server messages on save', async () => {
    api['getAccountSettings'].mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 0 })));
    store.ensureLoaded();
    expect(store.error()).toBe('Cannot reach the trading service.');

    api['updateAccountSettings'].mockReturnValueOnce(
      throwError(() => new HttpErrorResponse({ status: 422, error: { message: 'Invalid input' } })),
    );
    await expect(firstValueFrom(store.save({ orderBudget: -1, market: 'ALL', defaultQuantity: null }))).rejects.toThrow('Invalid input');
  });

  it('does nothing for admins, who have no trading account', async () => {
    admin = true;

    store.ensureLoaded();
    expect(api['getAccountSettings']).not.toHaveBeenCalled();
    await expect(firstValueFrom(store.save({ orderBudget: null, market: 'ALL', defaultQuantity: null }))).rejects.toThrow('signed in');
  });

  it('forgets the previous user’s settings when someone else signs in', () => {
    store.ensureLoaded();
    TestBed.tick();
    expect(store.market()).toBe('USA');

    user.set({ id: 'u-8', accountId: 8 });
    TestBed.tick();

    expect(store.settings()).toEqual(DEFAULT_ACCOUNT_SETTINGS);
    expect(store.isLoaded()).toBe(false);
  });
});
