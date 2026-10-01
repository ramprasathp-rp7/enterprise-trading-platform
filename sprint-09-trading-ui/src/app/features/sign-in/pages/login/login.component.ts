import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, ActivatedRoute } from '@angular/router';
import { CredentialFormComponent } from '../../components/credential-form/credential-form.component';
import { SignInErrorMessageComponent } from '../../components/sign-in-error-message/sign-in-error-message.component';
import { SignInStore } from '../../sign-in.store';
import { LoginCredentials } from '../../../../core/auth/auth.models';

/**
 * Login Page Component
 * 
 * Main sign-in page that composes:
 * - Credential form for username/password input
 * - Error message display
 * - Loading state during sign-in
 * 
 * Redirects to dashboard on successful login or to returnUrl query param.
 */
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, CredentialFormComponent, SignInErrorMessageComponent],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css'],
})
export class LoginComponent {
  private store = inject(SignInStore);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private readonly defaultReturnUrl = '/dashboard';

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
      const returnUrl = this.getSafeReturnUrl(this.route.snapshot.queryParams['returnUrl']);
      this.router.navigateByUrl(returnUrl);
    }
  }

  /**
   * Sign out the user
   */
  onSignOut(): void {
    this.store.logout();
  }

  private getSafeReturnUrl(candidate: unknown): string {
    if (
      typeof candidate !== 'string' ||
      candidate.length === 0 ||
      !candidate.startsWith('/') ||
      candidate.startsWith('//')
    ) {
      return this.defaultReturnUrl;
    }

    try {
      const parsed = new URL(candidate, window.location.origin);
      if (parsed.origin !== window.location.origin || !parsed.pathname.startsWith('/')) {
        return this.defaultReturnUrl;
      }

      return `${parsed.pathname}${parsed.search}${parsed.hash}`;
    } catch {
      return this.defaultReturnUrl;
    }
  }
}

