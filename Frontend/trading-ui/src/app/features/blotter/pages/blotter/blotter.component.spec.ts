import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BlotterComponent } from './blotter.component';
import { BlotterStore } from '../../blotter.store';
import { CoreAuthService } from '../../../../core/auth/core-auth.service';
import { OrderHistoryEntry, OrderStatus } from '../../../../api/generated/trade';
import { vi } from 'vitest';
import { provideRouter } from '@angular/router';

describe('BlotterComponent', () => {
  let component: BlotterComponent;
  let fixture: ComponentFixture<BlotterComponent>;
  let mockBlotterStore: any;
  let mockAuthService: any;

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
  ];

  beforeEach(async () => {
    mockBlotterStore = {
      orders: vi.fn(() => mockOrders),
      filteredOrders: vi.fn(() => mockOrders),
      isLoading: vi.fn(() => false),
      isPolling: vi.fn(() => true),
      pollAttempts: vi.fn(() => 5),
      lastError: vi.fn(() => null),
      hasNewOrders: vi.fn(() => true),
      loadOrderHistory: vi.fn(),
      stopPolling: vi.fn(),
      refreshOrderHistory: vi.fn(),
      clearOrders: vi.fn(),
      cancellingIds: vi.fn(() => new Set<string>()),
      cancelResult: vi.fn(() => null),
      cancelOrder: vi.fn(),
      clearCancelResult: vi.fn(),
    };

    mockAuthService = {
      currentUser: vi.fn(() => ({ accountId: 123 })),
      accessToken: vi.fn(() => null),
      decodeJWT: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [BlotterComponent],
      providers: [
        provideRouter([]),
        { provide: BlotterStore, useValue: mockBlotterStore },
        { provide: CoreAuthService, useValue: mockAuthService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(BlotterComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('order history rendering', () => {
    it('renders order history newest first including rejections', () => {
      fixture.detectChanges();

      const orders = component.orders();
      expect(orders.length).toBe(2);
      expect(orders[0].orderId).toBe('ord-001');
      expect(orders[1].orderId).toBe('ord-002');
    });

    it('handles empty history gracefully', () => {
      mockBlotterStore.orders.mockReturnValue([]);

      fixture.detectChanges();

      const orders = component.orders();
      expect(orders.length).toBe(0);
    });
  });

  describe('polling behavior', () => {
    it('shows polling status when orders are at NEW', () => {
      mockBlotterStore.hasNewOrders.mockReturnValue(true);
      mockBlotterStore.isPolling.mockReturnValue(true);

      fixture.detectChanges();

      expect(component.hasNewOrders()).toBe(true);
      expect(component.isPolling()).toBe(true);
    });

    it('stops polling when nothing is at NEW', () => {
      mockBlotterStore.hasNewOrders.mockReturnValue(false);
      mockBlotterStore.isPolling.mockReturnValue(false);

      fixture.detectChanges();

      expect(component.hasNewOrders()).toBe(false);
      expect(component.isPolling()).toBe(false);
    });
  });

  describe('initialization', () => {
    it('loads order history on init with current user', () => {
      component.ngOnInit();

      expect(mockBlotterStore.loadOrderHistory).toHaveBeenCalledWith(123);
    });

    it('stops polling on destroy', () => {
      component.ngOnDestroy();

      expect(mockBlotterStore.stopPolling).toHaveBeenCalled();
    });

    it('handles missing account ID gracefully', () => {
      mockAuthService.currentUser.mockReturnValue(null);

      component.ngOnInit();

      expect(mockBlotterStore.loadOrderHistory).not.toHaveBeenCalled();
    });
  });

  describe('refresh button', () => {
    it('calls store refresh on manual refresh', () => {
      component.onRefresh();

      expect(mockBlotterStore.refreshOrderHistory).toHaveBeenCalled();
    });
  });

  describe('computed signals', () => {
    it('exposes all required store signals', () => {
      expect(component.orders()).toEqual(mockOrders);
      expect(component.isLoading()).toBe(false);
      expect(component.isPolling()).toBe(true);
      expect(component.pollAttempts()).toBe(5);
      expect(component.lastError()).toBeNull();
      expect(component.hasNewOrders()).toBe(true);
    });
  });

  describe('cancel order', () => {
    it('delegates a confirmed cancel to the store', () => {
      component.onCancelOrder('order-1');

      expect(mockBlotterStore.cancelOrder).toHaveBeenCalledWith('order-1');
    });

    it('renders the cancel result banner and dismisses it', () => {
      mockBlotterStore.cancelResult.mockReturnValue({ type: 'error', orderId: 'order-1', message: 'Too late' });
      fixture = TestBed.createComponent(BlotterComponent);
      fixture.detectChanges();

      const banner = fixture.nativeElement.querySelector('[data-testid="cancel-result"]');
      expect(banner.textContent).toContain('Cancel failed');
      expect(banner.textContent).toContain('Too late');

      banner.querySelector('.alert-close').click();
      expect(mockBlotterStore.clearCancelResult).toHaveBeenCalled();
    });
  });

  describe('account from the token, states and filters', () => {
    it('falls back to the account in the access token', () => {
      mockAuthService.currentUser.mockReturnValue(null);
      mockAuthService.accessToken.mockReturnValue('jwt');
      mockAuthService.decodeJWT.mockReturnValue({ accountId: 456 });

      component.ngOnInit();

      expect(mockBlotterStore.loadOrderHistory).toHaveBeenCalledWith(456);
    });

    it('skips loading when the token cannot be read or has no account', () => {
      const error = vi.spyOn(console, 'error').mockImplementation(() => undefined);
      mockAuthService.currentUser.mockReturnValue(null);
      mockAuthService.accessToken.mockReturnValue('jwt');
      mockAuthService.decodeJWT.mockReturnValueOnce({}).mockImplementationOnce(() => {
        throw new Error('bad token');
      });

      component.ngOnInit();
      component.ngOnInit();

      expect(mockBlotterStore.loadOrderHistory).not.toHaveBeenCalled();
      expect(error).toHaveBeenCalled();
    });

    it('shows a load error and the loading state', () => {
      mockBlotterStore.lastError.mockReturnValue('Service unavailable');
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('Unable to load orders');

      mockBlotterStore.lastError.mockReturnValue(null);
      mockBlotterStore.isLoading.mockReturnValue(true);
      mockBlotterStore.orders.mockReturnValue([]);
      const loading = TestBed.createComponent(BlotterComponent);
      loading.detectChanges();
      expect(loading.nativeElement.textContent).toContain('Loading order history');
    });

    it('passes filter changes to the store', () => {
      mockBlotterStore.setFilters = vi.fn();

      component.onFiltersChanged({ symbol: 'AAPL' });

      expect(mockBlotterStore.setFilters).toHaveBeenCalledWith({ symbol: 'AAPL' });
    });
  });
});
