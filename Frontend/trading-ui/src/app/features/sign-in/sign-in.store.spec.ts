import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { SignInStore } from './sign-in.store';
import { SignInService } from './sign-in.service';

describe('SignInStore', () => {
  let store: SignInStore;
  let service: { login: ReturnType<typeof vi.fn>; logout: ReturnType<typeof vi.fn> };
  const user = { id: 'u-1', username: 'rkumar', accountId: 7, roles: ['CUSTOMER'] };

  beforeEach(() => {
    service = { login: vi.fn(), logout: vi.fn() };
    TestBed.configureTestingModule({ providers: [{ provide: SignInService, useValue: service }] });
    store = TestBed.inject(SignInStore);
  });

  it('starts signed out with no error', () => {
    expect(store.isLoading()).toBe(false);
    expect(store.user()).toBeNull();
    expect(store.error()).toBeNull();
    expect(store.errorMessage()).toBeNull();
  });

  it('signs in and stores the user', async () => {
    service.login.mockReturnValue(of(user));

    const pending = store.login('rkumar', 'secret');
    expect(store.isLoading()).toBe(true);
    const ok = await pending;

    expect(ok).toBe(true);
    expect(service.login).toHaveBeenCalledWith({ username: 'rkumar', password: 'secret' });
    expect(store.user()).toEqual(user);
    expect(store.isLoading()).toBe(false);
  });

  it('stores null when the service completes without a user', async () => {
    service.login.mockReturnValue(of(undefined));

    expect(await store.login('a', 'b')).toBe(true);
    expect(store.user()).toBeNull();
  });

  it('maps a known error code to a readable message', async () => {
    service.login.mockReturnValue(throwError(() => ({ error: { errorCode: 'AUTH-401' } })));

    const ok = await store.login('rkumar', 'wrong');

    expect(ok).toBe(false);
    expect(store.errorCode()).toBe('AUTH-401');
    expect(store.error()).toBe('The username or password is incorrect');
    expect(store.errorMessage()).toBe('The username or password is incorrect');
    expect(store.isLoading()).toBe(false);
  });

  it('uses UNKNOWN when the error has no code', async () => {
    service.login.mockReturnValue(throwError(() => new Error('network')));

    await store.login('a', 'b');

    expect(store.errorCode()).toBe('UNKNOWN');
    expect(store.error()).toBe('An error occurred (UNKNOWN)');
  });

  it('clearError removes the error but keeps the user', async () => {
    service.login.mockReturnValue(throwError(() => ({ error: { errorCode: 'VAL-422' } })));
    await store.login('a', 'b');

    store.clearError();

    expect(store.error()).toBeNull();
    expect(store.errorCode()).toBeNull();
  });

  it('logout clears all state and calls the service', async () => {
    service.login.mockReturnValue(of(user));
    await store.login('rkumar', 'secret');

    store.logout();

    expect(service.logout).toHaveBeenCalled();
    expect(store.user()).toBeNull();
    expect(store.error()).toBeNull();
    expect(store.isLoading()).toBe(false);
  });
});
