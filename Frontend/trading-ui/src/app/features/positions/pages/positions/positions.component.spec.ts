import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { of, throwError } from 'rxjs';
import { PositionsComponent } from './positions.component';
import { AccountsService } from '../../../../api/generated/trade';
import { PortfolioService, PortfolioSummary, PricedPosition } from '../../../../api/generated/porfolio';
import { CoreAuthService } from '../../../../core/auth/core-auth.service';
import { StatementPdfService } from '../../../../shared/statement-pdf.service';

describe('PositionsComponent', () => {
  let fixture: ComponentFixture<PositionsComponent>;
  let component: PositionsComponent;
  let accounts: { getAccount: ReturnType<typeof vi.fn> };
  let portfolio: { getPricedPositions: ReturnType<typeof vi.fn>; getPortfolioSummary: ReturnType<typeof vi.fn> };
  let currentUser: ReturnType<typeof vi.fn>;
  let statementPdf: { download: ReturnType<typeof vi.fn> };

  const positions: PricedPosition[] = [
    {
      accountId: 7, symbol: 'AAPL', quantity: 100, averageCost: 182.4, costBasis: 18240,
      lastPrice: 190, marketValue: 19000, unrealisedPnl: 760, unrealisedPnlPercent: 4.17,
      currency: 'USD', priceAsOf: '2026-10-06T09:45:00Z', stale: false,
    },
    {
      accountId: 7, symbol: 'MSFT', quantity: 20, averageCost: 351.1, costBasis: 7022,
      lastPrice: 340, marketValue: 6800, unrealisedPnl: -222, unrealisedPnlPercent: -3.16,
      currency: 'USD', priceAsOf: '2026-10-06T09:40:00Z', stale: false,
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
  });

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
    expect(text('summary-unrealised')).toBe('+538.00');
    expect(text('summary-realised')).toBe('-120.50');
    expect(text('summary-cash')).toBe('5,000.00');
    expect(fixture.nativeElement.querySelector('[data-testid="summary-unrealised"]').classList).toContain('pnl-up');
    expect(fixture.nativeElement.querySelector('[data-testid="summary-realised"]').classList).toContain('pnl-down');
    expect(fixture.nativeElement.querySelector('[data-testid="pricing-warning"]')).toBeNull();
  });

  it('shows last price, market value and signed unrealised P&L per holding', async () => {
    await create();

    const rows = fixture.nativeElement.querySelectorAll('tbody tr.transaction-row');
    expect(rows[0].textContent).toContain('190.00');
    expect(rows[0].textContent).toContain('19,000.00');
    expect(rows[0].textContent).toContain('+760.00');
    expect(rows[1].textContent).toContain('-222.00');
    expect(rows[1].textContent).toContain('-3.16%');
  });

  it('flags unpriced holdings without showing stale badges or the pricing banner', async () => {
    portfolio.getPricedPositions.mockReturnValue(of([
      { ...positions[0], stale: true },
      { ...positions[1], lastPrice: null, marketValue: null, unrealisedPnl: null, unrealisedPnlPercent: null, priceAsOf: null, stale: true },
    ]));
    portfolio.getPortfolioSummary.mockReturnValue(of({ ...summary, partial: true }));
    await create();

    expect(fixture.nativeElement.querySelectorAll('[data-testid="stale-badge"]').length).toBe(0);
    expect(fixture.nativeElement.querySelectorAll('[data-testid="unpriced-badge"]').length).toBe(1);
    expect(fixture.nativeElement.querySelector('[data-testid="pricing-warning"]')).toBeNull();
    expect(component.stalePositionCount()).toBe(2);
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
      expect(def.summary).toEqual([['Open positions', '2'], ['Total units', '120'], ['Total cost basis', '25,262.00']]);
      expect(def.columns.map((c: any) => c.header)).toEqual(['Symbol', 'Quantity', 'Average cost', 'Cost basis']);
      expect(def.rows[0]).toEqual(['AAPL', '100', '182.40', '18,240.00']);
      expect(def.footerRow).toEqual(['Total', '120', '', '25,262.00']);
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
});
