import { ComponentFixture, TestBed } from '@angular/core/testing';
import { OrderListComponent } from './order-list.component';
import { OrderHistoryEntry, OrderStatus } from '../../../../api/generated/trade';

describe('OrderListComponent', () => {
  let component: OrderListComponent;
  let fixture: ComponentFixture<OrderListComponent>;

  const mockOrders: OrderHistoryEntry[] = [
    {
      orderId: 'ord-001',
      accountId: 123,
      symbol: 'AAPL',
      side: 'BUY',
      quantity: 100,
      price: 150.5,
      executedPrice: null,
      status: OrderStatus.New,
      createdOn: '2026-10-01T10:00:00Z',
    },
    {
      orderId: 'ord-002',
      accountId: 123,
      symbol: 'GOOGL',
      side: 'SELL',
      quantity: 50,
      price: 2800.0,
      executedPrice: 2799.5,
      status: OrderStatus.Filled,
      createdOn: '2026-10-01T09:00:00Z',
    },
    {
      orderId: 'ord-003',
      accountId: 123,
      symbol: 'MSFT',
      side: 'BUY',
      quantity: 75,
      price: 420.0,
      executedPrice: null,
      status: OrderStatus.Rejected,
      createdOn: '2026-10-01T08:00:00Z',
    },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OrderListComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(OrderListComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('order list rendering', () => {
    it('renders all orders when provided', () => {
      component.orders = mockOrders as any;
      fixture.detectChanges();

      const rows = fixture.nativeElement.querySelectorAll('tbody tr');
      expect(rows.length).toBe(3);
    });

    it('renders orders newest first', () => {
      component.orders = mockOrders as any;
      fixture.detectChanges();

      const rows = fixture.nativeElement.querySelectorAll('tbody tr');
      expect(rows.length).toBe(3);
    });

    it('includes rejected orders in the list', () => {
      component.orders = mockOrders as any;
      fixture.detectChanges();

      const rows = fixture.nativeElement.querySelectorAll('tbody tr');
      const hasRejected = mockOrders.some(o => o.status === OrderStatus.Rejected);
      expect(rows.length).toBe(3);
      expect(hasRejected).toBe(true);
    });

    it('renders table headers', () => {
      component.orders = mockOrders as any;
      fixture.detectChanges();

      const headers = fixture.nativeElement.querySelectorAll('th');
      const headerTexts = Array.from(headers).map((h: any) => h.textContent);
      expect(headerTexts).toContain('Order ID');
      expect(headerTexts).toContain('Symbol');
      expect(headerTexts).toContain('Side');
      expect(headerTexts).toContain('Status');
    });
  });

  describe('empty state handling', () => {
    it('displays empty message when no orders', () => {
      component.orders = [];
      fixture.detectChanges();

      const emptyState = fixture.nativeElement.querySelector('.empty-state');
      expect(emptyState).toBeTruthy();
      expect(emptyState.textContent).toContain('No orders yet');
    });

    it('shows empty message when orders is null', () => {
      component.orders = null;
      fixture.detectChanges();

      const emptyState = fixture.nativeElement.querySelector('.empty-state');
      expect(emptyState).toBeTruthy();
    });

    it('hides table when empty', () => {
      component.orders = [];
      fixture.detectChanges();

      const table = fixture.nativeElement.querySelector('table');
      expect(table).toBeFalsy();
    });

    it('shows table when orders present', () => {
      component.orders = mockOrders as any;
      fixture.detectChanges();

      const table = fixture.nativeElement.querySelector('table');
      expect(table).toBeTruthy();
    });
  });

  describe('table structure', () => {
    it('has correct table headers', () => {
      component.orders = mockOrders as any;
      fixture.detectChanges();

      const headers = fixture.nativeElement.querySelectorAll('th');
      const headerTexts = Array.from(headers).map((h: any) => h.textContent.trim());
      
      expect(headerTexts[0]).toContain('Order ID');
      expect(headerTexts[1]).toContain('Symbol');
      expect(headerTexts[2]).toContain('Side');
      expect(headerTexts[3]).toContain('Quantity');
    });

    it('renders tbody with order rows', () => {
      component.orders = mockOrders as any;
      fixture.detectChanges();

      const tbody = fixture.nativeElement.querySelector('tbody');
      expect(tbody).toBeTruthy();
      const rows = tbody.querySelectorAll('tr');
      expect(rows.length).toBe(mockOrders.length);
    });
  });

  describe('cancel action', () => {
    beforeEach(() => {
      component.orders = mockOrders as any;
      fixture.detectChanges();
    });

    it('shows a Cancel button only for NEW orders', () => {
      const buttons = fixture.nativeElement.querySelectorAll('[data-testid="cancel-order-btn"]');
      const newCount = mockOrders.filter((o) => o.status === OrderStatus.New).length;
      expect(buttons.length).toBe(newCount);
    });

    it('asks for confirmation before emitting the order ID', () => {
      const emitted: string[] = [];
      component.cancelOrder.subscribe((id: string) => emitted.push(id));

      fixture.nativeElement.querySelector('[data-testid="cancel-order-btn"]').click();
      fixture.detectChanges();
      expect(emitted).toEqual([]);

      fixture.nativeElement.querySelector('[data-testid="confirm-cancel-btn"]').click();
      fixture.detectChanges();

      const newOrder = mockOrders.find((o) => o.status === OrderStatus.New)!;
      expect(emitted).toEqual([newOrder.orderId]);
      expect(fixture.nativeElement.querySelector('[data-testid="confirm-cancel-btn"]')).toBeNull();
    });

    it('shows progress instead of the button while cancelling', () => {
      const newOrder = mockOrders.find((o) => o.status === OrderStatus.New)!;
      fixture.componentRef.setInput('cancellingIds', new Set([newOrder.orderId]));
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('[data-testid="cancel-order-btn"]')).toBeNull();
      expect(fixture.nativeElement.querySelector('.cancel-progress').textContent).toContain('Cancelling');
    });
  });

  describe('date and money formatting', () => {
    it('formats ISO strings and epoch values, and keeps unreadable text', () => {
      expect(component.formatDate('2026-10-09T10:00:00Z')).toContain('2026');
      expect(component.formatDate(1791540000)).toContain('2026');
      expect(component.formatDate(1791540000000)).toContain('2026');
      expect(component.formatDate('later')).toBe('later');
      expect(component.formatDate(null)).toBe('');
      expect(component.formatDate('  ')).toBe('');
    });

    it('splits a timestamp into date and time, or keeps the raw text', () => {
      const parts = component.formatDateParts('2026-10-09T10:00:00Z');
      expect(parts?.date).toContain('2026');
      expect(parts?.time).toMatch(/\d/);
      expect(component.formatDateParts('later')).toEqual({ date: 'later', time: '' });
      expect(component.formatDateParts(undefined)).toBeNull();
    });

    it('formats money in the given currency', () => {
      expect(component.formatCurrency(1600, 'INR')).toContain('1,600.00');
    });
  });
});
