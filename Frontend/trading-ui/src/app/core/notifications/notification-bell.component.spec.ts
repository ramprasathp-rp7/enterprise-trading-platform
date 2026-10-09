import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { Router, provideRouter } from '@angular/router';
import { NotificationBellComponent } from './notification-bell.component';
import { NotificationService } from './notification.service';
import { CoreAuthService } from '../auth/core-auth.service';

describe('NotificationBellComponent', () => {
  let fixture: ComponentFixture<NotificationBellComponent>;
  let component: NotificationBellComponent;
  let service: NotificationService;
  let navigate: ReturnType<typeof vi.fn>;

  const q = (sel: string) => fixture.nativeElement.querySelector(sel);
  const qa = (sel: string): HTMLElement[] => Array.from(fixture.nativeElement.querySelectorAll(sel));
  const openPanel = () => {
    q('[data-testid="notification-bell"]').click();
    fixture.detectChanges();
  };

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [NotificationBellComponent],
      providers: [
        provideRouter([]),
        { provide: CoreAuthService, useValue: { currentUser: signal({ username: 'rkumar', accountId: 7 }), isAdmin: () => false } },
      ],
    }).compileComponents();
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true) as any;
    service = TestBed.inject(NotificationService);
    fixture = TestBed.createComponent(NotificationBellComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('shows no badge and an empty panel when there is nothing new', () => {
    expect(q('[data-testid="notification-badge"]')).toBeNull();
    expect(q('[data-testid="notification-bell"]').getAttribute('aria-label')).toBe('Notifications');

    openPanel();
    expect(q('[data-testid="notifications-empty"]').textContent).toContain("You're all caught up");
  });

  it('shows the unread count, capped at 9+', () => {
    service.notify({ kind: 'report', title: 'A', message: '' });
    fixture.detectChanges();
    expect(q('[data-testid="notification-badge"]').textContent.trim()).toBe('1');
    expect(q('[data-testid="notification-bell"]').getAttribute('aria-label')).toBe('Notifications, 1 unread');

    for (let i = 0; i < 10; i++) service.notify({ kind: 'report', title: `n${i}`, message: '' });
    fixture.detectChanges();
    expect(q('[data-testid="notification-badge"]').textContent.trim()).toBe('9+');
  });

  it('lists notifications with type, message and time; opening one marks it read and navigates', () => {
    service.notify({ kind: 'order-created', title: 'Order placed', message: 'BUY 10 AAPL', link: { path: '/order-confirmation/o1' } });
    openPanel();

    const items = qa('[data-testid="notification-item"]');
    expect(items.length).toBe(1);
    expect(items[0].getAttribute('data-kind')).toBe('order-created');
    expect(items[0].textContent).toContain('Order placed');
    expect(items[0].textContent).toContain('BUY 10 AAPL');
    expect(items[0].textContent).toContain('Just now');

    items[0].click();
    fixture.detectChanges();

    expect(service.unreadCount()).toBe(0);
    expect(navigate).toHaveBeenCalledWith(['/order-confirmation/o1'], { queryParams: undefined });
    expect(component.open()).toBe(false);
  });

  it('reads everything when the bell is opened, keeping the new ones highlighted until it closes', () => {
    service.notify({ kind: 'report', title: 'A', message: '' });
    service.notify({ kind: 'report', title: 'B', message: '' });
    expect(service.unreadCount()).toBe(2);

    openPanel();

    // read as soon as the bell opens: badge gone, saved as read
    expect(service.unreadCount()).toBe(0);
    expect(service.notifications().every((n) => n.read)).toBe(true);
    expect(q('[data-testid="notification-badge"]')).toBeNull();
    // still shown as new while the panel is open
    expect(q('[data-testid="new-count"]').textContent).toContain('2 new');
    expect(qa('[data-testid="notification-item"]').every((el) => el.classList.contains('unread'))).toBe(true);
    expect(q('[data-testid="mark-all-read"]')).toBeNull();

    // closing and reopening: nothing new any more
    document.body.click();
    fixture.detectChanges();
    openPanel();
    expect(q('[data-testid="new-count"]')).toBeNull();
    expect(qa('[data-testid="notification-item"]').some((el) => el.classList.contains('unread'))).toBe(false);

    q('[data-testid="clear-notifications"]').click();
    fixture.detectChanges();
    expect(q('[data-testid="notifications-empty"]')).not.toBeNull();
  });

  it('closes on an outside click or Escape, but not on inside clicks', () => {
    openPanel();
    q('[data-testid="notification-panel"]').click();
    expect(component.open()).toBe(true);

    document.body.click();
    expect(component.open()).toBe(false);

    openPanel();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(component.open()).toBe(false);
  });

  it('does not navigate for notifications without a link', () => {
    service.notify({ kind: 'report', title: 'A', message: '' });
    openPanel();

    qa('[data-testid="notification-item"]')[0].click();

    expect(navigate).not.toHaveBeenCalled();
  });

  it('formats relative times', () => {
    const ago = (ms: number) => new Date(Date.now() - ms).toISOString();
    expect(component.relativeTime(ago(10_000))).toBe('Just now');
    expect(component.relativeTime(ago(5 * 60_000))).toBe('5m ago');
    expect(component.relativeTime(ago(3 * 3_600_000))).toBe('3h ago');
    expect(component.relativeTime(ago(2 * 86_400_000))).toBe('2d ago');
    expect(component.relativeTime(ago(30 * 86_400_000))).toMatch(/[A-Z][a-z]{2} \d+/);
  });

  it('has an icon for every kind', () => {
    for (const kind of ['welcome', 'user-registered', 'order-created', 'order-filled', 'order-rejected', 'order-cancelled', 'report'] as const) {
      expect(component.icon({ kind } as any)).toMatch(/^fa-/);
    }
  });

  it('links to the full notification history', () => {
    openPanel();
    q('[data-testid="view-all-notifications"]').click();
    fixture.detectChanges();

    expect(navigate).toHaveBeenCalledWith(['/notifications']);
    expect(q('[data-testid="notification-panel"]')).toBeNull();
  });
});
