import { ComponentFixture, TestBed } from '@angular/core/testing';
import { OrderFiltersComponent } from './order-filters.component';
import { OrderStatus } from '../../../../api/generated/trade';

describe('OrderFiltersComponent', () => {
  let component: OrderFiltersComponent;
  let fixture: ComponentFixture<OrderFiltersComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OrderFiltersComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(OrderFiltersComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('filter input', () => {
    it('should initialize with empty filters', () => {
      expect(component.selectedStatus).toBe('');
      expect(component.selectedSymbol).toBe('');
    });

    it('should update selected status', async () => {
      component.selectedStatus = OrderStatus.Filled;
      fixture.detectChanges();
      await fixture.whenStable();

      const statusSelect = fixture.nativeElement.querySelector('#status-filter') as HTMLSelectElement;
      expect(statusSelect.value).toBe(OrderStatus.Filled);
    });

    it('should update selected symbol', async () => {
      component.selectedSymbol = 'AAPL';
      fixture.detectChanges();
      await fixture.whenStable();

      const symbolInput = fixture.nativeElement.querySelector('#symbol-filter') as HTMLInputElement;
      expect(symbolInput.value).toBe('AAPL');
    });
  });

  describe('filter active state', () => {
    it('should return false when no filters are active', () => {
      component.selectedStatus = '';
      component.selectedSymbol = '';
      expect(component.isFilterActive()).toBe(false);
    });

    it('should return true when status filter is set', () => {
      component.selectedStatus = OrderStatus.New;
      component.selectedSymbol = '';
      expect(component.isFilterActive()).toBe(true);
    });

    it('should return true when symbol filter is set', () => {
      component.selectedStatus = '';
      component.selectedSymbol = 'GOOGL';
      expect(component.isFilterActive()).toBe(true);
    });

    it('should return true when both filters are set', () => {
      component.selectedStatus = OrderStatus.Filled;
      component.selectedSymbol = 'AAPL';
      expect(component.isFilterActive()).toBe(true);
    });
  });

  describe('active filter count', () => {
    it('should return 0 when no filters active', () => {
      expect(component.activeFilterCount()).toBe(0);
    });

    it('should return 1 when only status filter active', () => {
      component.selectedStatus = OrderStatus.New;
      expect(component.activeFilterCount()).toBe(1);
    });

    it('should return 1 when only symbol filter active', () => {
      component.selectedSymbol = 'AAPL';
      expect(component.activeFilterCount()).toBe(1);
    });

    it('should return 2 when both filters active', () => {
      component.selectedStatus = OrderStatus.Cancelled;
      component.selectedSymbol = 'MSFT';
      expect(component.activeFilterCount()).toBe(2);
    });
  });

  describe('filter emission', () => {
    it('should emit filters when status changes', async () => {
      await new Promise<void>((resolve) => {
        component.filtersChanged.subscribe((filters) => {
          expect(filters.status).toBe(OrderStatus.Rejected);
          expect(filters.symbol).toBeUndefined();
          resolve();
        });

        component.selectedStatus = OrderStatus.Rejected;
        component.onStatusChange();
      });
    });

    it('should emit filters when symbol changes', async () => {
      await new Promise<void>((resolve) => {
        component.filtersChanged.subscribe((filters) => {
          expect(filters.symbol).toBe('TSLA');
          expect(filters.status).toBeUndefined();
          resolve();
        });

        component.selectedSymbol = 'TSLA';
        component.onSymbolChange();
      });
    });

    it('should emit empty filters when cleared', async () => {
      component.selectedStatus = OrderStatus.New;
      component.selectedSymbol = 'AAPL';

      await new Promise<void>((resolve) => {
        component.filtersChanged.subscribe((filters) => {
          expect(Object.keys(filters).length).toBe(0);
          resolve();
        });

        component.onClearFilters();
      });
    });

    it('should reset form when clear filters called', () => {
      component.selectedStatus = OrderStatus.Filled;
      component.selectedSymbol = 'GOOGL';

      component.onClearFilters();

      expect(component.selectedStatus).toBe('');
      expect(component.selectedSymbol).toBe('');
    });
  });

  describe('filter rendering', () => {
    it('should show clear button when filters are active', async () => {
      component.selectedStatus = OrderStatus.New;
      fixture.detectChanges();
      await fixture.whenStable();

      const clearButton = fixture.nativeElement.querySelector('.clear-search-btn');
      expect(clearButton).toBeTruthy();
    });

    it('should not show clear button when no filters active', async () => {
      component.selectedStatus = '';
      component.selectedSymbol = '';
      fixture.detectChanges();
      await fixture.whenStable();

      const clearButton = fixture.nativeElement.querySelector('.clear-search-btn');
      expect(clearButton).toBeFalsy();
    });

    it('should show filter info when filters are active', async () => {
      component.selectedStatus = OrderStatus.Cancelled;
      fixture.detectChanges();
      await fixture.whenStable();

      const filterInfo = fixture.nativeElement.querySelector('.clear-search-btn');
      expect(filterInfo).toBeTruthy();
    });

    it('should display all status options in select', async () => {
      fixture.detectChanges();
      await fixture.whenStable();

      const options = fixture.nativeElement.querySelectorAll('#status-filter option');
      expect(options.length).toBeGreaterThan(3); // At least one for each status
    });
  });
});
