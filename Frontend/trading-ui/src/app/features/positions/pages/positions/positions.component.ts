import { Component, OnInit, computed, effect, inject, signal, untracked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { AccountsService } from '../../../../api/generated/trade';
import {
  PortfolioService,
  PortfolioSummary,
  PricedPosition,
} from '../../../../api/generated/portfolio';
import { CoreAuthService } from '../../../../core/auth/core-auth.service';
import { StatementPdfService, fileDate } from '../../../../shared/statement-pdf.service';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { currencySymbol, formatMoney, toAccountCurrency } from '../../../../shared/currency';
import { LiveQuoteStore } from '../../../../core/market-data/live-quote.store';
import { OrderStatusWatcherService } from '../../../../core/notifications/order-status-watcher.service';
import { InstrumentCatalogService, exchangeOf, inMarket } from '../../../instruments/services/instrument-catalog.service';
import { FlashOnChangeDirective } from '../../../../shared/flash-on-change.directive';

export type HoldingsMarket = 'ALL' | 'USA' | 'NSE' | 'OTHER';
export type HoldingsSort = 'value-desc' | 'pnl-desc' | 'pnl-asc' | 'pnl-pct-desc' | 'symbol' | 'quantity-desc';

/** Orders holdings; holdings without the figure being sorted on go last */
export function sortHoldings(rows: readonly LivePosition[], sort: HoldingsSort): LivePosition[] {
  const by = (pick: (p: LivePosition) => number | null | undefined, dir: 1 | -1) => (a: LivePosition, b: LivePosition) => {
    const va = pick(a);
    const vb = pick(b);
    if (va == null || vb == null) {
      return va == null && vb == null ? 0 : va == null ? 1 : -1;
    }
    return (va - vb) * dir || a.symbol.localeCompare(b.symbol);
  };
  const list = [...rows];
  switch (sort) {
    case 'pnl-desc':
      return list.sort(by((p) => p.unrealisedPnlAccount ?? p.unrealisedPnl, -1));
    case 'pnl-asc':
      return list.sort(by((p) => p.unrealisedPnlAccount ?? p.unrealisedPnl, 1));
    case 'pnl-pct-desc':
      return list.sort(by((p) => p.unrealisedPnlPercent, -1));
    case 'symbol':
      return list.sort((a, b) => a.symbol.localeCompare(b.symbol));
    case 'quantity-desc':
      return list.sort(by((p) => p.quantity, -1));
    default:
      return list.sort(by((p) => p.marketValueAccount ?? p.marketValue, -1));
  }
}

/** A holding valued at its live price: the latest quote in the order service's cache,
 * or the portfolio API's price when the cache has none */
export interface LivePosition extends PricedPosition {
  /** True when the price came from the live quote cache */
  live: boolean;
}

/**
 * Positions Component
 *
 * Displays the portfolio summary and holdings from the portfolio API, valued at live prices:
 * the latest quotes from the order service's cache (LiveQuoteStore, re-read every 5 minutes, matching the backend's refresh),
 * falling back to the portfolio API's price for symbols the cache has no quote for.
 * Market value, P&L, weights, totals and the summary are all derived from those prices.
 *
 * Multi-currency: each row is in its instrument's currency (prices, market value, P&L); totals,
 * weights and the summary are in the account's currency, from each row's *Account figures. A live
 * price is converted at the row's fxRate, the live instrument-to-account rate from the portfolio API.
 */
@Component({
  selector: 'app-positions',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, FlashOnChangeDirective],
  templateUrl: './positions.component.html',
  styleUrls: ['./positions.component.css'],
})
export class PositionsComponent implements OnInit {
  private readonly authService = inject(CoreAuthService);
  private readonly accountsService = inject(AccountsService);
  private readonly portfolioService = inject(PortfolioService);
  private readonly statementPdf = inject(StatementPdfService);
  private readonly notifications = inject(NotificationService);
  private readonly liveQuotes = inject(LiveQuoteStore);
  private readonly catalog = inject(InstrumentCatalogService);
  private readonly orderWatcher = inject(OrderStatusWatcherService);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly positions = signal<PricedPosition[]>([]);
  readonly summaryLoading = signal(false);
  readonly summaryError = signal<string | null>(null);
  readonly summary = signal<PortfolioSummary | null>(null);
  readonly downloading = signal(false);
  readonly downloadError = signal<string | null>(null);
  readonly totalUnits = computed(() =>
    this.positions().reduce((sum, p) => sum + (p.quantity || 0), 0)
  );
  /** Cost basis of every holding, in the account's currency */
  readonly totalCostBasis = computed(() =>
    this.positions().reduce((sum, p) => sum + (p.costBasisAccount ?? p.costBasis ?? 0), 0)
  );
  /** Holdings revalued at their live price, recomputed whenever a quote refreshes */
  readonly livePositions = computed<LivePosition[]>(() =>
    this.positions().map((p) => {
      const cached = this.liveQuotes.quote(p.symbol);
      // A live quote in another currency than the position's cannot be compared with its cost
      const quote = cached && (!cached.currency || cached.currency === p.currency) ? cached : undefined;
      const price = quote?.price ?? p.lastPrice ?? null;
      const marketValue = price == null ? null : p.quantity * price;
      const unrealisedPnl = marketValue == null ? null : marketValue - p.costBasis;
      // Account-currency figures at the live FX rate; null when the holding has no price or no rate
      const marketValueAccount = toAccountCurrency(marketValue, p.fxRate);
      const costBasisAccount = p.costBasisAccount ?? p.costBasis;
      return {
        ...p,
        lastPrice: price,
        marketValue,
        unrealisedPnl,
        marketValueAccount,
        unrealisedPnlAccount: marketValueAccount == null ? null : marketValueAccount - costBasisAccount,
        unrealisedPnlPercent: unrealisedPnl == null || !p.costBasis ? null : (unrealisedPnl / p.costBasis) * 100,
        priceAsOf: quote?.receivedAt ?? p.priceAsOf,
        stale: quote ? quote.stale === true : p.stale,
        live: !!quote,
      };
    })
  );
  /** Holdings filters and order */
  readonly holdingsQuery = signal('');
  readonly holdingsMarket = signal<HoldingsMarket>('ALL');
  readonly holdingsClass = signal<'ALL' | 'EQUITY' | 'FX' | 'CRYPTO'>('ALL');
  readonly holdingsPnl = signal<'all' | 'gain' | 'loss'>('all');
  readonly holdingsSort = signal<HoldingsSort>('value-desc');

  readonly holdingsSortOptions: { value: HoldingsSort; label: string }[] = [
    { value: 'value-desc', label: 'Market value (largest first)' },
    { value: 'pnl-desc', label: 'Unrealised P&L (best first)' },
    { value: 'pnl-asc', label: 'Unrealised P&L (worst first)' },
    { value: 'pnl-pct-desc', label: 'Return % (best first)' },
    { value: 'symbol', label: 'Symbol (A–Z)' },
    { value: 'quantity-desc', label: 'Quantity (largest first)' },
  ];

  /** Holdings after the filters and the chosen order (totals and weights still cover every holding) */
  readonly visiblePositions = computed<LivePosition[]>(() => {
    const q = this.holdingsQuery().trim().toUpperCase();
    const market = this.holdingsMarket();
    const cls = this.holdingsClass();
    const pnl = this.holdingsPnl();
    const filtered = this.livePositions().filter((p) => {
      const instrument = this.catalog.find(p.symbol);
      const exchange = exchangeOf({ symbol: p.symbol, exchange: instrument?.exchange });
      const inChosenMarket =
        market === 'ALL' ||
        (market === 'OTHER' ? !inMarket({ symbol: p.symbol, exchange }, 'USA') && !inMarket({ symbol: p.symbol, exchange }, 'NSE') : inMarket({ symbol: p.symbol, exchange }, market));
      const assetClass = instrument?.assetClass ?? (exchange === 'FX' ? 'FX' : exchange === 'CRYPTO' ? 'CRYPTO' : 'EQUITY');
      const gain = p.unrealisedPnlAccount ?? p.unrealisedPnl ?? null;
      return (
        (!q || p.symbol.toUpperCase().includes(q) || (instrument?.name ?? '').toUpperCase().includes(q)) &&
        inChosenMarket &&
        (cls === 'ALL' || assetClass === cls) &&
        (pnl === 'all' || (gain !== null && (pnl === 'gain' ? gain > 0 : gain < 0)))
      );
    });
    return sortHoldings(filtered, this.holdingsSort());
  });

  readonly holdingsFiltered = computed(
    () =>
      !!this.holdingsQuery().trim() ||
      this.holdingsMarket() !== 'ALL' ||
      this.holdingsClass() !== 'ALL' ||
      this.holdingsPnl() !== 'all',
  );

  clearHoldingsFilters(): void {
    this.holdingsQuery.set('');
    this.holdingsMarket.set('ALL');
    this.holdingsClass.set('ALL');
    this.holdingsPnl.set('all');
  }

  /** Market value of the priced holdings in the account's currency (the Weight column is a share of this) */
  readonly totalMarketValue = computed(() =>
    this.livePositions().reduce((sum, p) => sum + (p.marketValueAccount ?? 0), 0)
  );
  /** Unrealised P&L in the account's currency */
  readonly totalUnrealised = computed(() =>
    this.livePositions().reduce((sum, p) => sum + (p.unrealisedPnlAccount ?? 0), 0)
  );
  /** Cost basis of the holdings that have a price and a rate, the base for the unrealised P&L % */
  private readonly pricedCostBasis = computed(() =>
    this.livePositions().reduce(
      (sum, p) => sum + (p.marketValueAccount == null ? 0 : p.costBasisAccount ?? p.costBasis),
      0
    )
  );
  /**
   * Summary figures at live prices. Cash, cost basis and realised P&L come from the summary
   * endpoint; holdings value and unrealised P&L are the live totals of the table, so the bar
   * and the table always agree. `cost` is the base of the unrealised %, shown beside it.
   * Until the holdings have loaded the endpoint's figures are used.
   */
  readonly liveSummary = computed(() => {
    const s = this.summary();
    if (!s) {
      return null;
    }
    if (this.error() || this.positions().length === 0) {
      return { holdings: s.marketValue, total: s.totalValue, unrealised: s.unrealisedPnl, unrealisedPercent: s.unrealisedPnlPercent ?? null, cost: s.costBasis };
    }
    const holdings = this.totalMarketValue();
    const unrealised = this.totalUnrealised();
    const cost = this.pricedCostBasis();
    return { holdings, total: s.cashBalance + holdings, unrealised, unrealisedPercent: cost ? (unrealised / cost) * 100 : null, cost };
  });
  /** Symbol of the summary's base currency, e.g. $ for USD */
  readonly baseSymbol = computed(() => currencySymbol(this.summary()?.baseCurrency));
  /** The account's currency code, for totals and P&L */
  readonly baseCurrency = computed(() => this.summary()?.baseCurrency ?? null);
  /**
   * When the holdings' live prices were last fetched (epoch ms): moves on every 5-minute poll
   * and on a manual refresh, even when the backend's cached quote has not changed.
   */
  readonly pricesAsOf = computed(() =>
    this.livePositions().reduce<number | null>((latest, p) => {
      const fetchedAt = p.live ? this.liveQuotes.entry(p.symbol)?.fetchedAt : undefined;
      return fetchedAt && (!latest || fetchedAt > latest) ? fetchedAt : latest;
    }, null)
  );

  constructor() {
    // Keep every held symbol's live price fresh from the quote cache
    this.liveQuotes.track(() => this.positions().map((p) => p.symbol));

    // An order filled: quantities, cost and cash changed, so reload holdings and summary in place
    let seenFills = this.orderWatcher.fills();
    effect(() => {
      const fills = this.orderWatcher.fills();
      if (fills !== seenFills) {
        seenFills = fills;
        untracked(() => this.reloadQuietly());
      }
    });
  }

  ngOnInit(): void {
    const accountId = this.authService.currentUser()?.accountId;
    if (!accountId) {
      this.error.set('No account found for current session. Please sign in again.');
      return;
    }

    this.loadPortfolio(accountId);
  }

  /** Reload the summary and holdings, and re-fetch their live prices. */
  refresh(): void {
    const accountId = this.authService.currentUser()?.accountId;
    if (accountId) {
      this.loadPortfolio(accountId);
      this.liveQuotes.refresh(this.positions().map((p) => p.symbol), true);
    }
  }

  /** Reload holdings and summary without spinners, keeping the table on screen meanwhile */
  private reloadQuietly(): void {
    const accountId = this.authService.currentUser()?.accountId;
    if (accountId) {
      this.loadPortfolio(accountId, true);
    }
  }

  private loadPortfolio(accountId: number, quiet = false): void {
    this.loadSummary(accountId, quiet);
    this.loadPositions(accountId, quiet);
  }

  private loadSummary(accountId: number, quiet = false): void {
    if (!quiet) {
      this.summaryLoading.set(true);
    }
    this.summaryError.set(null);

    this.portfolioService
      .getPortfolioSummary(accountId)
      .pipe(finalize(() => quiet || this.summaryLoading.set(false)))
      .subscribe({
        next: (summary) => {
          this.summary.set(summary ?? null);
        },
        error: () => {
          this.summaryError.set('Could not fetch the portfolio summary.');
        },
      });
  }

  private loadPositions(accountId: number, quiet = false): void {
    if (!quiet) {
      this.loading.set(true);
    }
    this.error.set(null);

    this.portfolioService
      .getPricedPositions(accountId)
      .pipe(finalize(() => quiet || this.loading.set(false)))
      .subscribe({
        next: (positions) => {
          this.positions.set(positions ?? []);
        },
        error: () => {
          this.error.set('Could not fetch current positions.');
        },
      });
  }

  /** Download the current holdings as a PDF statement. */
  async downloadStatement(): Promise<void> {
    const user = this.authService.currentUser();
    const positions = this.positions();

    this.downloading.set(true);
    this.downloadError.set(null);
    try {
      const accountNumber = await this.customerAccountNumber();
      const fileName = `holdings-statement-${accountNumber}-${fileDate()}`;
      await this.statementPdf.download({
        title: 'Holdings Statement',
        details: [
          ['Account', accountNumber],
          ['Prepared for', user?.username ?? 'N/A'],
        ],
        summary: [
          ['Open positions', String(positions.length)],
          ['Total units', this.formatNumber(this.totalUnits(), 0)],
          ['Total cost basis', formatMoney(this.totalCostBasis(), this.baseCurrency())],
        ],
        columns: [
          { header: 'Symbol' },
          { header: 'Currency' },
          { header: 'Quantity', numeric: true },
          { header: 'Average cost', numeric: true },
          { header: 'Cost basis', numeric: true },
          { header: 'Cost basis (' + (this.baseCurrency() ?? 'account') + ')', numeric: true },
        ],
        rows: positions.map((p) => [
          p.symbol,
          p.currency,
          this.formatNumber(p.quantity, 0),
          this.formatNumber(p.averageCost, this.catalog.decimals(p.symbol)),
          this.formatNumber(p.costBasis, 2),
          this.formatNumber(p.costBasisAccount ?? p.costBasis, 2),
        ]),
        footerRow: [
          'Total',
          '',
          this.formatNumber(this.totalUnits(), 0),
          '',
          '',
          formatMoney(this.totalCostBasis(), this.baseCurrency()),
        ],
        emptyMessage: 'There are no open positions on this account.',
        fileName,
      });
      this.notifications.notify({
        kind: 'report',
        title: 'Report exported',
        message: `Holdings statement downloaded as ${fileName}.pdf.`,
        link: { path: '/positions' },
      });
    } catch (err) {
      console.error('Failed to generate holdings statement', err);
      this.downloadError.set('Could not generate the statement. Please try again.');
    } finally {
      this.downloading.set(false);
    }
  }

  /** Customer-facing account number (e.g. ACC-000001) for statements, not the internal numeric key. */
  private async customerAccountNumber(): Promise<string> {
    const accountId = this.authService.currentUser()?.accountId;
    if (!accountId) {
      throw new Error('No account for the current session');
    }
    const account = await firstValueFrom(this.accountsService.getAccount(accountId));
    if (!account?.accountId) {
      throw new Error('Account has no customer-facing number');
    }
    return account.accountId;
  }

  formatNumber(value: number, fractionDigits: number): string {
    return new Intl.NumberFormat('en-US', {
      minimumFractionDigits: fractionDigits,
      maximumFractionDigits: fractionDigits,
    }).format(value);
  }

  /** Money with an explicit sign, for P&L figures; '—' when the value is unknown. */
  formatSigned(value: number | null | undefined, fractionDigits = 2): string {
    if (value === null || value === undefined) {
      return '—';
    }
    const formatted = this.formatNumber(Math.abs(value), fractionDigits);
    return value > 0 ? `+${formatted}` : value < 0 ? `-${formatted}` : formatted;
  }

  /** Price or amount with its currency symbol, e.g. ₹1,006.35 */
  money(value: number, currency: string | null | undefined, fractionDigits = 2): string {
    return `${currencySymbol(currency)}${this.formatNumber(value, fractionDigits)}`;
  }

  /** A per-unit price with its currency symbol and the instrument's precision (4 decimals for FX) */
  price(value: number, position: PricedPosition): string {
    return this.money(value, position.currency, this.catalog.decimals(position.symbol));
  }

  /** A P&L figure with its sign and currency, e.g. +₹4,000.00; '—' when unknown */
  signedMoney(value: number | null | undefined, currency: string | null | undefined): string {
    return formatMoney(value, currency, { signed: true });
  }

  /** An amount in the account's currency, e.g. $1,234.50 */
  baseMoney(value: number | null | undefined, signed = false): string {
    return formatMoney(value, this.baseCurrency(), { signed });
  }

  /** True when the row's instrument trades in another currency than the account */
  isForeign(position: PricedPosition): boolean {
    const base = this.baseCurrency();
    return !!base && !!position.currency && position.currency !== base;
  }

  /** Share of the holdings' market value in account currency, in percent; null when the holding has no value */
  weight(position: PricedPosition): number | null {
    const total = this.totalMarketValue();
    if (position.marketValueAccount == null || total <= 0) {
      return null;
    }
    return (position.marketValueAccount / total) * 100;
  }

  /** CSS tone for a P&L value. */
  pnlTone(value: number | null | undefined): string {
    if (!value) {
      return '';
    }
    return value > 0 ? 'pnl-up' : 'pnl-down';
  }

}
