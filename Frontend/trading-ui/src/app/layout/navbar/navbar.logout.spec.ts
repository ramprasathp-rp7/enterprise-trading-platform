import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { NavbarComponent } from './navbar.component';
import { CoreAuthService } from '../../core/auth/core-auth.service';
import { SignInStore } from '../../features/sign-in/sign-in.store';
import { AccountsService } from '../../api/generated/trade';
import { OrderStatusWatcherService } from '../../core/notifications/order-status-watcher.service';

/** A compact Logout button sits just before the profile in the navbar; there is no dropdown. */
describe('NavbarComponent – profile and logout', () => {
  let fixture: ComponentFixture<NavbarComponent>;
  let logout: ReturnType<typeof vi.fn>;
  let stopWatcher: ReturnType<typeof vi.fn>;
  let navigateByUrl: ReturnType<typeof vi.fn>;

  const q = (selector: string): HTMLElement | null => fixture.nativeElement.querySelector(selector);

  beforeEach(async () => {
    logout = vi.fn();
    stopWatcher = vi.fn();
    await TestBed.configureTestingModule({
      imports: [NavbarComponent],
      providers: [
        provideRouter([]),
        { provide: CoreAuthService, useValue: { currentUser: () => ({ accountId: 7, username: 'rkumar' }), isAdmin: () => false } },
        { provide: SignInStore, useValue: { logout } },
        { provide: AccountsService, useValue: { getAccount: vi.fn(() => of({ accountId: 'ACC-000007' })) } },
        { provide: OrderStatusWatcherService, useValue: { stop: stopWatcher, fills: () => 0 } },
      ],
    }).compileComponents();
    navigateByUrl = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true) as any;
    fixture = TestBed.createComponent(NavbarComponent);
    fixture.detectChanges();
  });

  it('shows a compact Logout button just before the profile and no dropdown', () => {
    const profile = q('[data-testid="user-profile"]')!;
    const button = q('[data-testid="logout-btn"]')!;

    expect(profile.textContent).toContain('rkumar');
    expect(button.textContent).toContain('Logout');
    expect(button.getAttribute('aria-label')).toBe('Log out');
    expect(button.nextElementSibling).toBe(profile);
    expect(q('[role="menu"]')).toBeNull();
    expect(q('[aria-haspopup="menu"]')).toBeNull();
  });

  it('logs out and returns to sign-in when Logout is clicked', () => {
    q('[data-testid="logout-btn"]')!.click();

    expect(stopWatcher).toHaveBeenCalled();
    expect(logout).toHaveBeenCalled();
    expect(navigateByUrl).toHaveBeenCalledWith('/sign-in');
  });
});
