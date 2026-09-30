import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SignInErrorMessageComponent } from './sign-in-error-message.component';

describe('AuthErrorMessageComponent', () => {
  let component: AuthErrorMessageComponent;
  let fixture: ComponentFixture<AuthErrorMessageComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AuthErrorMessageComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(AuthErrorMessageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
