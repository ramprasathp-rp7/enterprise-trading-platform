import { Component, OnInit, OnDestroy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { Subject, interval } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { CoreAuthService } from '../../core/auth/core-auth.service';

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
export class NavbarComponent implements OnInit, OnDestroy {
  currentDateTime = signal<string>('');
  isProfileMenuOpen = false;
  private destroy$ = new Subject<void>();

  constructor(
    public authService: CoreAuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.updateDateTime();
    // Update time every second
    interval(1000)
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.updateDateTime());
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private updateDateTime(): void {
    const now = new Date();
    const options: Intl.DateTimeFormatOptions = {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false
    };
    this.currentDateTime.set(now.toLocaleDateString('en-US', options));
  }

  toggleProfileMenu(): void {
    this.isProfileMenuOpen = !this.isProfileMenuOpen;
  }

  closeProfileMenu(): void {
    this.isProfileMenuOpen = false;
  }

  async logout(): Promise<void> {
    // Confirm logout action
    if (!confirm('Are you sure you want to logout?')) {
      return;
    }
    
    this.closeProfileMenu();
    
    try {
      // Clear authentication tokens and state immediately
      this.authService.clearTokens();
      
      // Give signals time to update
      await new Promise(resolve => setTimeout(resolve, 100));
      
      // Use window.location for a hard reload to ensure guard re-evaluation
      // This is more reliable than router.navigate for logout
      window.location.href = '/sign-in';
    } catch (error) {
      console.error('Logout error:', error);
      // Fallback to hard redirect
      window.location.href = '/sign-in';
    }
  }
}
