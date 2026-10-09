import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, ActivatedRoute } from '@angular/router';
import { CredentialFormComponent } from '../../components/credential-form/credential-form.component';
import { SignInErrorMessageComponent } from '../../components/sign-in-error-message/sign-in-error-message.component';
import { SignInSuccessMessageComponent } from '../../components/sign-in-success-message/sign-in-success-message.component';
import { SignInStore } from '../../sign-in.store';
import { LoginCredentials } from '../../../../core/auth/auth.models';
import { ThemeService } from '../../../../core/services/theme.service';
import { CoreAuthService } from '../../../../core/auth/core-auth.service';

/**
 * Login Page Component
 * 
 * Main sign-in page that composes:
 * - Credential form for username/password input
 * - Error message display
 * - Success message display
 * - Loading state during sign-in
 * 
 * Redirects to dashboard on successful login or to returnUrl query param.
 */
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, CredentialFormComponent, SignInErrorMessageComponent, SignInSuccessMessageComponent],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css'],
})
export class LoginComponent {
  private store = inject(SignInStore);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private authService = inject(CoreAuthService);
  readonly themeService = inject(ThemeService);

  // Track whether login was successful
  isLoginSuccessful = signal<boolean>(false);

  // Expose store signals to template
  isLoading = this.store.isLoading;
  error = this.store.error;
  errorCode = this.store.errorCode;
  errorMessage = this.store.errorMessage;

  constructor() {}

  /**
   * Handle form submission
   */
  async onSubmit(credentials: LoginCredentials): Promise<void> {
    const success = await this.store.login(credentials.username, credentials.password);

    if (success) {
      // Show success message for a brief moment before redirecting
      this.isLoginSuccessful.set(true);
      
      // Wait 1 second to let user see the success message
      await new Promise(resolve => setTimeout(resolve, 1000));
      
      // Check for explicit returnUrl first
      const returnUrl = this.route.snapshot.queryParams['returnUrl'];
      const shouldUseReturnUrl =
        returnUrl &&
        this.isSafeUrl(returnUrl) &&
        !(this.authService.isAdmin() && this.isCustomerDashboardReturnUrl(returnUrl));

      if (shouldUseReturnUrl) {
        this.router.navigateByUrl(returnUrl);
      } else {
        // Otherwise use role-based dashboard route
        const dashboardRoute = this.authService.getDashboardRoute();
        this.router.navigateByUrl(dashboardRoute);
      }
    }
  }

  /**
   * Sign out the user
   */
  onSignOut(): void {
    this.store.logout();
  }

  /**
   * Check if a URL is safe to navigate to (same origin, absolute path)
   */
  private isSafeUrl(candidate: unknown): boolean {
    if (
      typeof candidate !== 'string' ||
      candidate.length === 0 ||
      !candidate.startsWith('/') ||
      candidate.startsWith('//')
    ) {
      return false;
    }

    try {
      const parsed = new URL(candidate, window.location.origin);
      return parsed.origin === window.location.origin && parsed.pathname.startsWith('/');
    } catch {
      return false;
    }
  }

  private isCustomerDashboardReturnUrl(candidate: string): boolean {
    return candidate === '/dashboard' || candidate.startsWith('/dashboard?') || candidate.startsWith('/dashboard#');
  }
}

