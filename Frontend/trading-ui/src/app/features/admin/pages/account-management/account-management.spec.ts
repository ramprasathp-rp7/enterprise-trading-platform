import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AccountManagement } from './account-management';

describe('AccountManagement', () => {
  let component: AccountManagement;
  let fixture: ComponentFixture<AccountManagement>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AccountManagement],
    }).compileComponents();

    fixture = TestBed.createComponent(AccountManagement);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
