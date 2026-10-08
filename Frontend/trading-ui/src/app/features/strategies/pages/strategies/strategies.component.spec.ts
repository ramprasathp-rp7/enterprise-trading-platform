import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { STRATEGIES_POLL_MS, StrategiesComponent } from './strategies.component';
import { StrategiesService, StrategyExecutionResponse, StrategyResponse } from '../../../../api/generated/strategy';
import { CoreAuthService } from '../../../../core/auth/core-auth.service';
import { InstrumentCatalogService } from '../../../instruments/services/instrument-catalog.service';
import { StrategiesStore } from '../../strategies.store';
import { NotificationService } from '../../../../core/notifications/notification.service';

describe('StrategiesComponent', () => {
  let fixture: ComponentFixture<StrategiesComponent>;
  let component: StrategiesComponent;
  let api: Record<string, ReturnType<typeof vi.fn>>;

  const strategy = (over: Partial<StrategyResponse> = {}): StrategyResponse => ({
    strategyId: 9,
    symbol: 'AAPL',
    strategyType: 'MA_CROSSOVER',
    shortWindow: 5,
    longWindow: 20,
    tradeSide: 'BOTH',
    quantity: 10,
    maxPosition: 50,
    maxDailyNotional: 5000,
    maxConsecutiveFailures: 3,
    active: true,
    lastState: 'BELOW',
    lastEvaluatedQuoteAt: '2026-10-07T14:00:00Z',
    consecutiveFailures: 0,
    description: 'Buy 10 AAPL when the 5-point average crosses above the 20-point average; sell 10 when it crosses below.',
    createdAt: '2026-10-07T13:00:00Z',
    updatedAt: '2026-10-07T13:00:00Z',
    ...over,
  });

  const execution: StrategyExecutionResponse = {
    executionId: 1,
    strategyId: 9,
    symbol: 'AAPL',
    quoteAt: '2026-10-07T14:05:00Z',
    signal: 'BUY',
    price: 150.1,
    quantity: 10,
    notional: 1501,
    indicators: { shortMa: 150, longMa: 149 },
    reason: '5-point average 150.00 crossed above the 20-point average 149.00.',
    outcome: 'PLACED',
    orderId: 'ORD-abc',
    createdAt: '2026-10-07T14:05:01Z',
  };

  const el = (sel: string): HTMLElement | null => fixture.nativeElement.querySelector(sel);
  const text = (sel: string) => el(sel)?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  const click = (sel: string) => {
    el(sel)!.click();
    fixture.detectChanges();
  };

  async function create(strategies: StrategyResponse[] = [strategy()]): Promise<void> {
    api = {
      getStrategies: vi.fn(() => of(strategies)),
      createStrategy: vi.fn((_: number, req: { symbol: string }) => of(strategy({ strategyId: 10, symbol: req.symbol }))),
      updateStrategy: vi.fn((_: number, id: number, body: { active: boolean }) =>
        of(strategy({ strategyId: id, active: body.active, disabledReason: body.active ? null : 'USER' }))),
      deleteStrategy: vi.fn(() => of(undefined)),
      getStrategyExecutions: vi.fn(() => of([execution])),
    };
    await TestBed.configureTestingModule({
      imports: [StrategiesComponent],
      providers: [
        provideRouter([]),
        { provide: StrategiesService, useValue: api },
        { provide: CoreAuthService, useValue: { currentUser: () => ({ accountId: 7, username: 'strategy-spec-user' }) } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(StrategiesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  afterEach(() => {
    vi.useRealTimers();
    localStorage.clear();
  });

  it('lists the account strategies with their rule, state and status', async () => {
    await create();

    expect(api['getStrategies']).toHaveBeenCalledWith(7);
    expect(text('[data-testid="strategy-rule"]')).toContain('crosses above the 20-point average');
    expect(text('[data-testid="strategy-status"]')).toBe('On');
    expect(text('[data-testid="strategy-9"]')).toContain('Short avg below long');
  });

  it('shows an empty state and an auto-stopped strategy', async () => {
    await create([]);
    expect(el('[data-testid="strategies-empty"]')).not.toBeNull();

    TestBed.resetTestingModule();
    await create([strategy({ active: false, disabledReason: 'FAILURES' })]);
    expect(text('[data-testid="strategy-status"]')).toBe('Stopped after 3 failures');
  });

  it('turns a strategy off immediately and back on', async () => {
    await create();

    click('[data-testid="toggle-strategy"]');
    expect(api['updateStrategy']).toHaveBeenCalledWith(7, 9, { active: false });
    expect(text('[data-testid="strategy-status"]')).toBe('Off');

    click('[data-testid="toggle-strategy"]');
    expect(api['updateStrategy']).toHaveBeenLastCalledWith(7, 9, { active: true });
  });

  it('asks before deleting', async () => {
    await create();

    click('[data-testid="delete-strategy"]');
    expect(api['deleteStrategy']).not.toHaveBeenCalled();
    click('[data-testid="confirm-delete"]');

    expect(api['deleteStrategy']).toHaveBeenCalledWith(7, 9);
    expect(el('[data-testid="strategy-9"]')).toBeNull();
  });

  it('shows what a strategy did, with the reason and order', async () => {
    await create();

    click('[data-testid="toggle-executions"]');

    expect(api['getStrategyExecutions']).toHaveBeenCalledWith(7, 9, 50);
    const row = text('[data-testid="execution-1"]');
    expect(row).toContain('BUY');
    expect(row).toContain('Order placed');
    expect(row).toContain('crossed above');
    expect(row).toContain('ORD-abc');
  });

  it('validates the form and requires consent before creating', async () => {
    await create();
    click('[data-testid="new-strategy"]');

    click('[data-testid="create-strategy"]');
    expect(api['createStrategy']).not.toHaveBeenCalled();
    expect(component.errors()['instrument']).toBe('Pick an instrument.');
    expect(component.errors()['consent']).toBeDefined();

    component.pick(TestBed.inject(InstrumentCatalogService).find('MSFT')!);
    component.longWindow.set(4);
    expect(component.errors()['longWindow']).toBe('Must be longer than the short average.');
    component.longWindow.set(20);
    component.consent.set(true);
    fixture.detectChanges();
    expect(text('[data-testid="strategy-preview"]')).toContain('Buy 1 MSFT when the 5-point average crosses above the 20-point average');

    click('[data-testid="create-strategy"]');

    expect(api['createStrategy']).toHaveBeenCalledWith(7, expect.objectContaining({
      symbol: 'MSFT', strategyType: 'MA_CROSSOVER', shortWindow: 5, longWindow: 20, bandWindow: undefined,
      tradeSide: 'BOTH', quantity: 1, maxPosition: 10, maxDailyNotional: 1000, maxConsecutiveFailures: 3,
    }));
    expect(el('[data-testid="strategy-form"]')).toBeNull();
    expect(text('[data-testid="strategy-notice"]')).toContain('Strategy on MSFT is on');
    expect(el('[data-testid="strategy-10"]')).not.toBeNull();

    const pushed = TestBed.inject(NotificationService).notifications().find((n) => n.dedupeKey === 'strategy-created:10');
    expect(pushed?.kind).toBe('strategy-created');
    expect(pushed?.title).toBe('Strategy on MSFT is on');
    expect(pushed?.message).toContain('without asking you');
    expect(pushed?.link).toEqual({ path: '/strategies' });
  });

  it('sends Bollinger settings only for a Bollinger strategy, and shows server errors', async () => {
    await create();
    api['createStrategy'].mockReturnValue(throwError(() => new HttpErrorResponse({
      status: 409, error: { errorCode: 'STR-409', message: 'An active BOLLINGER strategy already trades MSFT' },
    })));
    click('[data-testid="new-strategy"]');
    click('[data-testid="type-bollinger"]');
    component.pick(TestBed.inject(InstrumentCatalogService).find('MSFT')!);
    component.consent.set(true);

    click('[data-testid="create-strategy"]');

    expect(api['createStrategy']).toHaveBeenCalledWith(7, expect.objectContaining({
      strategyType: 'BOLLINGER', bandWindow: 20, bandK: 2, shortWindow: undefined, longWindow: undefined,
    }));
    expect(text('[data-testid="form-error"]')).toBe('An active BOLLINGER strategy already trades MSFT');
    expect(TestBed.inject(NotificationService).notifications().some((n) => n.kind === 'strategy-created')).toBe(false);
  });

  it('refreshes the list and open activity every minute', async () => {
    vi.useFakeTimers();
    await create();
    click('[data-testid="toggle-executions"]');

    vi.advanceTimersByTime(STRATEGIES_POLL_MS);

    expect(api['getStrategies']).toHaveBeenCalledTimes(2);
    expect(api['getStrategyExecutions']).toHaveBeenCalledTimes(2);
  });

  it('explains when the server has no strategy API yet', async () => {
    await create();
    api['getStrategies'].mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404, error: { errorCode: 'VAL-422' } })));

    TestBed.inject(StrategiesStore).load();
    fixture.detectChanges();

    expect(text('[data-testid="strategies-error"]')).toContain('not available on the trading service yet');
  });
});
