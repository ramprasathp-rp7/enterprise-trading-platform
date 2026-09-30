import { Component, input, computed } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * Sign-In Error Message Component
 * 
 * Displays error messages with readable text.
 * Maps error codes to user-friendly messages.
 */
@Component({
  selector: 'app-sign-in-error-message',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './sign-in-error-message.component.html',
  styleUrls: ['./sign-in-error-message.component.css'],
})
export class SignInErrorMessageComponent {
  /**
   * Error message to display
   * If null or empty, nothing is rendered
   */
  message = input<string | null>(null);

  /**
   * Error code from Auth service
   * Used for testing and tracking
   */
  errorCode = input<string | null>(null);

  /**
   * Computed property: whether the error message is visible
   */
  isVisible = computed(() => !!this.message());
}
