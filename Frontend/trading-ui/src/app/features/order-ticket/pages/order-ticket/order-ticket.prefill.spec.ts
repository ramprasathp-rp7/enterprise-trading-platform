import { describe, it, expect, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { OrderSide } from '../../../../api/generated/trade';
import { OrderTicketStore } from '../../order-ticket.store';
import { OrderTicketComponent } from './order-ticket.component';

/** Prefilling the ticket from query params (Positions → Buy / Sell). */
describe('OrderTicketComponent – prefill from a holding', () => {
  async function open(queryParams: Record<string, string>) {
    await TestBed.configureTestingModule({
      imports: [OrderTicketComponent],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(queryParams) } } },
        {
          provide: OrderTicketStore,
          useValue: {
            accountId: signal(101),
            accountDisplayId: signal('CUST-000101'),
            isSubmitting: signal(false),
            submissionResult: signal(null),
            errorCode: signal(null),
            errorMessage: signal(null),
            clearErrors: vi.fn(),
            clearSubmissionResult: vi.fn(),
            submitOrder: vi.fn(),
          },
        },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(OrderTicketComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  it('fills the instrument and selects SELL', async () => {
    const fixture = await open({ symbol: 'aapl', side: 'sell' });
    const component = fixture.componentInstance;

    expect(component.symbol()).toBe('AAPL');
    expect(component.side()).toBe(OrderSide.Sell);
    expect(component.prefilledFrom()).toBe('AAPL');
    expect((fixture.nativeElement.querySelector('[data-testid="symbol-input"]') as HTMLInputElement).value).toBe('AAPL');
    expect(fixture.nativeElement.querySelector('[data-testid="side-sell"]').classList).toContain('active');
    expect(fixture.nativeElement.querySelector('[data-testid="prefill-hint"]').textContent).toContain('AAPL holding');
  });

  it('fills the instrument and keeps BUY', async () => {
    const fixture = await open({ symbol: 'INFY.NS', side: 'BUY' });

    expect(fixture.componentInstance.symbol()).toBe('INFY.NS');
    expect(fixture.componentInstance.side()).toBe(OrderSide.Buy);
  });

  it('ignores an unknown side and trims an over-long symbol to the field limit', async () => {
    const fixture = await open({ symbol: '  abcdefghijklmnopqrstuvwxyz  ', side: 'short' });

    expect(fixture.componentInstance.side()).toBe(OrderSide.Buy);
    expect(fixture.componentInstance.symbol()).toBe('ABCDEFGHIJKLMNOPQRST');
  });

  it('shows validation instead of the hint for a symbol that is not tradeable', async () => {
    const fixture = await open({ symbol: 'bad symbol!' });

    expect(fixture.componentInstance.symbolErrors().length).toBeGreaterThan(0);
    expect(fixture.nativeElement.querySelector('[data-testid="prefill-hint"]')).toBeNull();
  });

  it('leaves the ticket empty without query params', async () => {
    const fixture = await open({});

    expect(fixture.componentInstance.symbol()).toBe('');
    expect(fixture.componentInstance.prefilledFrom()).toBeNull();
    expect(fixture.componentInstance.side()).toBe(OrderSide.Buy);
  });

  describe('prefill hint', () => {
    const type = async (fixture: any, value: string) => {
      const input: HTMLInputElement = fixture.nativeElement.querySelector('[data-testid="symbol-input"]');
      input.value = value;
      input.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
    };
    const hint = (fixture: any) => fixture.nativeElement.querySelector('[data-testid="prefill-hint"]');

    it('disappears when the instrument field is erased', async () => {
      const fixture = await open({ symbol: 'MSFT', side: 'SELL' });
      expect(hint(fixture)).not.toBeNull();

      await type(fixture, '');

      expect(fixture.componentInstance.prefilledFrom()).toBeNull();
      expect(hint(fixture)).toBeNull();
    });

    it('stays gone when the same symbol is typed back in', async () => {
      const fixture = await open({ symbol: 'MSFT' });

      await type(fixture, '');
      await type(fixture, 'MSFT');

      expect(hint(fixture)).toBeNull();
    });

    it('disappears when a different symbol is typed', async () => {
      const fixture = await open({ symbol: 'MSFT' });

      await type(fixture, 'AAPL');

      expect(hint(fixture)).toBeNull();
    });

    it('stays when only the letter case changes', async () => {
      const fixture = await open({ symbol: 'MSFT' });

      await type(fixture, 'msft');

      expect(fixture.componentInstance.prefilledFrom()).toBe('MSFT');
      expect(hint(fixture)).not.toBeNull();
    });
  });
});
