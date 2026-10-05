import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';

import { AdminShell } from './admin-shell';

describe('AdminShell', () => {
  let component: AdminShell;
  let fixture: ComponentFixture<AdminShell>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RouterTestingModule, AdminShell],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminShell);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
