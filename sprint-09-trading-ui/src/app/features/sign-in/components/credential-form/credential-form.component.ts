import {
  Component,
  EventEmitter,
  Output,
  signal,
  computed,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LoginCredentials } from '../../../../core/auth/auth.models';

/**
 * Credential Form Component
 * 
 * Reusable login form with validation.
 * Uses standalone component with signals for reactive state.
 * 
 * Emits:
 * - submitAttempt: {username, password} when form is submitted with valid input
 */
@Component({
  selector: 'app-credential-form',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './credential-form.component.html',
  styleUrls: ['./credential-form.component.css'],
})
export class CredentialFormComponent {
  @Output() submitAttempt = new EventEmitter<LoginCredentials>();

  username = signal<string>('');
  password = signal<string>('');

  // Validation computed signals
  usernameErrors = computed(() => {
    const value = this.username();
    const errors: string[] = [];

    if (!value) {
      errors.push('Username is required');
    }

    return errors;
  });

  passwordErrors = computed(() => {
    const value = this.password();
    const errors: string[] = [];

    if (!value) {
      errors.push('Password is required');
    }

    return errors;
  });

  // Overall form validity
  isValid = computed(() => {
    return this.usernameErrors().length === 0 && this.passwordErrors().length === 0;
  });

  hasErrors = computed(() => {
    return this.usernameErrors().length > 0 || this.passwordErrors().length > 0;
  });

  constructor() {}

  /**
   * Handle form submission
   */
  onSubmit(): void {
    if (!this.isValid()) {
      return;
    }

    this.submitAttempt.emit({
      username: this.username(),
      password: this.password(),
    });
  }

  /**
   * Reset form to initial state
   */
  reset(): void {
    this.username.set('');
    this.password.set('');
  }
}
