import { ComponentFixture, TestBed } from '@angular/core/testing';
import { OrderListRowComponent } from './order-list-row.component';

describe('OrderListRowComponent', () => {
  let component: OrderListRowComponent;
  let fixture: ComponentFixture<OrderListRowComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OrderListRowComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(OrderListRowComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
