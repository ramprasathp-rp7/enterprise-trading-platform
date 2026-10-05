import { ComponentFixture, TestBed } from '@angular/core/testing';
import { OrderStatusBadgeComponent } from './order-status-badge.component';
import { OrderStatus } from '../../../../api/generated/trade';

describe('OrderStatusBadgeComponent', () => {
  let component: OrderStatusBadgeComponent;
  let fixture: ComponentFixture<OrderStatusBadgeComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OrderStatusBadgeComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(OrderStatusBadgeComponent);
    component = fixture.componentInstance;
  });

  describe('status badge display', () => {
    it('displays NEW status with correct styling', () => {
      component.status = OrderStatus.New;
      fixture.detectChanges();

      const element = fixture.nativeElement.querySelector('span');
      expect(element.textContent.trim()).toBe('NEW');
      expect(element.classList.contains('status-new')).toBe(true);
    });

    it('displays FILLED status with correct styling', () => {
      component.status = OrderStatus.Filled;
      fixture.detectChanges();

      const element = fixture.nativeElement.querySelector('span');
      expect(element.textContent.trim()).toBe('FILLED');
      expect(element.classList.contains('status-filled')).toBe(true);
    });

    it('displays REJECTED status with correct styling', () => {
      component.status = OrderStatus.Rejected;
      fixture.detectChanges();

      const element = fixture.nativeElement.querySelector('span');
      expect(element.textContent.trim()).toBe('REJECTED');
      expect(element.classList.contains('status-rejected')).toBe(true);
    });

    it('displays CANCELLED status with correct styling', () => {
      component.status = OrderStatus.Cancelled;
      fixture.detectChanges();

      const element = fixture.nativeElement.querySelector('span');
      expect(element.textContent.trim()).toBe('CANCELLED');
      expect(element.classList.contains('status-cancelled')).toBe(true);
    });
  });

  describe('getStatusClass method', () => {
    it('returns correct class for NEW', () => {
      component.status = OrderStatus.New;
      expect(component.getStatusClass()).toBe('status-new');
    });

    it('returns correct class for FILLED', () => {
      component.status = OrderStatus.Filled;
      expect(component.getStatusClass()).toBe('status-filled');
    });

    it('returns correct class for REJECTED', () => {
      component.status = OrderStatus.Rejected;
      expect(component.getStatusClass()).toBe('status-rejected');
    });

    it('returns correct class for CANCELLED', () => {
      component.status = OrderStatus.Cancelled;
      expect(component.getStatusClass()).toBe('status-cancelled');
    });

    it('defaults to NEW for unknown status', () => {
      component.status = 'UNKNOWN' as OrderStatus;
      expect(component.getStatusClass()).toBe('status-new');
    });
  });

  describe('accessibility', () => {
    it('includes aria-label with status text', () => {
      component.status = OrderStatus.Filled;
      fixture.detectChanges();

      const element = fixture.nativeElement.querySelector('span');
      expect(element.getAttribute('aria-label')).toBe('FILLED');
    });

    it('includes text content for color-blind accessibility', () => {
      component.status = OrderStatus.Rejected;
      fixture.detectChanges();

      const element = fixture.nativeElement.querySelector('span');
      // Text should be visible (not just color-based)
      expect(element.textContent.trim()).toBe('REJECTED');
    });
  });
});

