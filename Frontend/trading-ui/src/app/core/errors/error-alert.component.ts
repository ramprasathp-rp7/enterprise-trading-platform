import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ProcessedError } from './error-response-handler';

/**
 * Error display component that renders user-friendly error messages.
 *
 * This component receives a ProcessedError (from error handler) and displays
 * an appropriate message to the trader based on the error code and type.
 *
 * Usage in parent component:
 *   <app-error-alert [error]="errorFromHandler"></app-error-alert>
 *
 * Example in error handler:
 *   catch(error => {
 *     this.error = errorResponseHandler.processError(error.status, error.error);
 *   })
 */
@Component({
  selector: 'app-error-alert',
  templateUrl: './error-alert.component.html',
  styleUrl: './error-alert.component.css',
  imports: [CommonModule],
  standalone: true,
})
export class ErrorAlertComponent {
  @Input() error: ProcessedError | null = null;

  @Output() retry = new EventEmitter<void>();
  @Output() signIn = new EventEmitter<void>();
  @Output() dismiss = new EventEmitter<void>();

  /**
   * Event emitted when user clicks retry
   */
  onRetry(): void {
    console.log('Retrying...');
    this.retry.emit();
  }

  /**
   * Event emitted when user clicks sign in
   */
  onSignIn(): void {
    console.log('Navigating to sign in...');
    this.signIn.emit();
  }

  /**
   * Dismiss the error
   */
  onDismiss(): void {
    this.dismiss.emit();
    this.error = null;
  }

  /**
   * Determine CSS class based on error type
   */
  getErrorClass(): string {
    if (!this.error) return '';

    if (this.error.isNetworkError) {
      return 'network-error';
    }

    if (this.error.isUnknownCode) {
      return 'unknown-error';
    }

    return 'api-error';
  }

  /**
   * Check if we should show sign-in button (AUTH-401 error)
   */
  shouldShowSignIn(): boolean {
    if (!this.error) return false;
    return this.error.code === 'AUTH-401';
  }

  /**
   * Check if we should show retry button (all errors except AUTH-401)
   */
  shouldShowRetry(): boolean {
    if (!this.error) return false;
    return this.error.code !== 'AUTH-401';
  }
}
