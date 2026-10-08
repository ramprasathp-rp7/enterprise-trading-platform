import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { PositionsOverviewComponent } from './positions-overview.component';
import { AccountsService, PositionResponse } from '../../../../api/generated/trade';

describe('PositionsOverviewComponent', () => {
  let fixture: ComponentFixture<PositionsOverviewComponent>;
  let component: PositionsOverviewComponent;
  let accounts: { getPositions: ReturnType<typeof vi.fn> };

  const positions: PositionResponse[] = [
    { symbol: 'MSFT', quantity: 100, averageCost: 350 } as PositionResponse,
    { symbol: 'AAPL', quantity: 300, averageCost: 180 } as PositionResponse,
    { symbol: 'NVDA', quantity: 50, averageCost: 470 } as PositionResponse,
    { symbol: 'INFY.NS', quantity: 30, averageCost: 1500 } as PositionResponse,
    { symbol: 'EURUSD', quantity: 20, averageCost: 1.08 } as PositionResponse,
  ];

  async function create(accountId: number | null = 7): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [PositionsOverviewComponent],
      providers: [provideRouter([]), { provide: AccountsService, useValue: accounts }],
    }).compileComponents();
    fixture = TestBed.createComponent(PositionsOverviewComponent);
    component = fixture.componentInstance;
    component.accountId = accountId;
    fixture.detectChanges();
  }

  beforeEach(() => {
    accounts = { getPositions: vi.fn(() => of(positions)) };
  });

  afterEach(() => {
    delete (window as any).__CURRENT_ACCOUNT_ID__;
  });

  it('loads positions for the given account and hands them to the dashboard', async () => {
    const emitted: unknown[] = [];
    await TestBed.configureTestingModule({
      imports: [PositionsOverviewComponent],
      providers: [provideRouter([]), { provide: AccountsService, useValue: accounts }],
    }).compileComponents();
    fixture = TestBed.createComponent(PositionsOverviewComponent);
    component = fixture.componentInstance;
    component.accountId = 7;
    component.positionsLoaded.subscribe((p) => emitted.push(p));
    fixture.detectChanges();

    expect(emitted).toEqual([positions]);

    expect(accounts.getPositions).toHaveBeenCalledWith(7);
    expect(component.positionsCount()).toBe(5);
    expect(component.totalQuantity()).toBe(500);
    expect(component.loading()).toBe(false);
  });

  it('falls back to the window account id when no input is given', async () => {
    (window as any).__CURRENT_ACCOUNT_ID__ = 42;

    await create(null);

    expect(accounts.getPositions).toHaveBeenCalledWith(42);
  });

  it('does not load without any account id', async () => {
    await create(null);

    expect(accounts.getPositions).not.toHaveBeenCalled();
    expect(component.allocation()).toEqual([]);
    expect(component.topHolding()).toBeNull();
  });

  it('builds allocation segments by unit share, largest first, with palette colours', async () => {
    await create();

    const allocation = component.allocation();
    expect(allocation.map((a) => a.symbol)).toEqual(['AAPL', 'MSFT', 'NVDA', 'INFY.NS', 'EURUSD']);
    expect(allocation[0].percentage).toBeCloseTo(60);
    expect(allocation.reduce((sum, a) => sum + a.percentage, 0)).toBeCloseTo(100);
    expect(allocation.every((a) => /^#[0-9a-f]{6}$/i.test(a.color))).toBe(true);
    // The dashboard lists the four largest, each with a share bar in its colour
    expect(component.rows().map((r) => r.symbol)).toEqual(['AAPL', 'MSFT', 'NVDA', 'INFY.NS']);
    expect(component.rows()[0]).toEqual(expect.objectContaining({ averageCost: 180, color: allocation[0].color }));
    const fills: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('.share-fill'));
    expect(fills.length).toBe(4);
    expect(fills[0].style.width).toBe('60%');
    expect(fixture.nativeElement.querySelector('[data-testid="holding-AAPL"]').textContent).toContain('60.0%');
  });

  it('returns no allocation when every quantity is zero', async () => {
    accounts.getPositions.mockReturnValue(of([{ symbol: 'X', quantity: 0, averageCost: 1 }]));
    await create();

    expect(component.allocation()).toEqual([]);
    expect(fixture.nativeElement.querySelector('[data-testid="holdings-empty"]')).not.toBeNull();
  });

  it('drops fully sold positions from the holdings and from what it hands the dashboard', async () => {
    const withClosed = [
      ...positions.slice(0, 2),
      { symbol: 'FX:EURUSD', quantity: 0, averageCost: 0.925 } as PositionResponse,
      { symbol: 'TSLA', quantity: 0, averageCost: 337 } as PositionResponse,
    ];
    accounts.getPositions.mockReturnValue(of(withClosed));
    const emitted: PositionResponse[][] = [];
    await TestBed.configureTestingModule({
      imports: [PositionsOverviewComponent],
      providers: [provideRouter([]), { provide: AccountsService, useValue: accounts }],
    }).compileComponents();
    fixture = TestBed.createComponent(PositionsOverviewComponent);
    component = fixture.componentInstance;
    component.accountId = 7;
    component.positionsLoaded.subscribe((p) => emitted.push(p));
    fixture.detectChanges();

    expect(component.rows().map((r) => r.symbol)).toEqual(['AAPL', 'MSFT']);
    expect(component.positionsCount()).toBe(2);
    expect(emitted[0].map((p) => p.symbol)).toEqual(['MSFT', 'AAPL']);
    expect(fixture.nativeElement.querySelector('[data-testid="holding-TSLA"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="holding-FX:EURUSD"]')).toBeNull();
  });

  it('picks the largest holding and lists the rest after it', async () => {
    await create();

    expect(component.topHolding()?.symbol).toBe('AAPL');
    expect(component.otherHoldings().map((p) => p.symbol)).toEqual(['MSFT', 'NVDA', 'INFY.NS', 'EURUSD']);
    expect(component.tableHoldings()[0].symbol).toBe('AAPL');
    expect(component.topFour().length).toBe(4);
    expect(component.tableRows().map((p) => p.symbol)).toEqual(['AAPL', 'MSFT', 'NVDA', 'INFY.NS']);
    // The compact dashboard lists the largest holding first in the table (no separate card)
    expect(fixture.nativeElement.querySelector('.top-symbol')).toBeNull();
    const rows = fixture.nativeElement.querySelectorAll('.mini-table tbody tr');
    expect(rows.length).toBe(4);
    expect(rows[0].textContent).toContain('AAPL');
  });

  it('has no other holdings when there is a single position', async () => {
    accounts.getPositions.mockReturnValue(of([positions[0]]));
    await create();

    expect(component.otherHoldings()).toEqual([]);
    expect(component.tableHoldings().map((p) => p.symbol)).toEqual(['MSFT']);
  });

  it('treats a null response as no positions', async () => {
    accounts.getPositions.mockReturnValue(of(null));
    await create();

    expect(component.positions()).toEqual([]);
    expect(component.topFour()).toEqual([]);
    expect(component.tableHoldings()).toEqual([]);
  });

  it('shows an error when positions fail to load', async () => {
    accounts.getPositions.mockReturnValue(throwError(() => new Error('down')));
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});

    await create();

    expect(component.error()).toBe('Could not load positions');
    expect(component.loading()).toBe(false);
    expect(fixture.nativeElement.querySelector('.holdings-state.error').textContent).toContain('Could not load positions');
    consoleError.mockRestore();
  });

  it('formats amounts and gives each symbol a stable palette colour', async () => {
    await create();

    expect(component.formatAmount(1234.5)).toBe('1,234.50');
    expect(component.formatAmount(1234.5, 0)).toBe('1,235');
    const colour = component.paletteIndex(positions[0]);
    expect(colour).toMatch(/^#[0-9a-f]{6}$/i);
    expect(component.paletteIndex(positions[0])).toBe(colour);
  });
});
