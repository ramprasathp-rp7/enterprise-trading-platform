import { Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { OrderSide } from '../../../../api/generated/trade';
import { OrderTicketStore } from '../../order-ticket.store';
import {
  isValidLimitPrice,
  isValidTradeableSymbol,
  isValidWholeQuantity,
} from '../../order-ticket.models';
import { ErrorAlertComponent } from '../../../../core/errors/error-alert.component';
import { ProcessedError } from '../../../../core/errors/error-response-handler';

@Component({
  selector: 'app-order-ticket',
  standalone: true,
  imports: [CommonModule, FormsModule, ErrorAlertComponent],
  templateUrl: './order-ticket.component.html',
  styleUrls: ['./order-ticket.component.css']
})
export class OrderTicketComponent {
  private store = inject(OrderTicketStore);

  readonly symbol = signal<string>('');
  readonly side = signal<OrderSide>(OrderSide.Buy);
  readonly quantity = signal<string>('');
  readonly price = signal<string>('');
  readonly sideOptions = [OrderSide.Buy, OrderSide.Sell] as const;

  readonly accountId = this.store.accountId;
  readonly isSubmitting = this.store.isSubmitting;
  readonly submissionResult = this.store.submissionResult;
  readonly errorCode = this.store.errorCode;
  readonly errorMessage = this.store.errorMessage;

  readonly symbolErrors = computed(() => {
    const value = this.symbol().trim().toUpperCase();
    if (!value) {
      return ['Symbol is required.'];
    }

    if (!isValidTradeableSymbol(value)) {
      return ['Use a tradeable symbol shape such as AAPL, INFY.NS, FX:USD/EUR or X:BTC-USD.'];
    }

    return [];
  });

  readonly quantityErrors = computed(() => {
    const value = this.quantity().trim();
    if (!value) {
      return ['Quantity is required.'];
    }

    if (!isValidWholeQuantity(value)) {
      return ['Quantity must be a whole number greater than zero.'];
    }

    return [];
  });

  readonly priceErrors = computed(() => {
    const value = this.price().trim();
    if (!value) {
      return ['Price is required.'];
    }

    if (!isValidLimitPrice(value)) {
      return ['Price must be greater than zero with at most two decimal places.'];
    }

    return [];
  });

  readonly canSubmit = computed(() => {
    return (
      this.accountId() !== null &&
      this.symbolErrors().length === 0 &&
      this.quantityErrors().length === 0 &&
      this.priceErrors().length === 0 &&
      !this.isSubmitting()
    );
  });

  /**
   * Convert error code and message to ProcessedError for error-alert component
   * This transforms the internal error state to trader-friendly display format
   */
  readonly processedError = computed(() => {
    const errorCode = this.errorCode();
    const errorMessage = this.errorMessage();

    if (!errorCode || !errorMessage) {
      return null;
    }

    return {
      code: errorCode,
      message: errorMessage,
      isNetworkError: errorCode === 'NETWORK',
      isUnknownCode: errorCode === 'UNKNOWN',
    } as ProcessedError;
  });

  async onSubmit(): Promise<void> {
    if (!this.canSubmit()) {
      return;
    }

    await this.store.submitOrder({
      symbol: this.symbol().trim().toUpperCase(),
      side: this.side(),
      quantity: Number(this.quantity().trim()),
      price: Number(this.price().trim()),
    });
  }

  /**
   * Handle retry: clear form fields and error box
   */
  onRetryOrder(): void {
    // Clear form fields for fresh start
    this.symbol.set('');
    this.quantity.set('');
    this.price.set('');
    // Clear error from store
    this.store.clearErrors();
  }

  /**
   * Handle dismiss: clear the error alert
   */
  onDismissError(): void {
    // Clear error from store
    this.store.clearErrors();
  }
}
