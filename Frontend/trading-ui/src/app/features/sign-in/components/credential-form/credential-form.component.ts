import {
  Component,
  EventEmitter,
  Output,
  input,
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
 * Inputs:
 * - disabled: whether the form is disabled (during sign-in)
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

  // Input to disable the form during loading
  isDisabled = input<boolean>(false);

  username = signal<string>('');
  password = signal<string>('');

  // Track whether fields have been touched/focused
  usernameTouched = signal<boolean>(false);
  passwordTouched = signal<boolean>(false);
  formSubmitted = signal<boolean>(false);

  // Validation computed signals
  usernameErrors = computed(() => {
    const value = this.username();
    const errors: string[] = [];

    // Only show errors if field was touched or form was submitted
    if (!this.usernameTouched() && !this.formSubmitted()) {
      return errors;
    }

    if (!value) {
      errors.push('Username is required');
    }

    return errors;
  });

  passwordErrors = computed(() => {
    const value = this.password();
    const errors: string[] = [];

    // Only show errors if field was touched or form was submitted
    if (!this.passwordTouched() && !this.formSubmitted()) {
      return errors;
    }

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

  // Form is disabled during sign-in
  isFormDisabled = computed(() => this.isDisabled() || this.hasErrors());

  constructor() {}

  /**
   * Mark username field as touched
   */
  onUsernameBlur(): void {
    this.usernameTouched.set(true);
  }

  /**
   * Mark password field as touched
   */
  onPasswordBlur(): void {
    this.passwordTouched.set(true);
  }

  /**
   * Handle form submission
   */
  onSubmit(): void {
    this.formSubmitted.set(true);
    this.usernameTouched.set(true);
    this.passwordTouched.set(true);

    if (!this.isValid() || this.isDisabled()) {
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
    this.usernameTouched.set(false);
    this.passwordTouched.set(false);
    this.formSubmitted.set(false);
  }
}
