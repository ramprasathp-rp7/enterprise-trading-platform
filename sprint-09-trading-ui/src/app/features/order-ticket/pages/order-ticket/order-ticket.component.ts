import { Component, OnDestroy, computed, effect, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { OrderSide } from '../../../../api/generated/trade';
import { OrderTicketStore } from '../../order-ticket.store';
import {
  isValidLimitPrice,
  isValidTradeableSymbol,
  isValidWholeQuantity,
} from '../../order-ticket.models';
import { ProcessedError } from '../../../../core/errors/error-response-handler';

@Component({
  selector: 'app-order-ticket',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './order-ticket.component.html',
  styleUrls: ['./order-ticket.component.css']
})
export class OrderTicketComponent implements OnDestroy {
  private store = inject(OrderTicketStore);
  private dismissTimer: ReturnType<typeof setTimeout> | null = null;
  private dismissMessageKey: string | null = null;

  readonly symbol = signal<string>('');
  readonly side = signal<OrderSide>(OrderSide.Buy);
  readonly quantity = signal<string>('');
  readonly price = signal<string>('');
  readonly symbolTouched = signal<boolean>(false);
  readonly quantityTouched = signal<boolean>(false);
  readonly priceTouched = signal<boolean>(false);
  readonly formSubmitted = signal<boolean>(false);
  readonly sideOptions = [OrderSide.Buy, OrderSide.Sell] as const;

  readonly accountId = this.store.accountId;
  readonly isSubmitting = this.store.isSubmitting;
  readonly submissionResult = this.store.submissionResult;
  readonly errorCode = this.store.errorCode;
  readonly errorMessage = this.store.errorMessage;

  readonly isSymbolValidationVisible = computed(() => this.symbolTouched() || this.formSubmitted());
  readonly isQuantityValidationVisible = computed(() => this.quantityTouched() || this.formSubmitted());
  readonly isPriceValidationVisible = computed(() => this.priceTouched() || this.formSubmitted());

  readonly symbolErrors = computed(() => {
    const value = this.symbol().trim().toUpperCase();
    if (!this.isSymbolValidationVisible()) {
      return [];
    }

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
    if (!this.isQuantityValidationVisible()) {
      return [];
    }

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
    if (!this.isPriceValidationVisible()) {
      return [];
    }

    if (!value) {
      return ['Price is required.'];
    }

    if (!isValidLimitPrice(value)) {
      return ['Price must be greater than zero with at most two decimal places.'];
    }

    return [];
  });

  readonly isFormValid = computed(() => {
    const symbol = this.symbol().trim().toUpperCase();
    const quantity = this.quantity().trim();
    const price = this.price().trim();

    return (
      !!symbol &&
      isValidTradeableSymbol(symbol) &&
      !!quantity &&
      isValidWholeQuantity(quantity) &&
      !!price &&
      isValidLimitPrice(price)
    );
  });

  readonly canSubmit = computed(() => {
    return (
      this.accountId() !== null &&
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

  constructor() {
    effect(() => {
      const error = this.processedError();
      const result = this.submissionResult();

      if (!error && !result) {
        this.clearDismissTimer();
        return;
      }

      const nextKey = error
        ? `error:${error.code ?? ''}:${error.message ?? ''}`
        : `success:${result?.orderId ?? ''}:${result?.status ?? ''}:${result?.message ?? ''}`;

      if (this.dismissTimer && this.dismissMessageKey === nextKey) {
        return;
      }

      this.clearDismissTimer();
      this.dismissMessageKey = nextKey;

      this.dismissTimer = setTimeout(() => {
        this.store.clearErrors();
        this.store.clearSubmissionResult();
        this.dismissTimer = null;
        this.dismissMessageKey = null;
      }, 5000);
    });
  }

  async onSubmit(): Promise<void> {
    this.formSubmitted.set(true);
    this.symbolTouched.set(true);
    this.quantityTouched.set(true);
    this.priceTouched.set(true);

    if (!this.canSubmit() || !this.isFormValid()) {
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
    this.symbolTouched.set(false);
    this.quantityTouched.set(false);
    this.priceTouched.set(false);
    this.formSubmitted.set(false);
    this.clearDismissTimer();
    // Clear error from store
    this.store.clearErrors();
  }

  /**
   * Handle dismiss: clear the error alert
   */
  onDismissError(): void {
    this.clearDismissTimer();
    // Clear error from store
    this.store.clearErrors();
  }

  ngOnDestroy(): void {
    this.clearDismissTimer();
  }

  private clearDismissTimer(): void {
    if (this.dismissTimer) {
      clearTimeout(this.dismissTimer);
      this.dismissTimer = null;
    }
    this.dismissMessageKey = null;
  }

  onSymbolBlur(): void {
    this.symbolTouched.set(true);
  }

  onQuantityBlur(): void {
    this.quantityTouched.set(true);
  }

  onPriceBlur(): void {
    this.priceTouched.set(true);
  }
}
