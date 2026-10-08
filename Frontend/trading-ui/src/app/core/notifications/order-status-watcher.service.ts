import { Injectable, inject , signal } from '@angular/core';
import { Subscription, timer } from 'rxjs';
import { OrderNotificationResponse, OrderNotificationsService } from '../../api/generated/order-notifications';
import { CoreAuthService } from '../auth/core-auth.service';
import { NotificationService, storageOwner } from './notification.service';
import { orderStatusNotice } from './order-messages';

/** How often the feed is checked while an order placed in this session may still complete */
export const ORDER_FEED_FAST_MS = 5_000;
/** How often the feed is checked otherwise (orders placed elsewhere, late outcomes) */
export const ORDER_FEED_SLOW_MS = 30_000;
/** Fast polling lasts this long after the last order placed here */
const FAST_WINDOW_MS = 10 * 60_000;
const CURSOR_PREFIX = 'etp-order-notification-cursor:';
/**
 * Outcomes older than this are skipped (the cursor still moves past them). Real outcomes arrive
 * within seconds; this stops a first-time backfill of the topic's history from flooding the bell.
 */
export const ORDER_NOTIFICATION_MAX_AGE_MS = 60 * 60_000;

/**
 * Puts order outcomes (filled, rejected, cancelled) into the navbar bell, whichever page the
 * customer is on. Every one of them comes from the `trade-events` Kafka topic: the order service
 * consumes it and serves GET /accounts/{id}/order-notifications, which this service polls.
 * Nothing here infers an outcome from order history or from the place-order response.
 *
 * The cursor (largest notification id already handled) is kept per user and account in
 * localStorage, so a new user on an account another user has used here starts fresh.
 * On the very first start there is no cursor: the latest id only sets it, so history is not
 * replayed into the bell. Each session first checks the saved cursor against the feed's newest
 * id; if the feed was reset (newest < cursor) the cursor restarts from it instead of waiting
 * forever. Polling is fast for a while after an order is placed, slow otherwise.
 */
@Injectable({ providedIn: 'root' })
export class OrderStatusWatcherService {
  private readonly api = inject(OrderNotificationsService);
  private readonly authService = inject(CoreAuthService);
  private readonly notifications = inject(NotificationService);

  private readonly known = new Map<string, string>();
  private readonly fillCount = signal(0);
  /** Bumped each time a watched order fills, so views showing holdings can reload them */
  readonly fills = this.fillCount.asReadonly();
  private polling: Subscription | null = null;
  private accountId: number | null = null;
  private owner: string | null = null;
  private inFlight = false;
  private lastPoll = 0;
  private fastUntil = 0;
  /** Whether this session has checked the saved cursor against the feed's newest id */
  private cursorChecked = false;

  /** Start watching the signed-in customer's order notifications. */
  start(): void {
    const user = this.authService.currentUser();
    const accountId = user?.accountId ?? null;
    if (this.polling || !accountId) {
      return;
    }
    this.accountId = accountId;
    this.owner = storageOwner(user);
    this.lastPoll = 0;
    this.cursorChecked = false;
    this.polling = timer(0, ORDER_FEED_FAST_MS).subscribe(() => this.tick());
  }

  /**
   * Called after an order is placed: check the feed often until its outcome is likely in.
   * The outcome itself is only ever announced from the feed.
   */
  track(_orderId?: string, _status?: string): void {
    this.fastUntil = Date.now() + FAST_WINDOW_MS;
    this.start();
  }

  stop(): void {
    this.polling?.unsubscribe();
    this.polling = null;
    this.accountId = null;
    this.owner = null;
    this.inFlight = false;
    this.fastUntil = 0;
    this.cursorChecked = false;
  }

  get isPolling(): boolean {
    return this.polling !== null;
  }

  /** Polls now if due: every tick while fast, every ORDER_FEED_SLOW_MS otherwise */
  private tick(): void {
    const now = Date.now();
    const fast = now < this.fastUntil;
    if (fast || this.lastPoll === 0 || now - this.lastPoll >= ORDER_FEED_SLOW_MS) {
      this.poll();
    }
  }

  /** One read of the feed; public for tests */
  poll(): void {
    const accountId = this.accountId;
    if (!accountId || this.inFlight) {
      return;
    }
    const cursor = this.readCursor(accountId);
    this.inFlight = true;
    this.lastPoll = Date.now();

    if (cursor === null || !this.cursorChecked) {
      // No cursor yet, or first poll this session: learn the feed's newest id. A saved cursor
      // ahead of it means the feed was reset (e.g. the table recreated), so start again from it.
      this.api.getOrderNotifications(accountId, undefined, 1).subscribe({
        next: (latest) => {
          this.inFlight = false;
          if (accountId !== this.accountId) {
            return;
          }
          this.cursorChecked = true;
          const newest = latest?.[latest.length - 1]?.id ?? 0;
          if (cursor === null || newest < cursor) {
            this.writeCursor(accountId, newest);
          } else {
            this.poll(); // cursor is valid: read what is new since it
          }
        },
        error: () => {
          this.inFlight = false;
        },
      });
      return;
    }

    this.api.getOrderNotifications(accountId, cursor).subscribe({
      next: (items) => {
        this.inFlight = false;
        if (accountId !== this.accountId) {
          return;
        }
        this.handle(accountId, items ?? [], true);
      },
      error: () => {
        // Transient failure: try again on the next tick.
        this.inFlight = false;
      },
    });
  }

  private handle(accountId: number, oldestFirst: OrderNotificationResponse[], baselined: boolean): void {
    let cursor = this.readCursor(accountId) ?? 0;
    const oldest = Date.now() - ORDER_NOTIFICATION_MAX_AGE_MS;
    for (const item of oldestFirst) {
      const occurredAt = Date.parse(item.occurredAt);
      const recent = Number.isNaN(occurredAt) || occurredAt >= oldest;
      if (baselined && recent) {
        this.announce(item);
        if (item.status?.toLowerCase() === 'filled') {
          this.fillCount.update((n) => n + 1);
        }
      }
      cursor = Math.max(cursor, item.id);
    }
    this.writeCursor(accountId, cursor);
  }

  private announce(item: OrderNotificationResponse): void {
    const notice = orderStatusNotice({
      status: item.status,
      side: item.side ?? null,
      quantity: item.quantity ?? null,
      symbol: item.symbol ?? null,
      price: item.price ?? null,
      executedPrice: item.executedPrice ?? null,
      reason: item.reason ?? null,
    });
    if (notice) {
      this.notifications.notify({
        ...notice,
        link: { path: '/blotter' },
        dedupeKey: `order-status:${item.orderId}:${item.status}`,
      });
    }
  }

  private readCursor(accountId: number): number | null {
    try {
      const raw = localStorage.getItem(this.cursorKey(accountId));
      if (raw === null) {
        return null;
      }
      const id = Number(raw);
      return Number.isFinite(id) && id >= 0 ? id : 0;
    } catch {
      // Storage unavailable: treat as baselined from the start; dedupe keys stop repeats this session
      return 0;
    }
  }

  private writeCursor(accountId: number, id: number): void {
    try {
      localStorage.setItem(this.cursorKey(accountId), String(id));
    } catch {
      // Storage unavailable: dedupe keys still stop repeats within this session.
    }
  }

  private cursorKey(accountId: number): string {
    return `${CURSOR_PREFIX}${this.owner}:${accountId}`;
  }
}
