import { Component, input, computed } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * Sign-In Success Message Component
 * 
 * Displays a success message when login is successful.
 * Shows briefly before redirect to dashboard.
 */
@Component({
  selector: 'app-sign-in-success-message',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './sign-in-success-message.component.html',
  styleUrls: ['./sign-in-success-message.component.css'],
})
export class SignInSuccessMessageComponent {
  /**
   * Whether to show the success message
   */
  isVisible = input<boolean>(false);

  /**
   * Computed property: whether the success message is visible
   */
  show = computed(() => this.isVisible());
}
