import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { CredentialFormComponent } from './credential-form.component';
import { LoginCredentials } from '../../../../core/auth/auth.models';

describe('CredentialFormComponent', () => {
  let component: CredentialFormComponent;
  let fixture: ComponentFixture<CredentialFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CredentialFormComponent, FormsModule],
    }).compileComponents();

    fixture = TestBed.createComponent(CredentialFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('Empty field validation', () => {
    it('should require username field', () => {
      component.username.set('');
      component.onUsernameBlur();
      fixture.detectChanges();

      expect(component.usernameErrors().length).toBeGreaterThan(0);
      expect(component.usernameErrors()[0]).toContain('required');
    });

    it('should require password field', () => {
      component.password.set('');
      component.onPasswordBlur();
      fixture.detectChanges();

      expect(component.passwordErrors().length).toBeGreaterThan(0);
      expect(component.passwordErrors()[0]).toContain('required');
    });

    it('should be invalid when both fields are empty', () => {
      component.username.set('');
      component.password.set('');
      component.onSubmit();
      fixture.detectChanges();

      expect(component.isValid()).toBe(false);
      expect(component.hasErrors()).toBe(true);
      expect(component.usernameTouched()).toBe(true);
      expect(component.passwordTouched()).toBe(true);
    });

    it('should be valid when both fields have values', () => {
      component.username.set('priya.menon');
      component.password.set('correct horse battery staple');
      fixture.detectChanges();

      expect(component.usernameErrors().length).toBe(0);
      expect(component.passwordErrors().length).toBe(0);
      expect(component.isValid()).toBe(true);
      expect(component.hasErrors()).toBe(false);
    });
  });

  describe('Form submission', () => {
    it('should emit submitAttempt with valid credentials', async () => {
      const credentials: LoginCredentials = {
        username: 'priya.menon',
        password: 'correct horse battery staple',
      };

      let emitted: LoginCredentials | undefined;
      component.submitAttempt.subscribe((c) => {
        emitted = c;
      });

      component.username.set(credentials.username);
      component.password.set(credentials.password);
      fixture.detectChanges();

      component.onSubmit();
      
      expect(emitted).toEqual(credentials);
    });

    it('should not emit submitAttempt with empty username', async () => {
      let emitted = false;
      component.submitAttempt.subscribe(() => {
        emitted = true;
      });

      component.username.set('');
      component.password.set('password123');
      fixture.detectChanges();

      component.onSubmit();

      expect(emitted).toBe(false);
    });

    it('should not emit submitAttempt with empty password', async () => {
      let emitted = false;
      component.submitAttempt.subscribe(() => {
        emitted = true;
      });

      component.username.set('priya.menon');
      component.password.set('');
      fixture.detectChanges();

      component.onSubmit();

      expect(emitted).toBe(false);
    });
  });

  describe('Form reset', () => {
    it('should clear all form fields', () => {
      component.username.set('priya.menon');
      component.password.set('password123');

      component.reset();

      expect(component.username()).toBe('');
      expect(component.password()).toBe('');
    });

    it('should clear validation errors after reset', () => {
      component.username.set('');
      component.password.set('');
      component.onSubmit();
      fixture.detectChanges();

      expect(component.hasErrors()).toBe(true);

      component.reset();
      fixture.detectChanges();

      expect(component.usernameErrors().length).toBe(0);
      expect(component.passwordErrors().length).toBe(0);
      expect(component.hasErrors()).toBe(false);
    });
  });

  describe('Submit button disabled state', () => {
    it('should disable submit button when form has errors', () => {
      component.username.set('');
      component.password.set('');
      component.onSubmit();
      fixture.detectChanges();

      const button = fixture.nativeElement.querySelector('[data-testid="submit-btn"]');
      expect(button.disabled).toBe(true);
    });

    it('should enable submit button when form is valid', () => {
      component.username.set('priya.menon');
      component.password.set('password123');
      fixture.detectChanges();

      const button = fixture.nativeElement.querySelector('[data-testid="submit-btn"]');
      expect(button.disabled).toBe(false);
    });
  });

  describe('Test identifiers (data-testid)', () => {
    it('should have data-testid on username input', () => {
      const usernameInput = fixture.nativeElement.querySelector('[data-testid="username-input"]');
      expect(usernameInput).toBeTruthy();
      expect(usernameInput.getAttribute('data-testid')).toBe('username-input');
    });

    it('should have data-testid on password input', () => {
      const passwordInput = fixture.nativeElement.querySelector('[data-testid="password-input"]');
      expect(passwordInput).toBeTruthy();
      expect(passwordInput.getAttribute('data-testid')).toBe('password-input');
    });

    it('should have data-testid on submit button', () => {
      const submitBtn = fixture.nativeElement.querySelector('[data-testid="submit-btn"]');
      expect(submitBtn).toBeTruthy();
      expect(submitBtn.getAttribute('data-testid')).toBe('submit-btn');
    });
  });

  describe('CSS classes for styling', () => {
    it('should add is-invalid class to username input when invalid', () => {
      component.username.set('');
      component.onUsernameBlur();
      fixture.detectChanges();

      const usernameInput = fixture.nativeElement.querySelector('[data-testid="username-input"]');
      expect(usernameInput.classList.contains('is-invalid')).toBe(true);
    });

    it('should add is-invalid class to password input when invalid', () => {
      component.password.set('');
      component.onPasswordBlur();
      fixture.detectChanges();

      const passwordInput = fixture.nativeElement.querySelector('[data-testid="password-input"]');
      expect(passwordInput.classList.contains('is-invalid')).toBe(true);
    });

    it('should not add is-invalid class when field is valid', () => {
      component.username.set('priya.menon');
      fixture.detectChanges();

      const usernameInput = fixture.nativeElement.querySelector('[data-testid="username-input"]');
      expect(usernameInput.classList.contains('is-invalid')).toBe(false);
    });
  });
});

