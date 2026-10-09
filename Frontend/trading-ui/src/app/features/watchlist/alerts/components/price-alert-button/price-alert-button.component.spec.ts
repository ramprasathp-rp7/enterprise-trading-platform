import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { firstValueFrom, of, throwError } from 'rxjs';
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
    expect(el.textContent).toContain('Alert set: AAPL above $191.25.');
  });

  it('marks the bell when the symbol already has an active alert', async () => {
    const component = fixture.componentInstance;
    await firstValueFrom(component.store.create({ symbol: 'AAPL', direction: AlertDirection.Below, threshold: 150, mode: AlertMode.Once }));
    fixture.detectChanges();

    expect(el.querySelector('[data-testid="alert-btn-AAPL"]')!.classList).toContain('armed');
  });

  describe('closing and failures', () => {
    const open = () => {
      fixture.componentInstance.toggle();
      fixture.detectChanges();
    };

    it('closes on an outside click, Escape, resize or a page scroll, but not a scroll inside it', () => {
      const component = fixture.componentInstance;

      open();
      component.onDocumentClick({ target: el } as unknown as MouseEvent);
      expect(component.open()).toBe(true);
      component.onDocumentClick({ target: document.body } as unknown as MouseEvent);
      expect(component.open()).toBe(false);

      open();
      component.onEscape();
      expect(component.open()).toBe(false);

      open();
      component.onViewportChange();
      expect(component.open()).toBe(false);

      open();
      el.dispatchEvent(new Event('scroll'));
      expect(component.open()).toBe(true);
      document.dispatchEvent(new Event('scroll'));
      expect(component.open()).toBe(false);
    });

    it('does not submit an invalid threshold and shows a failed create', () => {
      const component = fixture.componentInstance;
      component.threshold = -5;
      component.submit();
      expect(api['createPriceAlert']).not.toHaveBeenCalled();

      api['createPriceAlert'].mockReturnValue(throwError(() => new Error('Too many alerts')));
      component.threshold = 200;
      component.submit();
      expect(component.message()?.error).toBe(true);
      expect(component.busy()).toBe(false);
    });
  });
});
