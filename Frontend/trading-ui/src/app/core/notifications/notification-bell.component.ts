import { Component, ElementRef, HostListener, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AppNotification, NotificationKind, NotificationService } from './notification.service';
import { CoreAuthService } from '../auth/core-auth.service';

const KIND_ICON: Record<NotificationKind, string> = {
  welcome: 'fa-hand-sparkles',
  'user-registered': 'fa-user-check',
  'order-created': 'fa-paper-plane',
  'order-filled': 'fa-circle-check',
  'order-rejected': 'fa-circle-xmark',
  'order-cancelled': 'fa-ban',
  report: 'fa-file-pdf',
  'price-alert': 'fa-chart-line',
  'strategy-created': 'fa-robot',
};

/**
 * Navbar bell: unread badge + notification panel.
 */
@Component({
  selector: 'app-notification-bell',
  standalone: true,
  imports: [CommonModule],
  template: `
    <button
      type="button"
      class="action-icon bell"
      [class.has-unread]="service.unreadCount() > 0"
      [attr.aria-label]="label()"
      [attr.aria-expanded]="open()"
      aria-haspopup="dialog"
      [title]="label()"
      data-testid="notification-bell"
      (click)="toggle($event)"
    >
      <i class="fas fa-bell"></i>
      @if (service.unreadCount() > 0) {
        <span class="badge" data-testid="notification-badge">{{ service.unreadCount() > 9 ? '9+' : service.unreadCount() }}</span>
      }
    </button>

    @if (open()) {
      <section class="panel-pop" animate.leave="anim-pop-out" role="dialog" aria-label="Notifications" data-testid="notification-panel">
        <header class="pop-header">
          <h2>Notifications</h2>
          @if (seenNow().size > 0) {
            <span class="new-count" data-testid="new-count">{{ seenNow().size }} new</span>
          }
        </header>

        @if (service.notifications().length) {
          <ul class="list">
            @for (n of service.notifications(); track n.id) {
              <li>
                <button
                  type="button"
                  class="item"
                  [class.unread]="isNew(n)"
                  [attr.data-kind]="n.kind"
                  (click)="openNotification(n)"
                  data-testid="notification-item"
                >
                  <span class="kind-icon"><i class="fas" [ngClass]="icon(n)"></i></span>
                  <span class="text">
                    <span class="title">{{ n.title }}</span>
                    <span class="message">{{ n.message }}</span>
                    <span class="time">{{ relativeTime(n.createdAt) }}</span>
                  </span>
                  @if (isNew(n)) {
                    <span class="dot" aria-label="New"></span>
                  }
                </button>
              </li>
            }
          </ul>
          <footer class="pop-footer">
            <button type="button" class="link-btn muted" (click)="service.clear()" data-testid="clear-notifications">Clear all</button>
            <button type="button" class="link-btn" (click)="viewAll()" data-testid="view-all-notifications">
              View all <i class="fas fa-arrow-right" aria-hidden="true"></i>
            </button>
          </footer>
        } @else {
          <div class="empty" data-testid="notifications-empty">
            <i class="fas fa-bell-slash"></i>
            <p>You're all caught up</p>
            <small>Order updates and exports will appear here.</small>
          </div>
          <footer class="pop-footer">
            <button type="button" class="link-btn" (click)="viewAll()" data-testid="view-all-notifications">
              View notification history <i class="fas fa-arrow-right" aria-hidden="true"></i>
            </button>
          </footer>
        }
      </section>
    }

    <!-- Announce new notifications to screen readers -->
    <span class="sr-only" aria-live="polite">{{ service.latest()?.title }}</span>
  `,
  styles: [`
    :host {
      position: relative;
      display: inline-flex;
    }

    /* Matches the navbar's .action-icon buttons (their styles are scoped to the navbar) */
    .bell {
      position: relative;
      width: 38px;
      height: 38px;
      padding: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      border: 1px solid transparent;
      border-radius: var(--r-md);
      background: transparent;
      color: var(--ink-500);
      font-size: 15px;
      cursor: pointer;
      transition: background-color var(--t-fast), color var(--t-fast), border-color var(--t-fast);
    }

    .bell:hover,
    .bell[aria-expanded='true'] {
      background: var(--surface-sunken);
      color: var(--ink-900);
    }

    .bell.has-unread i {
      color: var(--ink-900);
    }

    .badge {
      position: absolute;
      top: 3px;
      right: 2px;
      min-width: 17px;
      height: 17px;
      padding: 0 4px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      border: 2px solid var(--surface);
      border-radius: var(--r-pill);
      background: var(--negative);
      color: #ffffff;
      font-size: 10px;
      font-weight: 700;
      line-height: 1;
      font-variant-numeric: tabular-nums;
    }

    .panel-pop {
      position: absolute;
      top: calc(100% + 10px);
      right: 0;
      z-index: var(--z-dropdown);
      width: 380px;
      max-width: calc(100vw - 24px);
      background: var(--surface);
      border: 1px solid var(--line);
      border-radius: var(--r-lg);
      box-shadow: var(--shadow-pop);
      overflow: hidden;
      animation: rise-in 180ms var(--ease);
    }

    .pop-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 14px 16px;
      border-bottom: 1px solid var(--line);
    }

    .pop-header h2 {
      margin: 0;
      font-family: var(--font-display);
      font-size: 15px;
      font-weight: 600;
    }

    .new-count {
      padding: 2px 8px;
      border-radius: var(--r-pill);
      background: var(--brand-soft);
      color: var(--brand-strong);
      font-size: 11.5px;
      font-weight: 700;
    }

    .link-btn {
      border: 0;
      background: none;
      color: var(--brand-strong);
      font-size: 12.5px;
      font-weight: 600;
      cursor: pointer;
      padding: 4px 6px;
      border-radius: 6px;
    }

    .link-btn:hover {
      background: var(--surface-sunken);
    }

    .link-btn.muted {
      color: var(--ink-500);
    }

    .list {
      max-height: 400px;
      overflow-y: auto;
      margin: 0;
      padding: 6px;
      list-style: none;
    }

    .item {
      width: 100%;
      display: flex;
      align-items: flex-start;
      gap: 12px;
      padding: 10px;
      border: 0;
      border-radius: var(--r-md);
      background: transparent;
      text-align: left;
      color: inherit;
      cursor: pointer;
    }

    .item:hover {
      background: var(--surface-sunken);
    }

    .item.unread {
      background: var(--brand-soft);
    }

    .kind-icon {
      width: 34px;
      height: 34px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      border-radius: 10px;
      background: var(--info-soft);
      color: var(--info);
      font-size: 14px;
    }

    [data-kind='order-filled'] .kind-icon,
    [data-kind='user-registered'] .kind-icon,
    [data-kind='welcome'] .kind-icon { background: var(--positive-soft); color: var(--positive); }
    [data-kind='order-rejected'] .kind-icon { background: var(--negative-soft); color: var(--negative); }
    [data-kind='order-cancelled'] .kind-icon { background: var(--neutral-soft); color: var(--neutral); }
    [data-kind='report'] .kind-icon { background: var(--violet-soft); color: var(--violet); }
    [data-kind='price-alert'] .kind-icon { background: var(--info-soft); color: var(--info); }
    [data-kind='strategy-created'] .kind-icon { background: var(--brand-soft); color: var(--brand); }

    .text {
      flex: 1;
      min-width: 0;
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .title {
      font-size: 13.5px;
      font-weight: 600;
      color: var(--ink-900);
    }

    .message {
      font-size: 13px;
      line-height: 1.4;
      color: var(--ink-700);
    }

    .time {
      font-size: 11.5px;
      color: var(--ink-500);
    }

    .dot {
      width: 8px;
      height: 8px;
      margin-top: 6px;
      flex-shrink: 0;
      border-radius: 50%;
      background: var(--brand);
    }

    .pop-footer {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 8px;
      border-top: 1px solid var(--line);
    }

    .empty {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 6px;
      padding: 36px 16px;
      text-align: center;
      color: var(--ink-500);
    }

    .empty i {
      font-size: 22px;
      color: var(--ink-400);
    }

    .empty p {
      margin: 0;
      font-weight: 600;
      color: var(--ink-900);
    }
  `],
})
export class NotificationBellComponent {
  readonly service = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly auth = inject(CoreAuthService, { optional: true });
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly open = signal(false);
  /**
   * Notifications that were unread when the panel was opened. Opening the bell marks everything
   * read (the badge clears), but these keep their "new" highlight until the panel closes.
   */
  readonly seenNow = signal<ReadonlySet<string>>(new Set());

  label(): string {
    const unread = this.service.unreadCount();
    return unread ? `Notifications, ${unread} unread` : 'Notifications';
  }

  toggle(event: MouseEvent): void {
    event.stopPropagation();
    if (this.open()) {
      this.close();
    } else {
      this.openPanel();
    }
  }

  /** Opening the bell reads everything in it */
  private openPanel(): void {
    this.seenNow.set(new Set(this.service.notifications().filter((n) => !n.read).map((n) => n.id)));
    this.service.markAllRead();
    this.open.set(true);
  }

  private close(): void {
    this.open.set(false);
    this.seenNow.set(new Set());
  }

  /** Unread, or unread when this panel was opened */
  isNew(n: AppNotification): boolean {
    return !n.read || this.seenNow().has(n.id);
  }

  /** The full history page: customers and admins each have one */
  viewAll(): void {
    this.close();
    this.router.navigate([this.auth?.isAdmin?.() ? '/admin/notifications' : '/notifications']);
  }

  openNotification(n: AppNotification): void {
    this.service.markRead(n.id);
    this.close();
    if (n.link) {
      this.router.navigate([n.link.path], { queryParams: n.link.queryParams });
    }
  }

  icon(n: AppNotification): string {
    return KIND_ICON[n.kind] ?? 'fa-bell';
  }

  relativeTime(iso: string): string {
    const then = new Date(iso).getTime();
    const seconds = Math.max(0, Math.round((Date.now() - then) / 1000));
    if (seconds < 45) return 'Just now';
    const minutes = Math.round(seconds / 60);
    if (minutes < 60) return `${minutes}m ago`;
    const hours = Math.round(minutes / 60);
    if (hours < 24) return `${hours}h ago`;
    const days = Math.round(hours / 24);
    if (days < 7) return `${days}d ago`;
    return new Date(iso).toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) {
      this.close();
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.open()) {
      this.close();
    }
  }
}
