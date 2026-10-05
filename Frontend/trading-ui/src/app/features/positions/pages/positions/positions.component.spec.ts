import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { of, throwError } from 'rxjs';
import { PositionsComponent } from './positions.component';
import { AccountsService, PositionResponse } from '../../../../api/generated/trade';
import { CoreAuthService } from '../../../../core/auth/core-auth.service';
import { StatementPdfService } from '../../../../shared/statement-pdf.service';

describe('PositionsComponent', () => {
  let fixture: ComponentFixture<PositionsComponent>;
  let component: PositionsComponent;
  let accounts: { getPositions: ReturnType<typeof vi.fn>; getAccount: ReturnType<typeof vi.fn> };
  let currentUser: ReturnType<typeof vi.fn>;
  let statementPdf: { download: ReturnType<typeof vi.fn> };

  const positions: PositionResponse[] = [
    { symbol: 'AAPL', quantity: 100, averageCost: 182.4 } as PositionResponse,
    { symbol: 'MSFT', quantity: 20, averageCost: 351.1 } as PositionResponse,
  ];

  async function create(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [PositionsComponent],
      providers: [
        provideRouter([]),
        { provide: AccountsService, useValue: accounts },
        { provide: CoreAuthService, useValue: { currentUser } },
        { provide: StatementPdfService, useValue: statementPdf },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(PositionsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(() => {
    accounts = { getPositions: vi.fn(() => of(positions)), getAccount: vi.fn(() => of({ id: 7, accountId: 'ACC-000007', holderName: 'Rahul Kumar', cashBalance: 1, status: 'ACTIVE', version: 1, lastUpdated: '' })) };
    currentUser = vi.fn(() => ({ accountId: 7, username: 'rkumar' }));
    statementPdf = { download: vi.fn().mockResolvedValue(undefined) };
  });

  it('loads positions and renders a row per holding with cost basis', async () => {
    await create();

    expect(accounts.getPositions).toHaveBeenCalledWith(7);
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
    const tiles = fixture.nativeElement.querySelectorAll('.stat-value');
    expect(tiles[0].textContent.trim()).toBe('2');
    expect(tiles[1].textContent.trim()).toBe('120');
  });

  it('shows the empty state when there are no positions', async () => {
    accounts.getPositions.mockReturnValue(of(null));
    await create();

    expect(component.positions()).toEqual([]);
    expect(fixture.nativeElement.querySelector('.empty-state').textContent).toContain('No active positions');
  });

  it('shows an error without calling the API when there is no account', async () => {
    currentUser.mockReturnValue(null);
    await create();

    expect(accounts.getPositions).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('.alert-error').textContent).toContain('No account found');
  });

  it('shows an error when the request fails', async () => {
    accounts.getPositions.mockReturnValue(throwError(() => new Error('down')));
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
