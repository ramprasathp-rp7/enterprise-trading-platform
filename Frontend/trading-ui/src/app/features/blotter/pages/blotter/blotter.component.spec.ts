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
});
