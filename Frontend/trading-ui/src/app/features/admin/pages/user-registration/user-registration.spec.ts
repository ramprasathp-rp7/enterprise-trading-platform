import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { FormControl } from '@angular/forms';
import { AdminUserRegistrationComponent } from './user-registration';
import { environment } from '../../../../core/config/environment';
import { of, throwError } from 'rxjs';
import { AdminNotificationsService } from '../../../../api/generated/preferences';

describe('AdminUserRegistrationComponent', () => {
  let component: AdminUserRegistrationComponent;
  let fixture: ComponentFixture<AdminUserRegistrationComponent>;
  let http: HttpTestingController;
  let adminNotifications: { notifyCustomerRegistered: ReturnType<typeof vi.fn> };
  const registerUrl = `${environment.AUTH_SERVICE_URL}/auth/register`;

  const fillValidForm = () =>
    component.form.setValue({
      username: 'jdoe',
      password: 'a-very-long-password',
      accountId: 1001,
    });

  beforeEach(async () => {
    vi.useFakeTimers();
    adminNotifications = { notifyCustomerRegistered: vi.fn(() => of({ channels: ['IN_APP'] })) };
    await TestBed.configureTestingModule({
      imports: [AdminUserRegistrationComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AdminNotificationsService, useValue: adminNotifications },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminUserRegistrationComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });

  it('should create with no role control on the form', () => {
    expect(component).toBeTruthy();
    expect(component.form.get('roles')).toBeNull();
    expect(component.form.valid).toBe(false);
  });

  it('enforces username and password length rules', () => {
    component.form.patchValue({ username: 'ab', password: 'short' });
    expect(component.form.get('username')?.hasError('minlength')).toBe(true);
    expect(component.form.get('password')?.hasError('minlength')).toBe(true);

    fillValidForm();
    expect(component.form.valid).toBe(true);
  });

  it('refuses to submit an invalid form and clears the message after 5s', () => {
    component.onSubmit();

    expect(component.errorMessage()).toBe('Please fill all required fields correctly');
    http.expectNone(registerUrl);

    vi.advanceTimersByTime(5000);
    expect(component.errorMessage()).toBe('');
  });

  it('registers the user, resets the form and shows a success message', () => {
    const notify = vi.spyOn(TestBed.inject(NotificationService), 'notify');
    fillValidForm();

    component.onSubmit();
    expect(component.loading()).toBe(true);

    const req = http.expectOne(registerUrl);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      username: 'jdoe',
      password: 'a-very-long-password',
      accountId: 1001,
      roles: ['CUSTOMER'],
    });
    req.flush({});

    expect(component.loading()).toBe(false);
    expect(component.successMessage()).toBe("User 'jdoe' registered successfully!");
    expect(component.form.value.username).toBeNull();
    expect(adminNotifications.notifyCustomerRegistered).toHaveBeenCalledWith({ username: 'jdoe', accountId: 1001 });
    expect(notify).toHaveBeenCalledWith({
      kind: 'user-registered',
      title: 'Registration successful',
      message: "Customer 'jdoe' was registered for account 1001.",
      link: { path: '/admin/register' },
    });

    vi.advanceTimersByTime(5000);
    expect(component.successMessage()).toBe('');
  });

  it('sends the notice to the admin channels and leaves the bell alone when in-app was not chosen', () => {
    const notify = vi.spyOn(TestBed.inject(NotificationService), 'notify');
    adminNotifications.notifyCustomerRegistered.mockReturnValueOnce(of({ channels: ['EMAIL', 'SMS'] }));
    fillValidForm();

    component.onSubmit();
    http.expectOne(registerUrl).flush({});

    expect(adminNotifications.notifyCustomerRegistered).toHaveBeenCalledTimes(1);
    expect(notify).not.toHaveBeenCalled();
    expect(component.successMessage()).toBe("User 'jdoe' registered successfully!");
  });

  it('still puts the notice in the bell when the notification service cannot be reached', () => {
    const notify = vi.spyOn(TestBed.inject(NotificationService), 'notify');
    adminNotifications.notifyCustomerRegistered.mockReturnValueOnce(throwError(() => new Error('down')));
    fillValidForm();

    component.onSubmit();
    http.expectOne(registerUrl).flush({});

    expect(notify).toHaveBeenCalledWith(expect.objectContaining({ kind: 'user-registered' }));
  });

  it('sends no notice when registration fails', () => {
    fillValidForm();

    component.onSubmit();
    http.expectOne(registerUrl).flush({ message: 'Username already exists' }, { status: 409, statusText: 'Conflict' });

    expect(adminNotifications.notifyCustomerRegistered).not.toHaveBeenCalled();
  });

  it('shows the server message when registration fails', () => {
    fillValidForm();

    component.onSubmit();
    http.expectOne(registerUrl).flush({ message: 'Username already exists' }, { status: 409, statusText: 'Conflict' });

    expect(component.loading()).toBe(false);
    expect(component.errorMessage()).toBe('Username already exists');
    expect(component.successMessage()).toBe('');
  });

  it('falls back to a generic failure message', () => {
    fillValidForm();

    component.onSubmit();
    http.expectOne(registerUrl).flush(null, { status: 500, statusText: 'Server Error' });

    expect(component.errorMessage()).toBe('Registration failed');
  });

  it('offers no way to pick the Admin role', () => {
    expect(fixture.nativeElement.querySelector('input[value="ADMIN"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('input[type="checkbox"]')).toBeNull();
    const role = fixture.nativeElement.querySelector('[data-testid="registered-role"]');
    expect(role.textContent).toContain('Customer');
    expect(role.textContent).not.toContain('Admin');
  });

  it('always registers the user as CUSTOMER only, even if extra values are injected', () => {
    fillValidForm();
    (component.form as any).addControl('roles', new FormControl(['ADMIN']));

    component.onSubmit();

    const req = http.expectOne(registerUrl);
    expect(req.request.body.roles).toEqual(['CUSTOMER']);
    req.flush({});
  });

  it('toggles password visibility', () => {
    const input: HTMLInputElement = fixture.nativeElement.querySelector('#password');
    expect(input.type).toBe('password');

    fixture.nativeElement.querySelector('.toggle-password').click();
    fixture.detectChanges();

    expect(component.showPassword()).toBe(true);
    expect(input.type).toBe('text');

    component.togglePassword();
    expect(component.showPassword()).toBe(false);
  });

  it('cancels the pending message timer on destroy', () => {
    component.onSubmit();
    expect(component.errorMessage()).not.toBe('');

    fixture.destroy();
    vi.advanceTimersByTime(5000);

    expect(component.errorMessage()).not.toBe('');
  });

  describe('validation messages', () => {
    const errors = (): string[] =>
      Array.from(fixture.nativeElement.querySelectorAll('.error')).map((e: any) => e.textContent.trim());
    const invalidInputs = () => fixture.nativeElement.querySelectorAll('input.is-invalid').length;

    it('shows no errors before the admin has entered anything', () => {
      expect(errors()).toEqual([]);
      expect(invalidInputs()).toBe(0);
    });

    it("shows a field's error only after the field is left empty or too short", () => {
      const username: HTMLInputElement = fixture.nativeElement.querySelector('#username');

      username.dispatchEvent(new Event('blur'));
      fixture.detectChanges();
      expect(errors()).toEqual(['Username is required']);
      expect(username.classList).toContain('is-invalid');

      username.value = 'ab';
      username.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      expect(errors()).toEqual(['Username must be at least 3 characters']);
    });

    it('reveals every error when an invalid form is submitted', () => {
      fixture.nativeElement.querySelector('button[type="submit"]').click();
      fixture.detectChanges();

      expect(errors()).toEqual(['Username is required', 'Password is required', 'Account ID is required']);
      expect(invalidInputs()).toBe(3);
      http.expectNone(registerUrl);
    });

    it('clears the errors again after a successful registration resets the form', () => {
      component.form.markAllAsTouched();
      fillValidForm();
      component.onSubmit();
      http.expectOne(registerUrl).flush({});
      fixture.detectChanges();

      expect(errors()).toEqual([]);
      expect(invalidInputs()).toBe(0);
    });
  });
});
