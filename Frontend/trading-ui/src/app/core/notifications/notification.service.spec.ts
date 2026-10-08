import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { NotificationService } from './notification.service';
import { CoreAuthService } from '../auth/core-auth.service';

describe('NotificationService', () => {
  let service: NotificationService;
  let user: ReturnType<typeof signal<any>>;
  let admin: boolean;

  const create = () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [{ provide: CoreAuthService, useValue: { currentUser: user, isAdmin: () => admin } }],
    });
    service = TestBed.inject(NotificationService);
    TestBed.tick();
  };

  beforeEach(() => {
    localStorage.clear();
    user = signal<any>({ id: 'u-rkumar', username: 'rkumar', accountId: 7 });
    admin = false;
    create();
  });

  it('adds notifications newest first, unread, and counts them', () => {
    service.notify({ kind: 'order-created', title: 'A', message: 'a' });
    service.notify({ kind: 'report', title: 'B', message: 'b' });

    expect(service.notifications().map((n) => n.title)).toEqual(['B', 'A']);
    expect(service.unreadCount()).toBe(2);
    expect(service.latest()?.title).toBe('B');
  });

  it('skips a notification whose dedupe key was already used', () => {
    expect(service.notify({ kind: 'order-filled', title: 'Filled', message: 'x', dedupeKey: 'k1' })).not.toBeNull();
    expect(service.notify({ kind: 'order-filled', title: 'Filled', message: 'x', dedupeKey: 'k1' })).toBeNull();

    expect(service.notifications().length).toBe(1);
  });

  it('keeps at most 50 notifications', () => {
    for (let i = 0; i < 55; i++) {
      service.notify({ kind: 'report', title: `n${i}`, message: '' });
    }

    expect(service.notifications().length).toBe(50);
    expect(service.notifications()[0].title).toBe('n54');
  });

  it('marks one or all as read, and clears', () => {
    const first = service.notify({ kind: 'report', title: 'A', message: '' })!;
    service.notify({ kind: 'report', title: 'B', message: '' });

    service.markRead(first.id);
    expect(service.unreadCount()).toBe(1);

    service.markAllRead();
    expect(service.unreadCount()).toBe(0);

    service.clear();
    expect(service.notifications()).toEqual([]);
  });

  it('persists per user and reloads when the user changes', () => {
    service.notify({ kind: 'report', title: 'Mine', message: '' });

    user.set({ id: 'u-other', username: 'someone-else', accountId: 8 });
    TestBed.tick();
    expect(service.notifications()).toEqual([]);

    user.set({ id: 'u-rkumar', username: 'rkumar', accountId: 7 });
    TestBed.tick();
    expect(service.notifications().map((n) => n.title)).toEqual(['Mine']);

    create();
    expect(service.notifications().map((n) => n.title)).toEqual(['Mine']);
  });

  it('starts a new user with an empty bell even if the username was used before on this browser', () => {
    service.notify({ kind: 'order-created', title: 'Old order', message: '' });
    service.welcomeIfFirstVisit();

    // Same username, different user (e.g. re-created after a database reset)
    user.set({ id: 'u-rkumar-2', username: 'rkumar', accountId: 7 });
    TestBed.tick();
    expect(service.notifications()).toEqual([]);

    service.welcomeIfFirstVisit();
    expect(service.notifications().map((n) => n.kind)).toEqual(['welcome']);
  });

  it('falls back to the username when the user has no id', () => {
    user.set({ username: 'rkumar', accountId: 7 });
    TestBed.tick();
    service.notify({ kind: 'report', title: 'x', message: '' });

    expect(localStorage.getItem('etp-notifications:rkumar')).toContain('"title":"x"');
  });

  it('does nothing without a signed-in user', () => {
    user.set(null);
    TestBed.tick();

    expect(service.notify({ kind: 'report', title: 'x', message: '' })).toBeNull();
    service.welcomeIfFirstVisit();
    expect(service.notifications()).toEqual([]);
  });

  it('ignores corrupt stored data', () => {
    localStorage.setItem('etp-notifications:u-rkumar', '{not json');
    create();

    expect(service.notifications()).toEqual([]);
  });

  describe('hello for first-time users', () => {
    it('says hello once per user', () => {
      service.welcomeIfFirstVisit();
      service.welcomeIfFirstVisit();
      create();
      service.welcomeIfFirstVisit();

      const welcome = service.notifications().filter((n) => n.kind === 'welcome');
      expect(welcome.length).toBe(1);
      expect(welcome[0].title).toBe('Hello, rkumar!');
      expect(welcome[0].message).toBe("Welcome to Enterprise Trading Platform. We're glad to have you here.");
      expect(welcome[0].link).toBeUndefined();
    });

    it('says hello separately to each user on the browser', () => {
      service.welcomeIfFirstVisit();
      user.set({ id: 'u-ops', username: 'ops', accountId: 1 });
      TestBed.tick();

      service.welcomeIfFirstVisit();

      expect(service.notifications()[0].title).toBe('Hello, ops!');
    });
  });
});
