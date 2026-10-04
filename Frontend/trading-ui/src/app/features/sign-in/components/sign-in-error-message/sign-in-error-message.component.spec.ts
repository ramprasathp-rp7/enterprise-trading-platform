import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SignInErrorMessageComponent } from './sign-in-error-message.component';

describe('SignInErrorMessageComponent', () => {
  let component: SignInErrorMessageComponent;
  let fixture: ComponentFixture<SignInErrorMessageComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SignInErrorMessageComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(SignInErrorMessageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('Error message display', () => {
    it('should display error message when message is provided', () => {
      fixture.componentRef.setInput('message', 'Invalid username or password');
      fixture.detectChanges();

      const alert = fixture.nativeElement.querySelector('.alert');
      expect(alert).toBeTruthy();
      expect(alert.textContent).toContain('Invalid username or password');
    });

    it('should not display anything when message is null', () => {
      fixture.componentRef.setInput('message', null);
      fixture.detectChanges();

      const alert = fixture.nativeElement.querySelector('.alert');
      expect(alert).toBeFalsy();
    });

    it('should not display anything when message is empty', () => {
      fixture.componentRef.setInput('message', '');
      fixture.detectChanges();

      const alert = fixture.nativeElement.querySelector('.alert');
      expect(alert).toBeFalsy();
    });

    it('should display error code if provided', () => {
      fixture.componentRef.setInput('message', 'Invalid username or password');
      fixture.componentRef.setInput('errorCode', 'AUTH-401');
      fixture.detectChanges();

      const errorCode = fixture.nativeElement.querySelector('.error-code');
      expect(errorCode).toBeTruthy();
      expect(errorCode.textContent).toBe('AUTH-401');
    });

    it('should not display error code section if errorCode is null', () => {
      fixture.componentRef.setInput('message', 'Invalid username or password');
      fixture.componentRef.setInput('errorCode', null);
      fixture.detectChanges();

      const errorCode = fixture.nativeElement.querySelector('.error-code');
      expect(errorCode).toBeFalsy();
    });
  });

  describe('isVisible property', () => {
    it('should return true when message is provided', () => {
      fixture.componentRef.setInput('message', 'Test error');
      fixture.detectChanges();
      expect(component.isVisible()).toBe(true);
    });

    it('should return false when message is null', () => {
      fixture.componentRef.setInput('message', null);
      fixture.detectChanges();
      expect(component.isVisible()).toBe(false);
    });

    it('should return false when message is empty string', () => {
      fixture.componentRef.setInput('message', '');
      fixture.detectChanges();
      expect(component.isVisible()).toBe(false);
    });
  });

  describe('Alert styling', () => {
    it('should have alert-danger class for error styling', () => {
      fixture.componentRef.setInput('message', 'Error message');
      fixture.detectChanges();

      const alert = fixture.nativeElement.querySelector('.alert');
      expect(alert.classList.contains('alert-danger')).toBe(true);
    });

    it('should have role="alert" for accessibility', () => {
      fixture.componentRef.setInput('message', 'Error message');
      fixture.detectChanges();

      const alert = fixture.nativeElement.querySelector('.alert');
      expect(alert.getAttribute('role')).toBe('alert');
    });
  });

  describe('Dynamic content updates', () => {
    it('should update display when message changes', () => {
      fixture.componentRef.setInput('message', null);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.alert')).toBeFalsy();

      fixture.componentRef.setInput('message', 'New error message');
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.alert')).toBeTruthy();
      expect(fixture.nativeElement.textContent).toContain('New error message');
    });

    it('should update error code when errorCode changes', () => {
      fixture.componentRef.setInput('message', 'Error');
      fixture.componentRef.setInput('errorCode', 'AUTH-401');
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.error-code').textContent).toBe('AUTH-401');

      fixture.componentRef.setInput('errorCode', 'VAL-422');
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.error-code').textContent).toBe('VAL-422');
    });
  });
});
