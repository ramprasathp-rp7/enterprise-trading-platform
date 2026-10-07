import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Observable, interval } from 'rxjs';
import {
  CreateStrategyRequest,
  ExecutionOutcome,
  IndicatorState,
  StrategyExecutionResponse,
  StrategyResponse,
  StrategyType,
  TradeSide,
} from '../../../../api/generated/strategy';
import { Instrument } from '../../../instruments/instrument.models';
import { InstrumentSearchComponent } from '../../../instruments/components/instrument-search/instrument-search.component';
import { MAX_ACTIVE_STRATEGIES, StrategiesStore } from '../../strategies.store';

/** How often the list (states, failures, auto-stops) is refreshed while the page is open */
export const STRATEGIES_POLL_MS = 60_000;

const OUTCOME_LABEL: Record<ExecutionOutcome, string> = {
  PLACED: 'Order placed',
  FAILED: 'Failed',
  SKIPPED_DISABLED: 'Skipped: turned off',
  SKIPPED_LIMIT: 'Skipped: limit',
  SKIPPED_NO_POSITION: 'Skipped: not held',
};

const STATE_LABEL: Record<IndicatorState, string> = {
  ABOVE: 'Short avg above long',
  BELOW: 'Short avg below long',
  INSIDE: 'Inside bands',
  ABOVE_UPPER: 'Above upper band',
  BELOW_LOWER: 'Below lower band',
};

interface ExecutionsView {
  loading: boolean;
  rows: StrategyExecutionResponse[];
  error: string | null;
}

/**
 * Automated strategies: create a rule (moving-average crossover or Bollinger bands), see each
 * strategy's current state, turn it off or on, and read what it did. Orders a strategy places go
 * through the normal order flow, so they also appear on the blotter and in positions.
 */
@Component({
  selector: 'app-strategies',
  standalone: true,
  imports: [CommonModule, RouterLink, InstrumentSearchComponent],
  templateUrl: './strategies.component.html',
  styleUrl: './strategies.component.css',
})
export class StrategiesComponent {
  readonly store = inject(StrategiesStore);
  private readonly destroyRef = inject(DestroyRef);

  readonly maxActive = MAX_ACTIVE_STRATEGIES;
  readonly types = StrategyType;
  readonly sides: readonly { value: TradeSide; label: string }[] = [
    { value: TradeSide.Both, label: 'Buy and sell' },
    { value: TradeSide.BuyOnly, label: 'Buy only' },
    { value: TradeSide.SellOnly, label: 'Sell only' },
  ];

  // ===== Create form =====
  readonly formOpen = signal(false);
  readonly instrument = signal<Instrument | null>(null);
  readonly strategyType = signal<StrategyType>(StrategyType.MaCrossover);
  readonly shortWindow = signal(5);
  readonly longWindow = signal(20);
  readonly bandWindow = signal(20);
  readonly bandK = signal(2);
  readonly tradeSide = signal<TradeSide>(TradeSide.Both);
  readonly quantity = signal(1);
  readonly maxPosition = signal(10);
  readonly maxDailyNotional = signal(1000);
  readonly maxFailures = signal(3);
  readonly consent = signal(false);
  readonly submitted = signal(false);
  readonly submitting = signal(false);
  readonly formError = signal<string | null>(null);
  readonly notice = signal<string | null>(null);

  // ===== List =====
  readonly busy = signal<ReadonlySet<number>>(new Set());
  readonly confirmDelete = signal<number | null>(null);
  readonly rowError = signal<{ id: number; message: string } | null>(null);
  readonly executions = signal<ReadonlyMap<number, ExecutionsView>>(new Map());

  /** Field problems; the form can only be submitted when this is empty */
  readonly errors = computed(() => {
    const e: Record<string, string> = {};
    if (!this.instrument()) e['instrument'] = 'Pick an instrument.';
    else if (!this.instrument()!.tradable) e['instrument'] = `${this.instrument()!.symbol} is not tradable.`;
    if (this.strategyType() === StrategyType.MaCrossover) {
      if (!isInt(this.shortWindow(), 2, 99)) e['shortWindow'] = 'Whole number from 2 to 99.';
      if (!isInt(this.longWindow(), 3, 100)) e['longWindow'] = 'Whole number from 3 to 100.';
      else if (this.longWindow() <= this.shortWindow()) e['longWindow'] = 'Must be longer than the short average.';
    } else {
      if (!isInt(this.bandWindow(), 5, 100)) e['bandWindow'] = 'Whole number from 5 to 100.';
      if (!(this.bandK() >= 0.5 && this.bandK() <= 5)) e['bandK'] = 'From 0.5 to 5.';
    }
    if (!isInt(this.quantity(), 1, 1_000_000)) e['quantity'] = 'Whole number above 0.';
    if (!isInt(this.maxPosition(), 0, 10_000_000)) e['maxPosition'] = 'Whole number, 0 or more.';
    else if (this.tradeSide() !== TradeSide.SellOnly && this.maxPosition() < this.quantity()) {
      e['maxPosition'] = 'Must allow at least one order of the quantity.';
    }
    if (!(this.maxDailyNotional() > 0)) e['maxDailyNotional'] = 'Must be above 0.';
    if (!isInt(this.maxFailures(), 1, 20)) e['maxFailures'] = 'Whole number from 1 to 20.';
    if (!this.consent()) e['consent'] = 'Confirm that orders will be placed without asking you.';
    return e;
  });

  readonly canSubmit = computed(() => Object.keys(this.errors()).length === 0 && !this.submitting());

  /** The rule in words, as it will be saved */
  readonly preview = computed(() => {
    const symbol = this.instrument()?.symbol ?? 'the instrument';
    const q = this.quantity();
    const ma = this.strategyType() === StrategyType.MaCrossover;
    const buy = ma
      ? `the ${this.shortWindow()}-point average crosses above the ${this.longWindow()}-point average`
      : `the price falls below the lower ${this.bandWindow()}-point Bollinger band (${this.bandK()} std dev)`;
    const sell = ma
      ? `the ${this.shortWindow()}-point average crosses below the ${this.longWindow()}-point average`
      : `the price rises above the upper ${this.bandWindow()}-point Bollinger band (${this.bandK()} std dev)`;
    switch (this.tradeSide()) {
      case TradeSide.BuyOnly:
        return `Buy ${q} ${symbol} when ${buy}.`;
      case TradeSide.SellOnly:
        return `Sell ${q} ${symbol} when ${sell}.`;
      default:
        return `Buy ${q} ${symbol} when ${buy}; sell ${q} when ${sell}.`;
    }
  });

  constructor() {
    this.store.load();
    interval(STRATEGIES_POLL_MS)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.store.load(true);
        for (const id of this.executions().keys()) {
          this.loadExecutions(id, true);
        }
      });
  }

  // ===== Form =====

  openForm(): void {
    this.formOpen.set(true);
    this.notice.set(null);
  }

  closeForm(): void {
    this.formOpen.set(false);
    this.resetForm();
  }

  pick(instrument: Instrument): void {
    this.instrument.set(instrument);
  }

  num(event: Event): number {
    const value = (event.target as HTMLInputElement).value;
    return value === '' ? Number.NaN : Number(value);
  }

  showError(field: string): string | null {
    return this.submitted() ? (this.errors()[field] ?? null) : null;
  }

  submit(): void {
    this.submitted.set(true);
    this.formError.set(null);
    if (!this.canSubmit()) {
      return;
    }
    const ma = this.strategyType() === StrategyType.MaCrossover;
    const request: CreateStrategyRequest = {
      symbol: this.instrument()!.symbol,
      strategyType: this.strategyType(),
      shortWindow: ma ? this.shortWindow() : undefined,
      longWindow: ma ? this.longWindow() : undefined,
      bandWindow: ma ? undefined : this.bandWindow(),
      bandK: ma ? undefined : this.bandK(),
      tradeSide: this.tradeSide(),
      quantity: this.quantity(),
      maxPosition: this.maxPosition(),
      maxDailyNotional: this.maxDailyNotional(),
      maxConsecutiveFailures: this.maxFailures(),
    };
    this.submitting.set(true);
    this.store.create(request).subscribe({
      next: (created) => {
        this.submitting.set(false);
        this.formOpen.set(false);
        this.resetForm();
        this.notice.set(`Strategy on ${created.symbol} is on. It acts on the next price that meets its rule.`);
      },
      error: (err: Error) => {
        this.submitting.set(false);
        this.formError.set(err.message);
      },
    });
  }

  private resetForm(): void {
    this.instrument.set(null);
    this.strategyType.set(StrategyType.MaCrossover);
    this.shortWindow.set(5);
    this.longWindow.set(20);
    this.bandWindow.set(20);
    this.bandK.set(2);
    this.tradeSide.set(TradeSide.Both);
    this.quantity.set(1);
    this.maxPosition.set(10);
    this.maxDailyNotional.set(1000);
    this.maxFailures.set(3);
    this.consent.set(false);
    this.submitted.set(false);
    this.formError.set(null);
  }

  // ===== List =====

  isBusy(id: number): boolean {
    return this.busy().has(id);
  }

  toggle(strategy: StrategyResponse): void {
    this.run(strategy.strategyId, this.store.setActive(strategy.strategyId, !strategy.active));
  }

  askDelete(id: number): void {
    this.confirmDelete.set(id);
  }

  remove(id: number): void {
    this.confirmDelete.set(null);
    this.run(id, this.store.delete(id), () => {
      const next = new Map(this.executions());
      next.delete(id);
      this.executions.set(next);
    });
  }

  private run(id: number, request: Observable<unknown>, done?: () => void): void {
    this.setBusy(id, true);
    this.rowError.set(null);
    request.subscribe({
      next: () => {
        this.setBusy(id, false);
        done?.();
      },
      error: (err: Error) => {
        this.setBusy(id, false);
        this.rowError.set({ id, message: err.message });
      },
    });
  }

  private setBusy(id: number, on: boolean): void {
    const next = new Set(this.busy());
    if (on) next.add(id);
    else next.delete(id);
    this.busy.set(next);
  }

  status(strategy: StrategyResponse): { label: string; tone: 'on' | 'off' | 'stopped' } {
    if (strategy.active) return { label: 'On', tone: 'on' };
    if (strategy.disabledReason === 'FAILURES') {
      return { label: `Stopped after ${strategy.maxConsecutiveFailures} failures`, tone: 'stopped' };
    }
    return { label: 'Off', tone: 'off' };
  }

  stateLabel(state: IndicatorState | null | undefined): string {
    return state ? STATE_LABEL[state] : 'Waiting for price history';
  }

  outcomeLabel(outcome: ExecutionOutcome): string {
    return OUTCOME_LABEL[outcome] ?? outcome;
  }

  // ===== Executions =====

  executionsOf(id: number): ExecutionsView | undefined {
    return this.executions().get(id);
  }

  toggleExecutions(id: number): void {
    if (this.executions().has(id)) {
      const next = new Map(this.executions());
      next.delete(id);
      this.executions.set(next);
      return;
    }
    this.loadExecutions(id, false);
  }

  private loadExecutions(id: number, quiet: boolean): void {
    const current = this.executions().get(id);
    this.setExecutions(id, { loading: !quiet, rows: current?.rows ?? [], error: null });
    this.store.executions(id).subscribe({
      next: (rows) => this.setExecutions(id, { loading: false, rows, error: null }),
      error: (err: Error) => this.setExecutions(id, { loading: false, rows: current?.rows ?? [], error: err.message }),
    });
  }

  private setExecutions(id: number, view: ExecutionsView): void {
    this.executions.set(new Map(this.executions()).set(id, view));
  }
}

function isInt(value: number, min: number, max: number): boolean {
  return Number.isInteger(value) && value >= min && value <= max;
}
