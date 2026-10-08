import { Component, signal, HostListener, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { CoreAuthService } from '../../core/auth/core-auth.service';
import { SignInStore } from '../../features/sign-in/sign-in.store';
import { AccountsService } from '../../api/generated/trade';
import { ThemeService } from '../../core/services/theme.service';
import { InstrumentSearchDialogComponent } from '../../features/instruments/components/instrument-search-dialog/instrument-search-dialog.component';
import { Instrument } from '../../features/instruments/instrument.models';
import { NotificationBellComponent } from '../../core/notifications/notification-bell.component';
import { NotificationService } from '../../core/notifications/notification.service';
import { OrderStatusWatcherService } from '../../core/notifications/order-status-watcher.service';

/**
 * Navigation Bar Component
 * 
 * Top navigation bar containing:
 * - App logo/title
 * - Current date and time (real-time update)
 * - User profile (name and account) with a Logout button beside it
 */
@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [CommonModule, RouterModule, InstrumentSearchDialogComponent, NotificationBellComponent],
  templateUrl: './navbar.component.html',
  styleUrls: ['./navbar.component.css'],
})
export class NavbarComponent {
  readonly themeService = inject(ThemeService);
  private readonly notifications = inject(NotificationService);
  private readonly orderWatcher = inject(OrderStatusWatcherService);
  accountDisplayId = signal<string | null>(null);
  readonly searchOpen = signal(false);
  private accountLookupSubscription: Subscription | null = null;

  constructor(
    public authService: CoreAuthService,
    private router: Router,
    private signInStore: SignInStore,
    private accountsService: AccountsService
  ) {}

  ngOnInit(): void {
    this.updateAccountDisplayId();
    // First sign-in on this browser: a one-time hello
    this.notifications.welcomeIfFirstVisit();
  }

  ngOnDestroy(): void {
    this.accountLookupSubscription?.unsubscribe();
  }

  /** Instrument search leads to the order ticket, a customer-only page */
  /** Customers and admins each have their own Preferences page */
  preferencesLink(): string {
    return this.authService.isAdmin() ? '/admin/preferences' : '/settings';
  }

  canSearch(): boolean {
    return !this.authService.isAdmin();
  }

  openSearch(): void {
    this.searchOpen.set(true);
  }

  closeSearch(): void {
    this.searchOpen.set(false);
  }

  /** Open the trade view: instrument details + chart with the order ticket alongside */
  openInstrument(instrument: Instrument): void {
    this.searchOpen.set(false);
    this.router.navigate(['/order-ticket'], { queryParams: { symbol: instrument.symbol } });
  }

  /** Ctrl/⌘+K or "/" opens instrument search (not while typing in a field) */
  @HostListener('document:keydown', ['$event'])
  onShortcut(event: KeyboardEvent): void {
    if (!this.canSearch() || this.searchOpen()) {
      return;
    }
    const target = event.target as HTMLElement | null;
    const typing = !!target && (target.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName));
    const isPalette = (event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k';
    const isSlash = event.key === '/' && !typing;
    if (isPalette || isSlash) {
      event.preventDefault();
      this.openSearch();
    }
  }

  logout(): void {
    this.orderWatcher.stop();
    this.signInStore.logout();
    this.router.navigateByUrl('/sign-in');
  }

  private updateAccountDisplayId(): void {
    const numericAccountId = this.authService.currentUser()?.accountId;

    if (!numericAccountId) {
      this.accountDisplayId.set(null);
      return;
    }

    this.accountLookupSubscription?.unsubscribe();
    this.accountLookupSubscription = this.accountsService.getAccount(numericAccountId).subscribe({
      next: (account) => {
        this.accountDisplayId.set(account.accountId || null);
      },
      error: () => {
        this.accountDisplayId.set(null);
      },
    });
  }
}
