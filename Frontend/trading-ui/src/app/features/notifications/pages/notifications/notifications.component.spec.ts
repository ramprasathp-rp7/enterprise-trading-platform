import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { NotificationsComponent } from './notifications.component';
import { NotificationHistoryItem, OrderNotificationsService } from '../../../../api/generated/order-notifications';
import { CoreAuthService } from '../../../../core/auth/core-auth.service';
import { NotificationService } from '../../../../core/notifications/notification.service';

describe('NotificationsComponent', () => {
  let fixture: ComponentFixture<NotificationsComponent>;
  let component: NotificationsComponent;
  let history: ReturnType<typeof vi.fn>;
  let admin: boolean;

  const el = (id: string): HTMLElement | null => fixture.nativeElement.querySelector(`[data-testid="${id}"]`);

  const items = (): NotificationHistoryItem[] => [
    {
      id: 'order-12', kind: 'ORDER', occurredAt: '2026-10-09T10:00:00Z', status: 'FILLED', symbol: 'AAPL',
      channels: ['EMAIL', 'SMS', 'IN_APP'], inApp: true,
      order: { orderId: 'o-12', eventType: 'ORDER_FILLED', side: 'BUY', quantity: 10, limitPrice: 337, executedPrice: 336.71, reason: null },
      priceAlert: null,
    },
    {
      id: 'alert-7', kind: 'PRICE_ALERT', occurredAt: '2026-10-09T09:00:00Z', status: 'DELIVERED', symbol: 'AAPL',
      channels: ['IN_APP'], inApp: true, order: null,
      priceAlert: { alertId: 1, triggerId: 7, direction: 'ABOVE', threshold: 333, mode: 'ONCE', price: 336.67, quoteAt: '2026-10-09T08:00:00Z', reason: 'AAPL 336.67 >= 333 (ABOVE, ONCE, stale quote)' },
    },
    {
      id: 'order-11', kind: 'ORDER', occurredAt: '2026-10-08T10:00:00Z', status: 'REJECTED', symbol: 'INFY.NS',
      channels: ['EMAIL'], inApp: false,
      order: { orderId: 'o-11', eventType: 'ORDER_REJECTED', side: 'BUY', quantity: 40, limitPrice: 1500, executedPrice: null, reason: 'Insufficient funds' },
      priceAlert: null,
    },
  ];

  const create = async () => {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [NotificationsComponent],
      providers: [
        provideRouter([]),
        { provide: OrderNotificationsService, useValue: { getNotificationHistory: history } },
        { provide: CoreAuthService, useValue: { currentUser: signal({ id: 'u-7', username: 'rkumar', accountId: 7 }), isAdmin: () => admin } },
      ],
    }).compileComponents();
    localStorage.clear();
    const bell = TestBed.inject(NotificationService);
    TestBed.tick();
    bell.notify({ kind: 'welcome', title: 'Hello, rkumar!', message: 'Welcome.' });
    // The bell's own copy of the filled order: the server record replaces it, sharing its read state
    bell.notify({ kind: 'order-filled', title: 'Order completed', message: 'x', dedupeKey: 'order-status:o-12:FILLED' });
    fixture = TestBed.createComponent(NotificationsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  };

  beforeEach(async () => {
    admin = false;
    history = vi.fn(() => of(items()));
    await create();
  });

  it('merges the server history with the bell’s own notifications, newest first, without duplicates', () => {
    expect(history).toHaveBeenCalledWith(7, 200);
    const titles = component.entries().map((e) => e.title);
    expect(titles).toContain('Hello, rkumar!');
    expect(titles.filter((t) => t === 'Order completed').length).toBe(1);
    expect(component.entries().filter((e) => e.source === 'server').map((e) => e.id)).toEqual(['order-12', 'alert-7', 'order-11']);
    expect(component.counts()).toEqual({ all: 4, orders: 2, alerts: 1, other: 1 });
  });

  it('shows an order’s details, the channels it went to and a link to the order', () => {
    component.select(component.entries().find((e) => e.id === 'order-12')!);
    fixture.detectChanges();

    expect(el('detail-message')?.textContent).toContain('You bought 10 AAPL');
    expect(el('detail-channels')?.textContent).toContain('Email');
    expect(el('detail-channels')?.textContent).toContain('SMS');
    expect(el('detail-order')?.textContent).toContain('o-12');
    expect(el('view-order')?.getAttribute('href')).toBe('/order-confirmation/o-12');
  });

  it('shows a rejected order’s reason and that it never reached the bell', () => {
    component.select(component.entries().find((e) => e.id === 'order-11')!);
    fixture.detectChanges();

    expect(el('detail-order')?.textContent).toContain('Insufficient funds');
    expect(el('history-detail')?.textContent).toContain('Not shown in the bell');
  });

  it('shows a price alert’s condition and why it fired', () => {
    component.select(component.entries().find((e) => e.id === 'alert-7')!);
    fixture.detectChanges();

    expect(el('detail-alert')?.textContent).toContain('≥');
    expect(el('detail-alert')?.textContent).toContain('stale quote');
  });

  it('filters by kind, text and unread', () => {
    (el('category-alerts') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(component.visible().map((e) => e.id)).toEqual(['alert-7']);

    (el('category-all') as HTMLButtonElement).click();
    component.query.set('infy');
    fixture.detectChanges();
    expect(component.visible().map((e) => e.id)).toEqual(['order-11']);

    component.query.set('');
    component.unreadOnly.set(true);
    expect(component.visible().every((e) => !e.read)).toBe(true);
  });

  it('opening a notification marks the bell’s copy read', () => {
    const bell = TestBed.inject(NotificationService);
    const filled = component.entries().find((e) => e.id === 'order-12')!;
    expect(filled.read).toBe(false);

    component.select(filled);

    expect(bell.notifications().find((n) => n.dedupeKey === 'order-status:o-12:FILLED')?.read).toBe(true);
    expect(component.entries().find((e) => e.id === 'order-12')?.read).toBe(true);
  });

  it('falls back to the bell’s notifications when the history cannot load', async () => {
    history = vi.fn(() => throwError(() => new Error('down')));
    await create();

    expect(el('history-error')).not.toBeNull();
    expect(component.entries().map((e) => e.title)).toContain('Order completed');
  });

  it('shows admins the bell’s notifications only', async () => {
    admin = true;
    await create();

    expect(history).not.toHaveBeenCalledWith(0, 200);
    expect(component.entries().every((e) => e.source === 'local')).toBe(true);
  });
});
