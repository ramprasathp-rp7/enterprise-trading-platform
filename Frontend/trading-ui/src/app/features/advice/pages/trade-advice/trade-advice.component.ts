import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ReasonResponse, SignalResponse, TradeAdviceResponse, TradeAdviceService } from '../../../../api/generated/advice';
import { CoreAuthService } from '../../../../core/auth/core-auth.service';
import { currencySymbol } from '../../../../shared/currency';

export type AdviceFilter = 'all' | 'held' | 'watched';

/**
 * Trade Advice: BUY / SELL / HOLD signals with the reasoning behind each, for every instrument the
 * customer holds or watches (GET /accounts/{id}/advice). Instruments without enough price history
 * are listed separately with the reason.
 */
@Component({
  selector: 'app-trade-advice',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './trade-advice.component.html',
  styleUrls: ['./trade-advice.component.css'],
})
export class TradeAdviceComponent implements OnInit {
  private readonly api = inject(TradeAdviceService);
  private readonly authService = inject(CoreAuthService);

  readonly advice = signal<TradeAdviceResponse | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly filter = signal<AdviceFilter>('all');

  private readonly inFilter = computed(() => {
    const all = this.advice()?.signals ?? [];
    switch (this.filter()) {
      case 'held':
        return all.filter((s) => s.held);
      case 'watched':
        return all.filter((s) => s.watched);
      default:
        return all;
    }
  });

  /** Instruments with a signal (server order: SELL, BUY, HOLD) */
  readonly withSignal = computed(() => this.inFilter().filter((s) => !!s.signal));
  /** Instruments without enough data */
  readonly withoutSignal = computed(() => this.inFilter().filter((s) => !s.signal));

  readonly counts = computed(() => {
    const signals = this.advice()?.signals ?? [];
    return {
      buy: signals.filter((s) => s.signal === SignalResponse.SignalEnum.Buy).length,
      sell: signals.filter((s) => s.signal === SignalResponse.SignalEnum.Sell).length,
      hold: signals.filter((s) => s.signal === SignalResponse.SignalEnum.Hold).length,
      held: signals.filter((s) => s.held).length,
      watched: signals.filter((s) => s.watched).length,
      all: signals.length,
    };
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    const accountId = this.authService.currentUser()?.accountId;
    if (!accountId) {
      this.error.set('No account found for this session. Please sign in again.');
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.api.getTradeAdvice(accountId).subscribe({
      next: (advice) => {
        this.advice.set(advice);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(
          err instanceof HttpErrorResponse && err.status === 403
            ? 'You can only see advice for your own account.'
            : 'Could not load trade advice. Please try again.',
        );
      },
    });
  }

  signalClass(s: SignalResponse): string {
    return (s.signal ?? 'none').toLowerCase();
  }

  reasonIcon(r: ReasonResponse): string {
    return r.impact === 'POSITIVE' ? 'fa-arrow-trend-up' : r.impact === 'NEGATIVE' ? 'fa-arrow-trend-down' : 'fa-minus';
  }

  price(s: SignalResponse): string {
    const p = s.indicators.price;
    return p == null ? '—' : `${currencySymbol(s.currency)}${p.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 4 })}`;
  }

  signed(value: number | null | undefined, digits = 1): string {
    if (value == null) {
      return '—';
    }
    const text = Math.abs(value).toFixed(digits);
    return value > 0 ? `+${text}%` : value < 0 ? `−${text}%` : `${text}%`;
  }

  tone(value: number | null | undefined): string {
    return value == null || value === 0 ? '' : value > 0 ? 'up' : 'down';
  }
}
