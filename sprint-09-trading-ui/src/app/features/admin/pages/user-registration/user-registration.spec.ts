import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminUserRegistrationComponent } from './user-registration';

describe('AdminUserRegistrationComponent', () => {
  let component: AdminUserRegistrationComponent;
  let fixture: ComponentFixture<AdminUserRegistrationComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminUserRegistrationComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminUserRegistrationComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
