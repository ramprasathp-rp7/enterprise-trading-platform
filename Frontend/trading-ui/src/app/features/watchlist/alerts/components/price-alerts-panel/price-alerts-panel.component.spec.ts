import { ComponentFixture, TestBed } from '@angular/core/testing';
import { computed, signal } from '@angular/core';
import { Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { PriceAlertsPanelComponent } from './price-alerts-panel.component';
import { PriceAlertsStore } from '../../price-alerts.store';
import { LiveQuoteStore } from '../../../../../core/market-data/live-quote.store';
import { AlertDirection, AlertMode, PriceAlertResponse, PriceAlertTriggerResponse } from '../../../../../api/generated/watchlist';

const alert = (over: Partial<PriceAlertResponse>): PriceAlertResponse => ({
  alertId: 1,
  accountId: 1,
  symbol: 'AAPL',
  direction: AlertDirection.Above,
  threshold: 190,
  mode: AlertMode.Once,
  active: true,
  triggerCount: 0,
  createdAt: '2026-10-08T10:00:00Z',
  updatedAt: '2026-10-08T10:00:00Z',
  ...over,
} as PriceAlertResponse);

const trigger: PriceAlertTriggerResponse = {
  triggerId: 7,
  alertId: 2,
  symbol: 'MSFT',
  direction: AlertDirection.Below,
  threshold: 500,
  mode: AlertMode.EveryQuote,
  price: 498,
  quoteAt: '2026-10-09T09:59:00Z',
  triggeredAt: '2026-10-09T10:00:00Z',
  reason: 'MSFT 498 <= 500',
  deliveryStatus: 'DELIVERED' as PriceAlertTriggerResponse.DeliveryStatusEnum,
};

describe('PriceAlertsPanelComponent', () => {
  let fixture: ComponentFixture<PriceAlertsPanelComponent>;
  let component: PriceAlertsPanelComponent;
  const alerts = signal<PriceAlertResponse[]>([]);
  const loading = signal(false);
  const loaded = signal(true);
  const error = signal<string | null>(null);
  let store: {
    alerts: typeof alerts;
    isLoading: typeof loading;
    isLoaded: typeof loaded;
    error: typeof error;
    activeCount: ReturnType<typeof computed<number>>;
    atCap: ReturnType<typeof computed<boolean>>;
    load: ReturnType<typeof vi.fn>;
    setActive: ReturnType<typeof vi.fn>;
    delete: ReturnType<typeof vi.fn>;
    triggers: ReturnType<typeof vi.fn>;
  };

  const q = (selector: string) => fixture.nativeElement.querySelector(selector) as HTMLElement | null;
  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  beforeEach(async () => {
    alerts.set([
      alert({ alertId: 1 }),
      alert({ alertId: 2, symbol: 'MSFT', direction: AlertDirection.Below, threshold: 500, mode: AlertMode.EveryQuote,
        triggerCount: 3, lastTriggeredAt: '2026-10-09T10:00:00Z', lastTriggeredPrice: 498 }),
      alert({ alertId: 3, active: false, deactivatedReason: 'TRIGGERED_ONCE', triggerCount: 1,
        lastTriggeredAt: '2026-10-09T09:00:00Z', lastTriggeredPrice: 191 }),
      alert({ alertId: 4, active: false, deactivatedReason: 'PAUSED' }),
      alert({ alertId: 5, symbol: 'TSLA' }),
    ]);
    loading.set(false);
    loaded.set(true);
    error.set(null);
    store = {
      alerts,
      isLoading: loading,
      isLoaded: loaded,
      error,
      activeCount: computed(() => alerts().filter((a) => a.active).length),
      atCap: computed(() => alerts().filter((a) => a.active).length >= 20),
      load: vi.fn(),
      setActive: vi.fn(() => of(alert({}))),
      delete: vi.fn(() => of(undefined)),
      triggers: vi.fn(() => of([trigger])),
    };

    await TestBed.configureTestingModule({
      imports: [PriceAlertsPanelComponent],
      providers: [
        { provide: PriceAlertsStore, useValue: store },
        { provide: LiveQuoteStore, useValue: { quote: (s: string) => (s === 'AAPL' ? { price: 192.5 } : null) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PriceAlertsPanelComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('symbols', ['AAPL', 'MSFT']);
    fixture.detectChanges();
  });

  it('loads on open and shows only the alerts on this watchlist', () => {
    expect(store.load).toHaveBeenCalled();
    expect(component.visible().map((a) => a.alertId)).toEqual([1, 2, 3, 4]);
    expect(q('[data-testid="alert-row-5"]')).toBeNull();
    expect(text()).toContain(`${store.activeCount()}/`);
  });

  it('labels each alert by its state and shows the live price and condition', () => {
    expect(component.statusLabel(alerts()[0])).toBe('Active');
    expect(component.statusLabel(alerts()[2])).toBe('Triggered');
    expect(component.statusLabel(alerts()[3])).toBe('Paused');
    expect(component.livePrice('AAPL')).toBe(192.5);
    expect(component.livePrice('MSFT')).toBeNull();
    expect(q('[data-testid="alert-row-1"]')?.textContent).toContain('≥');
    expect(q('[data-testid="alert-row-2"]')?.textContent).toContain('≤');
    expect(q('[data-testid="alert-row-2"]')?.textContent).toContain('Every update');
    expect(q('[data-testid="alert-row-2"]')?.textContent).toContain('3×');
    expect(q('[data-testid="alert-row-1"]')?.textContent).toContain('Not yet');
  });

  it('pauses and resumes, reporting the result', () => {
    q('[data-testid="alert-toggle-1"]')!.click();
    fixture.detectChanges();

    expect(store.setActive).toHaveBeenCalledWith(1, false);
    expect(text()).toContain('Paused the AAPL alert.');

    component.setActive(alerts()[3], true);
    expect(component.message()?.text).toBe('Resumed the AAPL alert.');
    expect(component.busyId()).toBeNull();
  });

  it('reports a failed pause and lets the message be dismissed', () => {
    store.setActive.mockReturnValue(throwError(() => new Error('Active alert limit reached')));

    component.setActive(alerts()[0], false);
    fixture.detectChanges();

    expect(component.message()).toEqual({ text: 'Active alert limit reached', error: true });
    (q('.alert-close') as HTMLButtonElement).click();
    expect(component.message()).toBeNull();
  });

  it('shows the trigger history and closes it again', () => {
    component.toggleHistory(alerts()[1]);
    fixture.detectChanges();

    expect(store.triggers).toHaveBeenCalledWith(2);
    expect(q('.history-row')?.textContent).toContain('MSFT 498 <= 500');
    expect(q('.history-row')?.textContent).toContain('delivered');

    component.toggleHistory(alerts()[1]);
    expect(component.historyFor()).toBeNull();
  });

  it('shows a spinner while history loads and an error if it fails', () => {
    const pending = new Subject<PriceAlertTriggerResponse[]>();
    store.triggers.mockReturnValue(pending);
    component.toggleHistory(alerts()[1]);
    fixture.detectChanges();
    expect(q('.history-row')?.textContent).toContain('Loading history');

    pending.error(new Error('History unavailable'));
    expect(component.history()).toEqual([]);
    expect(component.message()?.error).toBe(true);
  });

  it('deletes an alert and closes its open history', () => {
    component.toggleHistory(alerts()[1]);

    component.remove(alerts()[1]);

    expect(store.delete).toHaveBeenCalledWith(2);
    expect(component.historyFor()).toBeNull();
    expect(component.message()?.text).toBe('Deleted the MSFT alert.');

    store.delete.mockReturnValue(throwError(() => new Error('Not found')));
    component.remove(alerts()[0]);
    expect(component.message()).toEqual({ text: 'Not found', error: true });
  });

  it('shows loading, a retryable error, and an empty state', () => {
    loading.set(true);
    loaded.set(false);
    fixture.detectChanges();
    expect(text()).toContain('Loading alerts');

    loading.set(false);
    error.set('Could not load alerts');
    fixture.detectChanges();
    (q('[data-testid="alerts-load-error"] button') as HTMLButtonElement).click();
    expect(store.load).toHaveBeenCalledTimes(2);

    error.set(null);
    fixture.componentRef.setInput('symbols', ['NVDA']);
    fixture.detectChanges();
    expect(text()).toContain('No alerts on this watchlist');
  });
});
