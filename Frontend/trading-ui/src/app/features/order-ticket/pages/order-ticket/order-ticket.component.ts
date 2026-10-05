import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { OrderSide } from '../../../../api/generated/trade';
import { OrderTicketStore } from '../../order-ticket.store';
import {
  isValidLimitPrice,
  isValidTradeableSymbol,
  isValidWholeQuantity,
} from '../../order-ticket.models';
import { ProcessedError } from '../../../../core/errors/error-response-handler';
import { Instrument } from '../../../instruments/instrument.models';
import { InstrumentSearchComponent } from '../../../instruments/components/instrument-search/instrument-search.component';
import { InstrumentPanelComponent } from '../../../instruments/components/instrument-panel/instrument-panel.component';

@Component({
  selector: 'app-order-ticket',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, InstrumentSearchComponent, InstrumentPanelComponent],
  templateUrl: './order-ticket.component.html',
  styleUrls: ['./order-ticket.component.css']
})
export class OrderTicketComponent implements OnInit {
  private store = inject(OrderTicketStore);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private destroyRef = inject(DestroyRef);

  readonly symbol = signal<string>('');
  readonly side = signal<OrderSide>(OrderSide.Buy);
  readonly quantity = signal<string>('');
  readonly price = signal<string>('');
  readonly symbolTouched = signal<boolean>(false);
  readonly quantityTouched = signal<boolean>(false);
  readonly priceTouched = signal<boolean>(false);
  readonly formSubmitted = signal<boolean>(false);
  readonly sideOptions = [OrderSide.Buy, OrderSide.Sell] as const;
  /** Set when the ticket was opened from a holding (e.g. Positions → Buy/Sell) */
  readonly prefilledFrom = signal<string | null>(null);

  readonly accountId = this.store.accountId;
  readonly accountDisplayId = this.store.accountDisplayId;
  readonly isSubmitting = this.store.isSubmitting;
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

  /** Live notional (quantity x limit price) shown in the order summary rail. */
  readonly estimatedNotional = computed(() => {
    const quantity = Number(String(this.quantity() ?? '').trim());
    const price = Number(String(this.price() ?? '').trim());
    if (!Number.isFinite(quantity) || !Number.isFinite(price) || quantity <= 0 || price <= 0) {
      return null;
    }
    return quantity * price;
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

  /**
   * Prefill from query params and keep following them, e.g.
   * /order-ticket?symbol=AAPL&side=SELL&from=holding (Positions → Buy/Sell) or
   * /order-ticket?symbol=AAPL (navbar instrument search).
   */
  ngOnInit(): void {
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const symbol = (params.get('symbol') ?? '').trim().toUpperCase().slice(0, 20);
      if (symbol) {
        this.symbol.set(symbol);
        this.symbolTouched.set(true);
        // The "from your holding" hint only applies when opened from a holding
        this.prefilledFrom.set(params.get('from') === 'holding' ? symbol : null);
      }

      const side = (params.get('side') ?? '').trim().toUpperCase();
      const matchingSide = this.sideOptions.find((option) => option === side);
      if (matchingSide) {
        this.side.set(matchingSide);
      }
    });
  }

  /**
   * An instrument picked from the search bar: fill the ticket, keep the URL in
   * sync (so a reload keeps the selection) and move on to the quantity.
   */
  onInstrumentSelected(instrument: Instrument): void {
    this.symbol.set(instrument.symbol);
    this.symbolTouched.set(true);
    this.prefilledFrom.set(null);
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { symbol: instrument.symbol, from: null },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
    queueMicrotask(() => (document.getElementById('quantity') as HTMLInputElement | null)?.focus());
  }

  async onSubmit(): Promise<void> {
    this.formSubmitted.set(true);
    this.symbolTouched.set(true);
    this.quantityTouched.set(true);
    this.priceTouched.set(true);

    if (!this.canSubmit() || !this.isFormValid()) {
      return;
    }

    const placed = await this.store.submitOrder({
      symbol: this.symbol().trim().toUpperCase(),
      side: this.side(),
      quantity: Number(this.quantity().trim()),
      price: Number(this.price().trim()),
    });

    // A placed order (whatever its status) gets its own confirmation page,
    // which routes on to the Order Blotter for live status.
    const result = this.store.submissionResult();
    if (placed && result?.orderId) {
      await this.router.navigate(['/order-confirmation', result.orderId]);
    }
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

  /**
   * Instrument edits: once the trader changes or clears a prefilled symbol,
   * the "Prefilled from your … holding" hint no longer applies, so drop it.
   */
  onSymbolChange(value: string): void {
    this.symbol.set(value);
    const prefilled = this.prefilledFrom();
    if (prefilled && (value ?? '').trim().toUpperCase() !== prefilled) {
      this.prefilledFrom.set(null);
    }
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
