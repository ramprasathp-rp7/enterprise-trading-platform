import { Component, signal, HostListener, ElementRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { CoreAuthService } from '../../core/auth/core-auth.service';
import { SignInStore } from '../../features/sign-in/sign-in.store';
import { AccountsService } from '../../api/generated/trade';
import { ThemeService } from '../../core/services/theme.service';

/**
 * Navigation Bar Component
 * 
 * Top navigation bar containing:
 * - App logo/title
 * - Current date and time (real-time update)
 * - User profile menu
 * - Logout functionality
 */
@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './navbar.component.html',
  styleUrls: ['./navbar.component.css'],
})
export class NavbarComponent {
  readonly themeService = inject(ThemeService);
  accountDisplayId = signal<string | null>(null);
  isProfileMenuOpen = false;
  private accountLookupSubscription: Subscription | null = null;

  constructor(
    public authService: CoreAuthService,
    private router: Router,
    private signInStore: SignInStore,
    private elementRef: ElementRef<HTMLElement>,
    private accountsService: AccountsService
  ) {}

  ngOnInit(): void {
    this.updateAccountDisplayId();
  }

  ngOnDestroy(): void {
    this.accountLookupSubscription?.unsubscribe();
  }

  toggleProfileMenu(event?: MouseEvent): void {
    event?.stopPropagation();
    this.isProfileMenuOpen = !this.isProfileMenuOpen;
  }

  closeProfileMenu(): void {
    this.isProfileMenuOpen = false;
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.isProfileMenuOpen) {
      return;
    }

    const target = event.target as Node | null;
    if (target && !this.elementRef.nativeElement.contains(target)) {
      this.closeProfileMenu();
    }
  }

  logout(): void {
    this.closeProfileMenu();
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
