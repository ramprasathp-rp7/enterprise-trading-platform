import { ComponentFixture, TestBed } from '@angular/core/testing';
import { OrderListRowComponent } from './order-list-row.component';
import { OrderHistoryEntry, OrderStatus } from '../../../../api/generated/trade';

describe('OrderListRowComponent', () => {
  let component: OrderListRowComponent;
  let fixture: ComponentFixture<OrderListRowComponent>;

  const mockOrder: OrderHistoryEntry = {
    orderId: 'ord-001',
    accountId: 123,
    symbol: 'AAPL',
    side: 'BUY',
    quantity: 100,
    price: 150.5,
    executedPrice: null,
    status: OrderStatus.New,
    createdOn: '2026-10-01T10:00:00Z',
  };

  const filledOrder: OrderHistoryEntry = {
    ...mockOrder,
    status: OrderStatus.Filled,
    executedPrice: 150.5,
    orderId: 'ord-002',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OrderListRowComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(OrderListRowComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('order row rendering', () => {
    it('renders order ID', () => {
      component.order = mockOrder as any;
      fixture.detectChanges();

      const orderIdCell = fixture.nativeElement.querySelector('.order-id');
      expect(orderIdCell.textContent).toBe('ord-001');
    });

    it('renders symbol', () => {
      component.order = mockOrder as any;
      fixture.detectChanges();

      const symbolCell = fixture.nativeElement.querySelector('.symbol');
      expect(symbolCell.textContent).toBe('AAPL');
    });

    it('renders side with correct styling', () => {
      component.order = mockOrder as any;
      fixture.detectChanges();

      const sideCell = fixture.nativeElement.querySelector('.side');
      expect(sideCell.textContent).toBe('BUY');
      expect(sideCell.classList.contains('buy')).toBe(true);
    });

    it('renders SELL side with correct styling', () => {
      component.order = { ...mockOrder, side: 'SELL' } as any;
      fixture.detectChanges();

      const sideCell = fixture.nativeElement.querySelector('.side');
      expect(sideCell.classList.contains('sell')).toBe(true);
    });

    it('renders formatted quantity', () => {
      component.order = mockOrder as any;
      fixture.detectChanges();

      const quantityCell = fixture.nativeElement.querySelector('.quantity');
      expect(quantityCell.textContent).toBe('100');
    });

    it('renders formatted price', () => {
      component.order = mockOrder as any;
      fixture.detectChanges();

      const priceCell = fixture.nativeElement.querySelector('.price');
      expect(priceCell.textContent).toContain('150.50');
    });

    it('renders dash for null executed price', () => {
      component.order = mockOrder as any;
      fixture.detectChanges();

      const executedPriceCell = fixture.nativeElement.querySelector('.executed-price');
      expect(executedPriceCell.textContent).toBe('—');
    });

    it('renders executed price when filled', () => {
      component.order = filledOrder as any;
      fixture.detectChanges();

      const executedPriceCell = fixture.nativeElement.querySelector('.executed-price');
      expect(executedPriceCell.textContent).toContain('150.50');
      expect(executedPriceCell.classList.contains('filled')).toBe(true);
    });

    it('renders status badge', () => {
      component.order = mockOrder as any;
      fixture.detectChanges();

      const badgeComponent = fixture.nativeElement.querySelector('app-order-status-badge');
      expect(badgeComponent).toBeTruthy();
    });

    it('renders formatted date', () => {
      component.order = mockOrder as any;
      fixture.detectChanges();

      const dateCell = fixture.nativeElement.querySelector('.created-on');
      expect(dateCell.textContent).toBeTruthy();
      // parse date text back to Date and compare to input
      const rendered = dateCell.textContent.trim();
      const parsed = new Date(rendered);
      expect(Number.isFinite(parsed.getTime())).toBe(true);
    });
  });

  describe('row styling', () => {
    it('applies new-status class when order is NEW', () => {
      component.order = mockOrder as any;
      fixture.detectChanges();

      const row = fixture.nativeElement.querySelector('tr');
      expect(row.classList.contains('new-status')).toBe(true);
    });

    it('does not apply new-status class when order is FILLED', () => {
      component.order = filledOrder as any;
      fixture.detectChanges();

      const row = fixture.nativeElement.querySelector('tr');
      expect(row.classList.contains('new-status')).toBe(false);
    });
  });

  describe('formatting methods', () => {
    it('formats quantity with locale-specific separators', () => {
      component.order = { ...mockOrder, quantity: 1000 } as any;
      fixture.detectChanges();

      const quantityCell = fixture.nativeElement.querySelector('.quantity');
      expect(quantityCell.textContent).toBe('1,000');
    });

    it('formats currency with two decimal places', () => {
      component.order = { ...mockOrder, price: 1500.5 } as any;
      fixture.detectChanges();

      const priceCell = fixture.nativeElement.querySelector('.price');
      expect(priceCell.textContent).toContain('1,500.50');
    });

    it('formats date in a parseable format', () => {
      component.order = { ...mockOrder, createdOn: '2026-10-15T14:30:45Z' } as any;
      fixture.detectChanges();

      const dateCell = fixture.nativeElement.querySelector('.created-on');
      expect(dateCell.textContent).toBeTruthy();
      const rendered = dateCell.textContent.trim();
      const parsed = new Date(rendered);
      expect(Number.isFinite(parsed.getTime())).toBe(true);
    });
  });

  describe('edge cases', () => {
    it('handles null order gracefully', () => {
      component.order = null;
      fixture.detectChanges();

      const row = fixture.nativeElement.querySelector('tr');
      expect(row).toBeFalsy();
    });

    it('handles undefined executedPrice', () => {
      const orderWithUndefined = { ...mockOrder, executedPrice: undefined } as any;
      component.order = orderWithUndefined;
      fixture.detectChanges();

      const executedPriceCell = fixture.nativeElement.querySelector('.executed-price');
      expect(executedPriceCell.textContent).toBe('—');
    });

    it('handles zero quantity', () => {
      component.order = { ...mockOrder, quantity: 0 } as any;
      fixture.detectChanges();

      const quantityCell = fixture.nativeElement.querySelector('.quantity');
      expect(quantityCell.textContent).toBe('0');
    });
  });

  describe('created timestamp', () => {
    const createdWith = (createdOn: unknown) => {
      component.order = { ...mockOrder, createdOn } as any;
      return component.createdOn;
    };

    it('reads epoch seconds and milliseconds as well as ISO strings', () => {
      expect(createdWith(1791540000)).toContain('2026');
      expect(createdWith(1791540000000)).toContain('2026');
      expect(createdWith('2026-10-09T10:00:00Z')).toContain('Oct');
    });

    it('shows nothing for a missing value and the raw text for an unreadable one', () => {
      expect(createdWith(null)).toBe('');
      expect(createdWith('   ')).toBe('');
      expect(createdWith('not a date')).toBe('not a date');
      component.order = null as any;
      expect(component.createdOn).toBe('');
    });
  });
});
