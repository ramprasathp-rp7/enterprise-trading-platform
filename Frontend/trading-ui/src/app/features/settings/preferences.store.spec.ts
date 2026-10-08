import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { firstValueFrom, of, throwError } from 'rxjs';
import { PreferencesStore } from './preferences.store';
import {
  AdminPreferencesService,
  PreferencesRequest,
  PreferencesResponse,
  PreferencesService,
} from '../../api/generated/preferences';
import { CoreAuthService } from '../../core/auth/core-auth.service';

const defaults = (): PreferencesResponse => ({
  accountId: 7,
  alertChannels: [{ channel: 'IN_APP', contactDetail: null }],
  configured: false,
  updatedAt: null,
});

describe('PreferencesStore', () => {
  let api: Record<string, ReturnType<typeof vi.fn>>;
  let adminApi: Record<string, ReturnType<typeof vi.fn>>;
  let user: ReturnType<typeof signal<{ id: string; accountId: number } | null>>;
  let admin: boolean;
  let store: PreferencesStore;

  beforeEach(() => {
    api = {
      getPreferences: vi.fn(() => of(defaults())),
      updatePreferences: vi.fn((accountId: number, body: PreferencesRequest) =>
        of({ accountId, alertChannels: body.alertChannels, configured: true, updatedAt: '2026-10-06T09:00:00Z' }),
      ),
      resetPreferences: vi.fn(() => of(null)),
    };
    adminApi = {
      getAdminPreferences: vi.fn(() =>
        of({ alertChannels: [{ channel: 'IN_APP', contactDetail: null }], configured: false, updatedAt: null }),
      ),
      updateAdminPreferences: vi.fn((body: PreferencesRequest) =>
        of({ alertChannels: body.alertChannels, configured: true, updatedAt: '2026-10-06T09:00:00Z' }),
      ),
      resetAdminPreferences: vi.fn(() => of(null)),
    };
    user = signal<{ id: string; accountId: number } | null>({ id: 'u-7', accountId: 7 });
    admin = false;
    TestBed.configureTestingModule({
      providers: [
        { provide: PreferencesService, useValue: api },
        { provide: AdminPreferencesService, useValue: adminApi },
        { provide: CoreAuthService, useValue: { currentUser: user, isAdmin: () => admin } },
      ],
    });
    store = TestBed.inject(PreferencesStore);
  });

  it('loads the signed-in account’s preferences, showing the default when none are stored', () => {
    store.load();

    expect(api['getPreferences']).toHaveBeenCalledWith(7);
    expect(store.preferences()?.alertChannels.map((c) => c.channel)).toEqual(['IN_APP']);
    expect(store.preferences()?.configured).toBe(false);
    expect(store.isLoading()).toBe(false);
  });

  it('saves several channels for the signed-in account and keeps the saved value', async () => {
    const saved = await firstValueFrom(
      store.save([
        { channel: 'EMAIL', contactDetail: '  customer@example.com ' },
        { channel: 'SMS', contactDetail: '+447700900123' },
      ]),
    );

    expect(api['updatePreferences']).toHaveBeenCalledWith(7, {
      alertChannels: [
        { channel: 'EMAIL', contactDetail: 'customer@example.com' },
        { channel: 'SMS', contactDetail: '+447700900123' },
      ],
    });
    expect(saved.configured).toBe(true);
    expect(store.preferences()?.alertChannels).toHaveLength(2);
  });

  it('never sends a contact detail with the in-app channel', async () => {
    await firstValueFrom(store.save([{ channel: 'IN_APP', contactDetail: 'left-over@example.com' }]));

    expect(api['updatePreferences']).toHaveBeenCalledWith(7, {
      alertChannels: [{ channel: 'IN_APP', contactDetail: null }],
    });
  });

  it('resets to default and reloads', async () => {
    await firstValueFrom(store.resetToDefault());

    expect(api['resetPreferences']).toHaveBeenCalledWith(7);
    expect(api['getPreferences']).toHaveBeenCalledWith(7);
    expect(store.preferences()?.configured).toBe(false);
  });

  it('surfaces the server message on failure', async () => {
    api['updatePreferences'].mockReturnValueOnce(
      throwError(() => new HttpErrorResponse({
        status: 422,
        error: { errorCode: 'PREF-422', message: 'EMAIL is listed more than once' },
      })),
    );

    await expect(firstValueFrom(store.save([{ channel: 'EMAIL', contactDetail: 'a@b.co' }]))).rejects.toThrow(
      'EMAIL is listed more than once',
    );
  });

  it('reports a load failure and an unreachable service', () => {
    api['getPreferences'].mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 0 })));

    store.load();

    expect(store.error()).toBe('Cannot reach the trading service.');
    expect(store.isLoading()).toBe(false);
  });

  it('falls back to a generic message for an unexplained failure', async () => {
    api['resetPreferences'].mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));

    await expect(firstValueFrom(store.resetToDefault())).rejects.toThrow('Something went wrong');
  });

  it('does nothing and refuses writes when nobody is signed in', async () => {
    user.set(null);

    store.load();
    expect(api['getPreferences']).not.toHaveBeenCalled();
    await expect(firstValueFrom(store.save([{ channel: 'IN_APP' }]))).rejects.toThrow('signed in');
  });

  it('forgets the previous user’s preferences when the account changes', () => {
    store.load();
    TestBed.tick();
    expect(store.preferences()).not.toBeNull();

    user.set({ id: 'u-8', accountId: 8 });
    TestBed.tick();

    expect(store.preferences()).toBeNull();
  });

  describe('for an admin', () => {
    beforeEach(() => {
      admin = true;
      user.set({ id: 'admin-1', accountId: 0 });
    });

    it('loads, saves and resets on the admin routes, never the account routes', async () => {
      store.load();
      expect(adminApi['getAdminPreferences']).toHaveBeenCalled();
      expect(store.preferences()?.configured).toBe(false);

      const saved = await firstValueFrom(
        store.save([
          { channel: 'EMAIL', contactDetail: ' ops@example.com ' },
          { channel: 'IN_APP', contactDetail: 'ignored' },
        ]),
      );
      expect(adminApi['updateAdminPreferences']).toHaveBeenCalledWith({
        alertChannels: [
          { channel: 'EMAIL', contactDetail: 'ops@example.com' },
          { channel: 'IN_APP', contactDetail: null },
        ],
      });
      expect(saved.configured).toBe(true);

      await firstValueFrom(store.resetToDefault());
      expect(adminApi['resetAdminPreferences']).toHaveBeenCalled();

      expect(api['getPreferences']).not.toHaveBeenCalled();
      expect(api['updatePreferences']).not.toHaveBeenCalled();
      expect(api['resetPreferences']).not.toHaveBeenCalled();
    });

    it('works even though the admin holds account 0', () => {
      store.load();
      expect(adminApi['getAdminPreferences']).toHaveBeenCalledTimes(1);
    });

    it('surfaces the server message on failure', async () => {
      adminApi['updateAdminPreferences'].mockReturnValueOnce(
        throwError(() => new HttpErrorResponse({ status: 422, error: { message: 'SMS requires a telephone number' } })),
      );

      await expect(firstValueFrom(store.save([{ channel: 'SMS', contactDetail: '' }]))).rejects.toThrow(
        'SMS requires a telephone number',
      );
    });
  });
});
