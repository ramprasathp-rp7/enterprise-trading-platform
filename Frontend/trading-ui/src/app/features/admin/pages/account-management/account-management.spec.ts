import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminAccountManagementComponent } from './account-management';

describe('AccountManagement', () => {
  let component: AdminAccountManagementComponent;
  let fixture: ComponentFixture<AdminAccountManagementComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminAccountManagementComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminAccountManagementComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
