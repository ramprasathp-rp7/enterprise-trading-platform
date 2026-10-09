import { Component, Injector, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { NotificationHistoryItem, OrderNotificationsService } from '../../../../api/generated/order-notifications';
import { CoreAuthService } from '../../../../core/auth/core-auth.service';
import { AppNotification, NotificationKind, NotificationService } from '../../../../core/notifications/notification.service';
import { orderStatusNotice } from '../../../../core/notifications/order-messages';
import { formatInstrumentPrice } from '../../../instruments/services/instrument-catalog.service';
import { ReplayAnimationDirective } from '../../../../shared/replay-animation.directive';

export type HistoryCategory = 'orders' | 'alerts' | 'other';
type CategoryFilter = HistoryCategory | 'all';

/** One row of the history: a server record (order outcome, price alert) or a bell notification */
export interface HistoryEntry {
  id: string;
  source: 'server' | 'local';
  category: HistoryCategory;
  kind: NotificationKind;
  title: string;
  message: string;
  occurredAt: string;
  read: boolean;
  /** Server records: the channels it went out on */
  channels: string[] | null;
  /** Server records: whether it was shown in the bell */
  inApp: boolean | null;
  server: NotificationHistoryItem | null;
  local: AppNotification | null;
}

const KIND_ICON: Record<NotificationKind, string> = {
  welcome: 'fa-hand-sparkles',
  'user-registered': 'fa-user-plus',
  'order-created': 'fa-paper-plane',
  'order-filled': 'fa-circle-check',
  'order-rejected': 'fa-circle-xmark',
  'order-cancelled': 'fa-ban',
  report: 'fa-file-arrow-down',
  'price-alert': 'fa-bell',
  'strategy-created': 'fa-robot',
};

const CHANNEL_LABEL: Record<string, { label: string; icon: string }> = {
  EMAIL: { label: 'Email', icon: 'fa-envelope' },
  SMS: { label: 'SMS', icon: 'fa-mobile-screen' },
  IN_APP: { label: 'In-app', icon: 'fa-bell' },
};

/** Bell notifications that the server history already covers (for customers) */
const SERVER_KINDS: readonly NotificationKind[] = ['order-filled', 'order-rejected', 'order-cancelled', 'price-alert'];

/**
 * Notification history: every order outcome and price alert on record (on any channel, with the
 * channels it went to), plus the bell's own notifications (welcome, order received, exports...).
 * Picking one shows everything known about it.
 */
@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, ReplayAnimationDirective],
  templateUrl: './notifications.component.html',
  styleUrls: ['./notifications.component.css'],
})
export class NotificationsComponent implements OnInit {
  private readonly injector = inject(Injector);
  private readonly authService = inject(CoreAuthService);
  private readonly route = inject(ActivatedRoute, { optional: true });
  private readonly router = inject(Router, { optional: true });
  readonly bell = inject(NotificationService);

  readonly isAdmin = this.authService.isAdmin();
  readonly serverItems = signal<NotificationHistoryItem[] | null>(null);
  readonly loading = signal(false);
  readonly loadError = signal<string | null>(null);

  readonly category = signal<CategoryFilter>('all');
  readonly unreadOnly = signal(false);
  readonly query = signal('');
  readonly selectedId = signal<string | null>(null);

  readonly categoryOptions: { value: CategoryFilter; label: string; icon: string }[] = this.isAdmin
    ? [
        { value: 'all', label: 'All', icon: 'fa-inbox' },
        { value: 'other', label: 'Account', icon: 'fa-user' },
      ]
    : [
        { value: 'all', label: 'All', icon: 'fa-inbox' },
        { value: 'orders', label: 'Orders', icon: 'fa-receipt' },
        { value: 'alerts', label: 'Price alerts', icon: 'fa-bell' },
        { value: 'other', label: 'Other', icon: 'fa-circle-info' },
      ];

  /** Server records and bell notifications as one list, newest first */
  readonly entries = computed<HistoryEntry[]>(() => {
    const local = this.bell.notifications();
    const server = this.serverItems();
    const readByKey = new Map(local.filter((n) => n.dedupeKey).map((n) => [n.dedupeKey!, n.read]));
    const fromServer = (server ?? []).map((item) => this.fromServer(item, readByKey));
    const serverKeys = new Set(fromServer.map((e) => this.dedupeKeyFor(e.server!)));
    const fromLocal = local
      .filter((n) => !(n.dedupeKey && serverKeys.has(n.dedupeKey)))
      // With the server history loaded, its records replace the bell's copies of them
      .filter((n) => server === null || !SERVER_KINDS.includes(n.kind))
      .map((n) => this.fromLocal(n));
    return [...fromServer, ...fromLocal].sort((a, b) => b.occurredAt.localeCompare(a.occurredAt));
  });

  readonly counts = computed<Record<CategoryFilter, number>>(() => {
    const all = this.entries();
    return {
      all: all.length,
      orders: all.filter((e) => e.category === 'orders').length,
      alerts: all.filter((e) => e.category === 'alerts').length,
      other: all.filter((e) => e.category === 'other').length,
    };
  });

  readonly unreadCount = computed(() => this.entries().filter((e) => !e.read).length);

  readonly visible = computed<HistoryEntry[]>(() => {
    const category = this.category();
    const q = this.query().trim().toLowerCase();
    return this.entries().filter(
      (e) =>
        (category === 'all' || e.category === category) &&
        (!this.unreadOnly() || !e.read) &&
        (!q || e.title.toLowerCase().includes(q) || e.message.toLowerCase().includes(q) ||
          (e.server?.symbol ?? '').toLowerCase().includes(q)),
    );
  });

  /** Entries grouped by day for the list */
  readonly groups = computed(() => {
    const groups: { label: string; entries: HistoryEntry[] }[] = [];
    for (const entry of this.visible()) {
      const label = this.dayLabel(entry.occurredAt);
      const last = groups[groups.length - 1];
      if (last && last.label === label) {
        last.entries.push(entry);
      } else {
        groups.push({ label, entries: [entry] });
      }
    }
    return groups;
  });

  readonly selected = computed<HistoryEntry | null>(() => {
    const id = this.selectedId();
    return (id && this.entries().find((e) => e.id === id)) || null;
  });

  ngOnInit(): void {
    const wanted = this.route?.snapshot?.queryParamMap?.get('id');
    if (wanted) {
      this.selectedId.set(wanted);
    } else if (this.isAdmin && this.entries().length) {
      // Admins have no server history to wait for: open the newest straight away
      this.selectedId.set(this.entries()[0].id);
    }
    this.load();
  }

  load(): void {
    const accountId = this.isAdmin ? null : this.authService.currentUser()?.accountId;
    if (accountId == null) {
      return;
    }
    this.loading.set(true);
    this.loadError.set(null);
    this.injector
      .get(OrderNotificationsService)
      .getNotificationHistory(accountId, 200)
      .subscribe({
        next: (items) => {
          this.serverItems.set(items ?? []);
          this.loading.set(false);
          if (!this.selectedId() && this.entries().length) {
            this.selectedId.set(this.entries()[0].id);
          }
        },
        error: () => {
          this.loading.set(false);
          this.loadError.set('Could not load your order and alert history. Showing this browser’s notifications only.');
        },
      });
  }

  select(entry: HistoryEntry): void {
    this.selectedId.set(entry.id);
    this.markRead(entry);
    this.router?.navigate([], { queryParams: { id: entry.id }, replaceUrl: true, queryParamsHandling: 'merge' });
  }

  markAllRead(): void {
    this.bell.markAllRead();
  }

  icon(entry: HistoryEntry): string {
    return KIND_ICON[entry.kind] ?? 'fa-bell';
  }

  channel(code: string): { label: string; icon: string } {
    return CHANNEL_LABEL[code] ?? { label: code, icon: 'fa-paper-plane' };
  }

  price(value: number | null | undefined, symbol: string | null | undefined): string {
    return formatInstrumentPrice(value ?? null, symbol ?? null);
  }

  notional(item: NotificationHistoryItem): string {
    const o = item.order;
    const unit = o?.executedPrice ?? o?.limitPrice;
    if (!o?.quantity || unit == null) {
      return '—';
    }
    return formatInstrumentPrice(o.quantity * Number(unit), item.symbol ?? null);
  }

  /** Status shown as a pill: FILLED, REJECTED, ... or the bell's kind */
  statusLabel(entry: HistoryEntry): string | null {
    const status = entry.server?.status;
    if (!status) {
      return null;
    }
    return status.charAt(0) + status.slice(1).toLowerCase();
  }

  private markRead(entry: HistoryEntry): void {
    const key = entry.server ? this.dedupeKeyFor(entry.server) : null;
    const local = entry.local ?? (key ? this.bell.notifications().find((n) => n.dedupeKey === key) : undefined);
    if (local && !local.read) {
      this.bell.markRead(local.id);
    }
  }

  /** The bell's dedupe key for a server record, so read state is shared with the bell */
  private dedupeKeyFor(item: NotificationHistoryItem): string {
    return item.kind === NotificationHistoryItem.KindEnum.Order
      ? `order-status:${item.order?.orderId}:${item.status}`
      : `price-alert:${item.priceAlert?.triggerId}`;
  }

  private fromServer(item: NotificationHistoryItem, readByKey: Map<string, boolean>): HistoryEntry {
    const key = this.dedupeKeyFor(item);
    if (item.kind === NotificationHistoryItem.KindEnum.Order) {
      const notice = orderStatusNotice({
        status: item.status ?? '',
        side: item.order?.side ?? null,
        quantity: item.order?.quantity ?? null,
        symbol: item.symbol ?? null,
        price: item.order?.limitPrice ?? null,
        executedPrice: item.order?.executedPrice ?? null,
        reason: item.order?.reason ?? null,
      });
      return {
        id: item.id,
        source: 'server',
        category: 'orders',
        kind: notice?.kind ?? 'order-filled',
        title: notice?.title ?? 'Order update',
        message: notice?.message ?? `Your ${item.symbol ?? ''} order is ${String(item.status ?? '').toLowerCase()}.`,
        occurredAt: item.occurredAt,
        read: readByKey.get(key) ?? true,
        channels: [...item.channels],
        inApp: item.inApp,
        server: item,
        local: null,
      };
    }
    const alert = item.priceAlert;
    const side = alert?.direction === 'BELOW' ? 'below' : 'above';
    const off = alert?.mode === 'ONCE' ? ' This alert is now off.' : '';
    return {
      id: item.id,
      source: 'server',
      category: 'alerts',
      kind: 'price-alert',
      title: `Price alert: ${item.symbol ?? ''}`,
      message: `${item.symbol} is at ${this.price(alert?.price, item.symbol)}, ${side} your ${this.price(alert?.threshold, item.symbol)} level.${off}`,
      occurredAt: item.occurredAt,
      read: readByKey.get(key) ?? true,
      channels: [...item.channels],
      inApp: item.inApp,
      server: item,
      local: null,
    };
  }

  private fromLocal(n: AppNotification): HistoryEntry {
    const category: HistoryCategory = n.kind.startsWith('order-') ? 'orders' : n.kind === 'price-alert' ? 'alerts' : 'other';
    return {
      id: `local-${n.id}`,
      source: 'local',
      category,
      kind: n.kind,
      title: n.title,
      message: n.message,
      occurredAt: n.createdAt,
      read: n.read,
      channels: null,
      inApp: null,
      server: null,
      local: n,
    };
  }

  private dayLabel(iso: string): string {
    const date = new Date(iso);
    const today = new Date();
    const yesterday = new Date();
    yesterday.setDate(today.getDate() - 1);
    if (date.toDateString() === today.toDateString()) {
      return 'Today';
    }
    if (date.toDateString() === yesterday.toDateString()) {
      return 'Yesterday';
    }
    return date.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' });
  }
}
