import { Injectable, inject } from '@angular/core';
import { Subscription, timer } from 'rxjs';
import { AlertDirection, AlertMode, PriceAlertTriggerResponse, PriceAlertsService } from '../../../api/generated/watchlist';
import { CoreAuthService } from '../../../core/auth/core-auth.service';
import { NotificationService, storageOwner } from '../../../core/notifications/notification.service';
import { PriceAlertsStore } from './price-alerts.store';
import { formatInstrumentPrice } from '../../instruments/services/instrument-catalog.service';

export const PRICE_ALERT_POLL_MS = 30_000;
const CURSOR_PREFIX = 'etp-price-alert-cursor:';

const { Delivered, Pending } = PriceAlertTriggerResponse.DeliveryStatusEnum;

/**
 * Puts fired price alerts into the navbar bell, whichever page the customer is on.
 *
 * The order service evaluates alerts against market data and hands each firing to its
 * notifier, which publishes it as a DELIVERED trigger. This service polls
 * GET /price-alerts/triggers?since=… and turns each new delivered trigger into a
 * 'price-alert' notification (deduplicated by trigger id).
 *
 * The cursor (newest trigger time already handled) is kept per user and account in localStorage.
 * On the very first start there is no cursor: existing triggers only set it, so an account's
 * history is not replayed into the bell.
 */
@Injectable({ providedIn: 'root' })
export class PriceAlertWatcherService {
  private readonly api = inject(PriceAlertsService);
  private readonly authService = inject(CoreAuthService);
  private readonly notifications = inject(NotificationService);
  private readonly alertsStore = inject(PriceAlertsStore);

  private polling: Subscription | null = null;
  private accountId: number | null = null;
  private owner: string | null = null;
  private inFlight = false;

  start(): void {
    const user = this.authService.currentUser();
    const accountId = user?.accountId ?? null;
    if (this.polling || !accountId) {
      return;
    }
    this.accountId = accountId;
    this.owner = storageOwner(user);
    this.polling = timer(0, PRICE_ALERT_POLL_MS).subscribe(() => this.poll());
  }

  stop(): void {
    this.polling?.unsubscribe();
    this.polling = null;
    this.accountId = null;
    this.owner = null;
    this.inFlight = false;
  }

  get isPolling(): boolean {
    return this.polling !== null;
  }

  /** One poll; public for tests */
  poll(): void {
    const accountId = this.accountId;
    if (!accountId || this.inFlight) {
      return;
    }
    const cursor = this.readCursor(accountId);
    this.inFlight = true;
    this.api.getRecentPriceAlertTriggers(accountId, cursor.since ?? undefined).subscribe({
      next: (triggers) => {
        this.inFlight = false;
        if (accountId !== this.accountId) {
          return;
        }
        this.handle(accountId, triggers ?? [], cursor.baselined);
      },
      error: () => {
        // Transient failure: try again on the next tick.
        this.inFlight = false;
      },
    });
  }

  private handle(accountId: number, newestFirst: PriceAlertTriggerResponse[], baselined: boolean): void {
    const oldestFirst = [...newestFirst].reverse();
    let since = this.readCursor(accountId).since;
    let announced = 0;

    for (const trigger of oldestFirst) {
      // A PENDING trigger may still become DELIVERED with this timestamp: don't move past it.
      if (trigger.deliveryStatus === Pending) {
        break;
      }
      if (trigger.deliveryStatus === Delivered && baselined) {
        if (this.notifications.notify(toNotification(trigger))) {
          announced++;
        }
      }
      since = trigger.triggeredAt;
    }

    this.writeCursor(accountId, since);
    if (announced > 0 && this.alertsStore.isLoaded()) {
      // ONCE alerts have just turned off; refresh their status on the Watchlists page.
      this.alertsStore.load();
    }
  }

  private readCursor(accountId: number): { baselined: boolean; since: string | null } {
    try {
      const raw = localStorage.getItem(this.cursorKey(accountId));
      if (raw === null) {
        return { baselined: false, since: null };
      }
      return { baselined: true, since: raw || null };
    } catch {
      return { baselined: true, since: null };
    }
  }

  private writeCursor(accountId: number, since: string | null): void {
    try {
      localStorage.setItem(this.cursorKey(accountId), since ?? '');
    } catch {
      // Storage unavailable: dedupe keys still stop repeats within this session.
    }
  }

  private cursorKey(accountId: number): string {
    return `${CURSOR_PREFIX}${this.owner}:${accountId}`;
  }
}

function toNotification(trigger: PriceAlertTriggerResponse) {
  const side = trigger.direction === AlertDirection.Above ? 'above' : 'below';
  const off = trigger.mode === AlertMode.Once ? ' This alert is now off.' : '';
  return {
    kind: 'price-alert' as const,
    title: `Price alert: ${trigger.symbol}`,
    message: `${trigger.symbol} is at ${formatInstrumentPrice(trigger.price, trigger.symbol)}, ${side} your ${formatInstrumentPrice(trigger.threshold, trigger.symbol)} level.${off}`,
    link: { path: '/watchlists' },
    dedupeKey: `price-alert:${trigger.triggerId}`,
  };
}

