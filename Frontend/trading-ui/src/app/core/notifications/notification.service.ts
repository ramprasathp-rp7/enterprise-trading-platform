import { Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { CoreAuthService } from '../auth/core-auth.service';

export type NotificationKind =
  | 'welcome'
  | 'user-registered'
  | 'order-created'
  | 'order-filled'
  | 'order-rejected'
  | 'order-cancelled'
  | 'report'
  | 'price-alert'
  | 'strategy-created';

export interface NotificationLink {
  path: string;
  queryParams?: Record<string, string>;
}

export interface AppNotification {
  id: string;
  kind: NotificationKind;
  title: string;
  message: string;
  /** ISO timestamp */
  createdAt: string;
  read: boolean;
  link?: NotificationLink;
  /** Same key is never notified twice (e.g. one notification per order status) */
  dedupeKey?: string;
}

export interface NewNotification {
  kind: NotificationKind;
  title: string;
  message: string;
  link?: NotificationLink;
  dedupeKey?: string;
}

const MAX_NOTIFICATIONS = 50;
const STORAGE_PREFIX = 'etp-notifications:';
const WELCOMED_PREFIX = 'etp-welcomed:';

/**
 * Whose browser-stored state this is: the user id (the token's subject), which is unique per user.
 * Username alone is not: a user re-created after a database reset, or another person signing in
 * with a reused username, would inherit the old bell. Falls back to username if the id is missing.
 */
export function storageOwner(user: { id?: string | null; username?: string | null } | null | undefined): string | null {
  return user?.id || user?.username || null;
}

/**
 * In-app notification centre (navbar bell).
 * Notifications are kept per user (by user id) in localStorage, newest first, capped at 50.
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly authService = inject(CoreAuthService);

  private readonly items = signal<AppNotification[]>([]);
  private readonly owner = computed(() => storageOwner(this.authService.currentUser()));

  readonly notifications = computed(() => this.items());
  readonly unreadCount = computed(() => this.items().filter((n) => !n.read).length);
  /** The most recent notification added in this session (for screen-reader announcements) */
  readonly latest = signal<AppNotification | null>(null);

  constructor() {
    // Load the signed-in user's notifications; switch when the user changes.
    effect(() => {
      const owner = this.owner();
      untracked(() => {
        this.items.set(owner ? this.load(owner) : []);
        this.latest.set(null);
      });
    });
  }

  notify(input: NewNotification): AppNotification | null {
    const owner = this.owner();
    if (!owner) {
      return null;
    }
    if (input.dedupeKey && this.items().some((n) => n.dedupeKey === input.dedupeKey)) {
      return null;
    }

    const notification: AppNotification = {
      id: `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`,
      createdAt: new Date().toISOString(),
      read: false,
      ...input,
    };
    this.items.update((list) => [notification, ...list].slice(0, MAX_NOTIFICATIONS));
    this.latest.set(notification);
    this.save(owner);
    return notification;
  }

  /**
   * Say hello to a first-time user once.
   * "First time" is per browser: there is no server-side first-login flag.
   */
  welcomeIfFirstVisit(): void {
    const user = this.authService.currentUser();
    const owner = storageOwner(user);
    if (!user?.username || !owner) {
      return;
    }
    const key = WELCOMED_PREFIX + owner;
    if (this.read(key)) {
      return;
    }
    this.notify({
      kind: 'welcome',
      title: `Hello, ${user.username}!`,
      message: "Welcome to Enterprise Trading Platform. We're glad to have you here.",
      dedupeKey: 'welcome',
    });
    this.write(key, '1');
  }

  markRead(id: string): void {
    this.updateAll((list) => list.map((n) => (n.id === id ? { ...n, read: true } : n)));
  }

  markAllRead(): void {
    this.updateAll((list) => list.map((n) => (n.read ? n : { ...n, read: true })));
  }

  clear(): void {
    this.updateAll(() => []);
  }

  private updateAll(change: (list: AppNotification[]) => AppNotification[]): void {
    this.items.update(change);
    const owner = this.owner();
    if (owner) {
      this.save(owner);
    }
  }

  private load(owner: string): AppNotification[] {
    try {
      const parsed = JSON.parse(this.read(STORAGE_PREFIX + owner) ?? '[]');
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }

  private save(owner: string): void {
    this.write(STORAGE_PREFIX + owner, JSON.stringify(this.items()));
  }

  private read(key: string): string | null {
    try {
      return localStorage.getItem(key);
    } catch {
      return null;
    }
  }

  private write(key: string, value: string): void {
    try {
      localStorage.setItem(key, value);
    } catch {
      // Storage unavailable: notifications still work for this session.
    }
  }
}
