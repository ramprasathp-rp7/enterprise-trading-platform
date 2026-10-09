import { HttpErrorResponse } from '@angular/common/http';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { of, throwError } from 'rxjs';
import { PositionsComponent } from './positions.component';
import { AccountsService } from '../../../../api/generated/trade';
import { PortfolioService, PortfolioSummary, PricedPosition } from '../../../../api/generated/portfolio';
import { CoreAuthService } from '../../../../core/auth/core-auth.service';
import { StatementPdfService } from '../../../../shared/statement-pdf.service';
import { MarketDataService } from '../../../../api/generated/market-data';
import { LIVE_QUOTE_TTL_MS } from '../../../../core/market-data/live-quote.store';
import { OrderStatusWatcherService } from '../../../../core/notifications/order-status-watcher.service';

describe('PositionsComponent', () => {
  let fixture: ComponentFixture<PositionsComponent>;
  let component: PositionsComponent;
  let accounts: { getAccount: ReturnType<typeof vi.fn> };
  let portfolio: { getPricedPositions: ReturnType<typeof vi.fn>; getPortfolioSummary: ReturnType<typeof vi.fn> };
  let currentUser: ReturnType<typeof vi.fn>;
  let statementPdf: { download: ReturnType<typeof vi.fn> };
  let marketData: { getLatestQuote: ReturnType<typeof vi.fn> };
  let fills: ReturnType<typeof signal<number>>;

  const positions: PricedPosition[] = [
    {
      accountId: 7, symbol: 'AAPL', quantity: 100, averageCost: 182.4, costBasis: 18240,
      lastPrice: 190, marketValue: 19000, unrealisedPnl: 760, unrealisedPnlPercent: 4.17,
      currency: 'USD', priceAsOf: '2026-10-06T09:45:00Z', stale: false,
      fxRate: 1, averageCostAccount: 182.4, costBasisAccount: 18240, marketValueAccount: 19000, unrealisedPnlAccount: 760,
    },
    {
      accountId: 7, symbol: 'MSFT', quantity: 20, averageCost: 351.1, costBasis: 7022,
      lastPrice: 340, marketValue: 6800, unrealisedPnl: -222, unrealisedPnlPercent: -3.16,
      currency: 'USD', priceAsOf: '2026-10-06T09:40:00Z', stale: false,
      fxRate: 1, averageCostAccount: 351.1, costBasisAccount: 7022, marketValueAccount: 6800, unrealisedPnlAccount: -222,
    },
  ];

  const summary: PortfolioSummary = {
    accountId: 7, baseCurrency: 'USD', cashBalance: 5000, marketValue: 25800, costBasis: 25262,
    unrealisedPnl: 538, unrealisedPnlPercent: 2.13, realisedPnl: -120.5, totalValue: 30800,
    positionCount: 2, partial: false, asOf: '2026-10-06T09:46:00Z',
  };

  async function create(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [PositionsComponent],
      providers: [
        provideRouter([]),
        { provide: AccountsService, useValue: accounts },
        { provide: PortfolioService, useValue: portfolio },
        { provide: CoreAuthService, useValue: { currentUser } },
        { provide: StatementPdfService, useValue: statementPdf },
        { provide: MarketDataService, useValue: marketData },
        { provide: OrderStatusWatcherService, useValue: { fills: fills.asReadonly() } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(PositionsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(() => {
    portfolio = { getPricedPositions: vi.fn(() => of(positions)), getPortfolioSummary: vi.fn(() => of(summary)) };
    accounts = { getAccount: vi.fn(() => of({ id: 7, accountId: 'ACC-000007', holderName: 'Rahul Kumar', cashBalance: 1, status: 'ACTIVE', version: 1, lastUpdated: '' })) };
    currentUser = vi.fn(() => ({ accountId: 7, username: 'rkumar' }));
    statementPdf = { download: vi.fn().mockResolvedValue(undefined) };
    // By default the live quote cache has nothing, so the portfolio API's prices are used
    marketData = { getLatestQuote: vi.fn(() => throwError(() => new HttpErrorResponse({ status: 404 }))) };
    fills = signal(0);
  });

  afterEach(() => vi.useRealTimers());

  it('loads priced positions and renders a row per holding with cost basis', async () => {
    await create();

    expect(portfolio.getPricedPositions).toHaveBeenCalledWith(7);
    const rows = fixture.nativeElement.querySelectorAll('tbody tr.transaction-row');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('AAPL');
    expect(rows[0].textContent).toContain('18,240.00');
    expect(component.loading()).toBe(false);
  });

  it('derives total units and total cost basis', async () => {
    await create();

    expect(component.totalUnits()).toBe(120);
    expect(component.totalCostBasis()).toBeCloseTo(18240 + 7022);
  });

  it('renders the portfolio summary tiles from the summary endpoint', async () => {
    await create();

    expect(portfolio.getPortfolioSummary).toHaveBeenCalledWith(7);
    const text = (id: string) => fixture.nativeElement.querySelector(`[data-testid="${id}"]`).textContent.trim();
    expect(text('summary-total-value')).toBe('30,800.00');
    expect(text('summary-market-value')).toBe('25,800.00');
    expect(text('summary-unrealised')).toBe('+$538.00');
    expect(text('summary-realised')).toBe('-$120.50');
    expect(text('summary-cash')).toBe('5,000.00');
    expect(fixture.nativeElement.querySelector('[data-testid="summary-unrealised"]').classList).toContain('pnl-up');
    expect(fixture.nativeElement.querySelector('[data-testid="summary-realised"]').classList).toContain('pnl-down');
    expect(fixture.nativeElement.querySelector('[data-testid="pricing-warning"]')).toBeNull();
  });

  it('shows the unrealised % against the cost it was computed on, leaving out unpriced holdings', async () => {
    portfolio.getPricedPositions.mockReturnValue(
      of([positions[0], { ...positions[1], lastPrice: null, marketValue: null, unrealisedPnl: null, unrealisedPnlPercent: null }]),
    );
    await create();

    // AAPL alone: +760 on 18,240; MSFT's 7,022 has no price, so it is in neither figure
    const caption: string = fixture.nativeElement.querySelector('[data-testid="summary-unrealised"]').closest('.sum-item').textContent;
    expect(component.liveSummary()!.cost).toBe(18240);
    expect(caption).toContain('+4.17% on cost 18,240.00');
    expect(caption).not.toContain('25,262.00');
  });

  it('shows last price, market value and signed unrealised P&L per holding', async () => {
    await create();

    const rows = fixture.nativeElement.querySelectorAll('tbody tr.transaction-row');
    expect(rows[0].textContent).toContain('190.00');
    expect(rows[0].textContent).toContain('19,000.00');
    expect(rows[0].textContent).toContain('+$760.00');
    expect(rows[1].textContent).toContain('-$222.00');
    expect(rows[1].textContent).toContain('-3.16%');
  });

  it('reads the summary as Total = Cash + Holdings, with the base currency symbol', async () => {
    await create();

    const bar: HTMLElement = fixture.nativeElement.querySelector('.summary-bar');
    expect(bar.querySelectorAll('.sum-op').length).toBe(2);
    expect(Array.from(bar.querySelectorAll('.sum-label')).map((e) => e.textContent!.trim())).toEqual([
      'Total value', 'Cash', 'Holdings', 'Unrealised P&L', 'Realised P&L',
    ]);
    expect(component.baseSymbol()).toBe('$');
    expect(bar.querySelector('.sum-total .sum-value')!.textContent!.trim()).toBe('$30,800.00');
    expect(fixture.nativeElement.querySelector('[data-testid="portfolio-as-of"]').textContent).toContain('Live prices');
  });

  it("shows each holding's weight by market value, prices with currency symbols, and a totals row", async () => {
    await create();

    expect(component.weight(positions[0])).toBeCloseTo((19000 / 25800) * 100);
    expect(component.weight({ ...positions[0], marketValue: null, marketValueAccount: null })).toBeNull();
    expect(component.money(1006.35, 'INR')).toBe('₹1,006.35');

    const rows = fixture.nativeElement.querySelectorAll('tbody tr.transaction-row');
    expect(rows[0].textContent).toContain('$190.00');
    expect(rows[0].querySelector('.weight-pct').textContent).toContain('73.6%');
    expect(rows[0].querySelector('.weight-fill').style.width).toMatch(/^73\.6/);

    const totals: HTMLElement = fixture.nativeElement.querySelector('[data-testid="holdings-totals"]');
    expect(totals.textContent).toContain('$25,800.00');
    expect(totals.textContent).toContain('cost $25,262.00');
    expect(totals.textContent).toContain('+$538.00');
  });

  it('values holdings at the live price from the quote cache and keeps the table and summary in step', async () => {
    marketData.getLatestQuote.mockImplementation((symbol: string) =>
      symbol === 'AAPL'
        ? of({ symbol: 'AAPL', price: 200, bid: 199.9, ask: 200.1, currency: 'USD', stale: false, receivedAt: '2026-10-06T10:00:00Z' })
        : throwError(() => new HttpErrorResponse({ status: 404 })),
    );
    await create();
    fixture.detectChanges();

    expect(marketData.getLatestQuote).toHaveBeenCalledWith('AAPL');
    expect(marketData.getLatestQuote).toHaveBeenCalledWith('MSFT');
    const [aapl, msft] = component.livePositions();
    // AAPL revalued at the live 200.00; MSFT keeps the portfolio API's 340.00
    expect(aapl).toEqual(expect.objectContaining({ lastPrice: 200, marketValue: 20000, unrealisedPnl: 1760, live: true }));
    expect(aapl.unrealisedPnlPercent).toBeCloseTo((1760 / 18240) * 100);
    expect(msft).toEqual(expect.objectContaining({ lastPrice: 340, marketValue: 6800, live: false }));

    const rows = fixture.nativeElement.querySelectorAll('tbody tr.transaction-row');
    expect(rows[0].querySelector('[data-testid="live-price"]').textContent).toContain('$200.00');
    expect(rows[0].querySelector('[data-testid="live-price"]').getAttribute('title')).toContain('Live price');
    expect(rows[1].querySelector('[data-testid="live-price"]').getAttribute('title')).toContain('portfolio service');
    expect(fixture.nativeElement.querySelector('thead').textContent).toContain('Live price');

    // Totals and the summary bar follow the live prices: holdings 26,800, total = 5,000 cash + 26,800
    const text = (id: string) => fixture.nativeElement.querySelector(`[data-testid="${id}"]`).textContent.trim();
    expect(text('summary-market-value')).toBe('26,800.00');
    expect(text('summary-total-value')).toBe('31,800.00');
    expect(text('summary-unrealised')).toBe('+$1,538.00');
    expect(text('summary-cash')).toBe('5,000.00');
    expect(fixture.nativeElement.querySelector('[data-testid="holdings-totals"]').textContent).toContain('26,800.00');
  });

  it('flags stale and unpriced holdings and warns that the valuation is partial', async () => {
    portfolio.getPricedPositions.mockReturnValue(of([
      { ...positions[0], stale: true },
      { ...positions[1], lastPrice: null, marketValue: null, unrealisedPnl: null, unrealisedPnlPercent: null, priceAsOf: null, stale: true },
    ]));
    portfolio.getPortfolioSummary.mockReturnValue(of({ ...summary, partial: true }));
    await create();

    // No Stale badge on the priced holding; the unpriced one gets No price
    expect(fixture.nativeElement.querySelectorAll('[data-testid="stale-badge"]').length).toBe(0);
    expect(fixture.nativeElement.querySelectorAll('[data-testid="unpriced-badge"]').length).toBe(1);
    const warning = fixture.nativeElement.querySelector('[data-testid="pricing-warning"]');
    expect(warning).not.toBeNull();
    expect(warning.textContent).toContain('could not be priced');
  });

  it('shows no warning when every holding is priced, even if some quotes are stale', async () => {
    portfolio.getPricedPositions.mockReturnValue(of([{ ...positions[0], stale: true }, positions[1]]));
    await create();

    expect(fixture.nativeElement.querySelectorAll('[data-testid="stale-badge"]').length).toBe(0);
    expect(fixture.nativeElement.querySelector('[data-testid="pricing-warning"]')).toBeNull();
  });

  it('still shows holdings when the summary request fails', async () => {
    portfolio.getPortfolioSummary.mockReturnValue(throwError(() => new Error('503')));
    await create();

    expect(component.summaryError()).toBe('Could not fetch the portfolio summary.');
    expect(fixture.nativeElement.querySelector('[data-testid="summary-total-value"]').textContent.trim()).toBe('—');
    expect(fixture.nativeElement.querySelectorAll('tbody tr.transaction-row').length).toBe(2);
  });

  it('reloads the summary and holdings on refresh', async () => {
    await create();

    fixture.nativeElement.querySelector('[data-testid="refresh-portfolio-btn"]').click();

    expect(portfolio.getPortfolioSummary).toHaveBeenCalledTimes(2);
    expect(portfolio.getPricedPositions).toHaveBeenCalledTimes(2);
  });

  it('moves the live prices time forward on a manual refresh, even when the cached quote is unchanged', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-06T10:00:00Z'));
    marketData.getLatestQuote.mockImplementation((symbol: string) =>
      of({ symbol, price: 200, bid: 200, ask: 200, currency: 'USD', stale: false, receivedAt: '2026-10-06T09:58:00Z' }),
    );
    await create();
    expect(component.pricesAsOf()).toBe(Date.parse('2026-10-06T10:00:00Z'));

    vi.setSystemTime(new Date('2026-10-06T10:01:30Z'));
    fixture.nativeElement.querySelector('[data-testid="refresh-portfolio-btn"]').click();
    fixture.detectChanges();

    expect(component.pricesAsOf()).toBe(Date.parse('2026-10-06T10:01:30Z'));
    fixture.destroy();
  });

  it('polls live prices from the quote cache without re-calling the portfolio API', async () => {
    vi.useFakeTimers();
    let price = 200;
    marketData.getLatestQuote.mockImplementation((symbol: string) =>
      of({ symbol, price, bid: price, ask: price, currency: 'USD', stale: false, receivedAt: '2026-10-06T10:00:00Z' }),
    );
    await create();
    expect(component.livePositions()[0].lastPrice).toBe(200);

    price = 210;
    vi.advanceTimersByTime(LIVE_QUOTE_TTL_MS);
    fixture.detectChanges();

    expect(component.livePositions()[0]).toEqual(expect.objectContaining({ lastPrice: 210, marketValue: 21000 }));
    expect(fixture.nativeElement.querySelector('[data-testid="portfolio-as-of"]').textContent).toContain('as of');
    // The portfolio API quotes Fauxnance on every call, so it is not on the live timer
    expect(portfolio.getPricedPositions).toHaveBeenCalledTimes(1);
    expect(portfolio.getPortfolioSummary).toHaveBeenCalledTimes(1);
    fixture.destroy();
  });

  it('reloads holdings in place, without the loading spinner, when an order fills', async () => {
    await create();
    portfolio.getPricedPositions.mockReturnValue(of([{ ...positions[0], quantity: 110, costBasis: 20140 }, positions[1]]));

    fills.set(1);
    fixture.detectChanges();

    expect(portfolio.getPricedPositions).toHaveBeenCalledTimes(2);
    expect(portfolio.getPortfolioSummary).toHaveBeenCalledTimes(2);
    expect(component.loading()).toBe(false);
    expect(component.positions()[0].quantity).toBe(110);
  });

  it('shows the empty state when there are no positions', async () => {
    portfolio.getPricedPositions.mockReturnValue(of(null));
    await create();

    expect(component.positions()).toEqual([]);
    expect(fixture.nativeElement.querySelector('.empty-state').textContent).toContain('No active positions');
  });

  it('shows an error without calling the API when there is no account', async () => {
    currentUser.mockReturnValue(null);
    await create();

    expect(portfolio.getPricedPositions).not.toHaveBeenCalled();
    expect(portfolio.getPortfolioSummary).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('.alert-error').textContent).toContain('No account found');
  });

  it('shows an error when the request fails', async () => {
    portfolio.getPricedPositions.mockReturnValue(throwError(() => new Error('down')));
    await create();

    expect(component.error()).toBe('Could not fetch current positions.');
    expect(component.loading()).toBe(false);
  });

  it('formats numbers with the requested precision', async () => {
    await create();

    expect(component.formatNumber(1234.567, 2)).toBe('1,234.57');
    expect(component.formatNumber(1234.567, 0)).toBe('1,235');
  });

  it('offers Buy and Sell on each holding, linking to a prefilled order ticket', async () => {
    await create();

    const buy = fixture.nativeElement.querySelectorAll('[data-testid="holding-buy-btn"]');
    const sell = fixture.nativeElement.querySelectorAll('[data-testid="holding-sell-btn"]');
    expect(buy.length).toBe(2);
    expect(sell.length).toBe(2);
    expect(buy[0].getAttribute('href')).toBe('/order-ticket?symbol=AAPL&side=BUY&from=holding');
    expect(sell[1].getAttribute('href')).toBe('/order-ticket?symbol=MSFT&side=SELL&from=holding');
    expect(buy[0].getAttribute('aria-label')).toBe('Buy AAPL');
  });

  describe('multi-currency holdings', () => {
    const infy: PricedPosition = {
      accountId: 7, symbol: 'INFY.NS', quantity: 40, averageCost: 1500, costBasis: 60000,
      lastPrice: 1600, marketValue: 64000, unrealisedPnl: 4000, unrealisedPnlPercent: 6.67,
      currency: 'INR', priceAsOf: '2026-10-06T09:45:00Z', stale: false,
      fxRate: 0.0125, averageCostAccount: 18, costBasisAccount: 720, marketValueAccount: 800, unrealisedPnlAccount: 80,
    };

    it('sums the account-currency values, not the native ones', async () => {
      portfolio.getPricedPositions = vi.fn(() => of([positions[0], infy]));
      await create();

      // AAPL $19,000 + INFY INR 64,000 at 0.0125 = $800, not 64,000
      expect(component.totalMarketValue()).toBe(19800);
      expect(component.totalUnrealised()).toBe(840);
      expect(component.totalCostBasis()).toBe(18960);
      expect(component.weight(infy)).toBeCloseTo((800 / 19800) * 100, 6);
    });

    it('shows the native value with its account-currency equivalent', async () => {
      portfolio.getPricedPositions = vi.fn(() => of([positions[0], infy]));
      await create();

      const equivalents = fixture.nativeElement.querySelectorAll('[data-testid="market-value-account"]');
      expect(equivalents.length).toBe(1); // only the INR row
      expect(equivalents[0].textContent.trim()).toBe('≈ $800.00');
      expect(fixture.nativeElement.textContent).toContain('₹64,000.00');
    });

    it('leaves a holding with no exchange rate out of the totals', async () => {
      portfolio.getPricedPositions = vi.fn(() => of([positions[0], { ...infy, fxRate: null, marketValueAccount: null, unrealisedPnlAccount: null }]));
      await create();

      expect(component.totalMarketValue()).toBe(19000);
    });
  });

  describe('holdings statement download', () => {
    it('downloads a PDF of the current holdings with totals', async () => {
      await create();

      const notify = vi.spyOn(TestBed.inject(NotificationService), 'notify');
      fixture.nativeElement.querySelector('[data-testid="download-statement-btn"]').click();
      await fixture.whenStable();

      expect(statementPdf.download).toHaveBeenCalledTimes(1);
      const def = statementPdf.download.mock.calls[0][0];
      expect(def.title).toBe('Holdings Statement');
      expect(accounts.getAccount).toHaveBeenCalledWith(7);
      expect(def.details).toEqual([['Account', 'ACC-000007'], ['Prepared for', 'rkumar']]);
      expect(def.summary).toEqual([['Open positions', '2'], ['Total units', '120'], ['Total cost basis', '$25,262.00']]);
      expect(def.columns.map((c: any) => c.header)).toEqual([
        'Symbol', 'Currency', 'Quantity', 'Average cost', 'Cost basis', 'Cost basis (USD)',
      ]);
      expect(def.rows[0]).toEqual(['AAPL', 'USD', '100', '182.40', '18,240.00', '18,240.00']);
      expect(def.footerRow).toEqual(['Total', '', '120', '', '', '$25,262.00']);
      expect(def.fileName).toMatch(/^holdings-statement-ACC-000007-\d{4}-\d{2}-\d{2}$/);
      expect(notify).toHaveBeenCalledWith({
        kind: 'report',
        title: 'Report exported',
        message: `Holdings statement downloaded as ${def.fileName}.pdf.`,
        link: { path: '/positions' },
      });
      expect(component.downloading()).toBe(false);
    });

    it('shows "Preparing…" while the PDF is being built', async () => {
      let finish!: () => void;
      statementPdf.download.mockReturnValue(new Promise<void>((resolve) => (finish = resolve)));
      await create();

      const pending = component.downloadStatement();
      fixture.detectChanges();
      const button = fixture.nativeElement.querySelector('[data-testid="download-statement-btn"]');
      expect(button.textContent).toContain('Preparing');
      expect(button.disabled).toBe(true);

      finish();
      await pending;
      fixture.detectChanges();
      expect(button.textContent).toContain('Download statement');
    });

    it('shows an error if the statement cannot be generated', async () => {
      statementPdf.download.mockRejectedValue(new Error('boom'));
      const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});
      await create();
      const notify = vi.spyOn(TestBed.inject(NotificationService), 'notify');

      await component.downloadStatement();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('[data-testid="download-error"]').textContent).toContain('Could not generate the statement');
      expect(component.downloading()).toBe(false);
      expect(notify).not.toHaveBeenCalled();
      consoleError.mockRestore();
    });

    it('does not download when the customer account number cannot be loaded', async () => {
      await create();
      accounts.getAccount.mockReturnValue(throwError(() => new Error('down')));
      const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});

      await component.downloadStatement();

      expect(statementPdf.download).not.toHaveBeenCalled();
      expect(component.downloadError()).toBe('Could not generate the statement. Please try again.');
      consoleError.mockRestore();
    });
  });

  describe('holdings filters', () => {
    it('sorts by market value by default and by P&L on request', async () => {
      await create();
      expect(component.visiblePositions().map((p) => p.symbol)).toEqual(['AAPL', 'MSFT']);

      component.holdingsSort.set('pnl-asc');
      expect(component.visiblePositions().map((p) => p.symbol)).toEqual(['MSFT', 'AAPL']);
      component.holdingsSort.set('symbol');
      expect(component.visiblePositions().map((p) => p.symbol)).toEqual(['AAPL', 'MSFT']);
    });

    it('filters by profit or loss, text and market, and says how many are shown', async () => {
      await create();

      component.holdingsPnl.set('loss');
      fixture.detectChanges();
      expect(component.visiblePositions().map((p) => p.symbol)).toEqual(['MSFT']);
      expect(fixture.nativeElement.querySelector('[data-testid="holdings-shown"]').textContent).toContain('1 of 2');

      component.holdingsPnl.set('all');
      component.holdingsQuery.set('apple');
      expect(component.visiblePositions().map((p) => p.symbol)).toEqual(['AAPL']);

      component.holdingsQuery.set('');
      component.holdingsMarket.set('NSE');
      fixture.detectChanges();
      expect(component.visiblePositions()).toEqual([]);
      expect(fixture.nativeElement.querySelector('[data-testid="holdings-no-match"]')).not.toBeNull();

      component.clearHoldingsFilters();
      expect(component.visiblePositions().length).toBe(2);
    });

    it('keeps the totals for every holding while filtered', async () => {
      await create();
      const total = component.totalMarketValue();

      component.holdingsPnl.set('gain');

      expect(component.totalMarketValue()).toBe(total);
    });
  });
});
