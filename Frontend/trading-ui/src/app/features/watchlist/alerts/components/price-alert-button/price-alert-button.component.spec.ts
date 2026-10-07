import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { firstValueFrom, of } from 'rxjs';
import { AlertDirection, AlertMode, PriceAlertsService } from '../../../../../api/generated/watchlist';
import { CoreAuthService } from '../../../../../core/auth/core-auth.service';
import { LiveQuoteStore } from '../../../../../core/market-data/live-quote.store';
import { PriceAlertButtonComponent } from './price-alert-button.component';

describe('PriceAlertButtonComponent', () => {
  let api: Record<string, ReturnType<typeof vi.fn>>;
  let fixture: ComponentFixture<PriceAlertButtonComponent>;
  let el: HTMLElement;

  beforeEach(async () => {
    api = {
      getPriceAlerts: vi.fn(() => of([])),
      createPriceAlert: vi.fn((_: number, body: { symbol: string; direction: AlertDirection; threshold: number; mode: AlertMode }) =>
        of({ alertId: 1, accountId: 7, active: true, triggerCount: 0, createdAt: '', updatedAt: '', ...body }),
      ),
    };
    await TestBed.configureTestingModule({
      imports: [PriceAlertButtonComponent],
      providers: [
        { provide: PriceAlertsService, useValue: api },
        { provide: LiveQuoteStore, useValue: { quote: () => ({ price: 191.25 }) } },
        { provide: CoreAuthService, useValue: { currentUser: () => ({ accountId: 7 }) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PriceAlertButtonComponent);
    fixture.componentRef.setInput('symbol', 'AAPL');
    fixture.detectChanges();
    el = fixture.nativeElement;
  });

  it('opens with the live price as the threshold and creates the alert', async () => {
    (el.querySelector('[data-testid="alert-btn-AAPL"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const threshold = el.querySelector('[data-testid="alert-threshold"]') as HTMLInputElement;
    expect(Number(threshold.value)).toBe(191.25);

    (el.querySelector('[data-testid="alert-create"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(api['createPriceAlert']).toHaveBeenCalledWith(7, {
      symbol: 'AAPL',
      direction: AlertDirection.Above,
      threshold: 191.25,
      mode: AlertMode.Once,
    });
    expect(el.textContent).toContain('Alert set: AAPL above 191.25.');
  });

  it('marks the bell when the symbol already has an active alert', async () => {
    const component = fixture.componentInstance;
    await firstValueFrom(component.store.create({ symbol: 'AAPL', direction: AlertDirection.Below, threshold: 150, mode: AlertMode.Once }));
    fixture.detectChanges();

    expect(el.querySelector('[data-testid="alert-btn-AAPL"]')!.classList).toContain('armed');
  });
});
