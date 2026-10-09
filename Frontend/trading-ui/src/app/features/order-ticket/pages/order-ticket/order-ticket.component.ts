import { Component, DestroyRef, OnInit, computed, inject, signal, effect, untracked } from '@angular/core';
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
import { NotificationService } from '../../../../core/notifications/notification.service';
import { OrderStatusWatcherService } from '../../../../core/notifications/order-status-watcher.service';
import { orderPlacedNotice } from '../../../../core/notifications/order-messages';
import { Instrument } from '../../../instruments/instrument.models';
import { InstrumentSearchComponent } from '../../../instruments/components/instrument-search/instrument-search.component';
import { InstrumentPanelComponent } from '../../../instruments/components/instrument-panel/instrument-panel.component';
import { InstrumentCatalogService } from '../../../instruments/services/instrument-catalog.service';
import { AccountSettingsStore } from '../../../../core/settings/account-settings.store';
import { LiveQuoteStore } from '../../../../core/market-data/live-quote.store';
import { formatMoney } from '../../../../shared/currency';

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
  private notifications = inject(NotificationService);
  private orderWatcher = inject(OrderStatusWatcherService);
  private catalog = inject(InstrumentCatalogService);
  private liveQuotes = inject(LiveQuoteStore);
  private settings = inject(AccountSettingsStore);

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
      return ['Try a symbol like AAPL.'];
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
      return ['Whole number above 0.'];
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
      return ['Above 0, max 2 decimals.'];
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

  /** The instrument's currency: the limit price and notional are in it */
  readonly instrumentCurrency = computed(() => this.catalog.find(this.symbol().trim().toUpperCase())?.currency ?? null);
  /** The account's currency, when it differs from the instrument's (cash is debited in it) */
  readonly foreignAccountCurrency = computed(() => {
    const account = this.store.accountCurrency?.() ?? null;
    const instrument = this.instrumentCurrency();
    return account && instrument && account !== instrument ? account : null;
  });
  /** FX: pairs that price the instrument's currency in the account's: direct, then inverse */
  private readonly fxSymbols = computed(() => {
    const account = this.foreignAccountCurrency();
    const instrument = this.instrumentCurrency();
    return account && instrument ? [`FX:${instrument}${account}`, `FX:${account}${instrument}`] : [];
  });
  /** Today's instrument-to-account rate from the live quote cache; the order settles at the rate at fill */
  readonly estimatedFxRate = computed(() => {
    const [direct, inverse] = this.fxSymbols();
    if (!direct) {
      return null;
    }
    const directPrice = this.liveQuotes.quote(direct)?.price;
    if (directPrice) {
      return directPrice;
    }
    const inversePrice = this.liveQuotes.quote(inverse)?.price;
    return inversePrice ? 1 / inversePrice : null;
  });
  /** Estimated cash amount in the account's currency, at today's rate */
  readonly estimatedNotionalAccount = computed(() => {
    const notional = this.estimatedNotional();
    const rate = this.estimatedFxRate();
    return notional !== null && rate !== null ? notional * rate : null;
  });

  readonly formatMoney = formatMoney;

  /** The order budget from the Preferences page, in the account's currency (null = none) */
  readonly orderBudget = this.settings.orderBudget;
  /** The currency the budget is in: the account's (falls back to the instrument's while it loads) */
  readonly budgetCurrency = computed(() => this.store.accountCurrency?.() ?? this.instrumentCurrency() ?? 'USD');
  /** The order's estimated cost in the budget's currency */
  readonly budgetNotional = computed(() =>
    this.foreignAccountCurrency() ? this.estimatedNotionalAccount() : this.estimatedNotional(),
  );
  /** True when the order would cost more than the budget: warn, never block */
  readonly overBudget = computed(() => {
    const budget = this.orderBudget();
    const notional = this.budgetNotional();
    return budget !== null && notional !== null && notional > budget;
  });

  constructor() {
    // Keep today's FX rate for the selected instrument fresh while the ticket is open
    this.liveQuotes.track(() => this.fxSymbols());

    // Prefill the default quantity from the Preferences page, once, if the field is untouched
    effect(() => {
      const preferred = this.settings.defaultQuantity();
      untracked(() => {
        if (preferred && !this.quantity().trim() && !this.quantityTouched()) {
          this.quantity.set(String(preferred));
        }
      });
    });
  }

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
      this.notifications.notify({
        ...orderPlacedNotice(result),
        link: { path: `/order-confirmation/${result.orderId}` },
        dedupeKey: `order-created:${result.orderId}`,
      });
      this.orderWatcher.track(result.orderId, result.status);
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
