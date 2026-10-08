import { Component, OnDestroy, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { AuthService, RegisterRequest, Role } from '../../../../api/generated/auth';
import { AdminNotificationsService, AlertChannel } from '../../../../api/generated/preferences';

/** Admins may only create customer users from this page; the role is not user-selectable. */
export const REGISTERED_USER_ROLES: readonly Role[] = [Role.Customer];

@Component({
  selector: 'app-admin-user-registration',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './user-registration.html',
  styleUrls: ['./user-registration.css'],
})
export class AdminUserRegistrationComponent implements OnInit, OnDestroy {
  private fb = inject(FormBuilder);
  private authApi = inject(AuthService);
  private notifications = inject(NotificationService);
  private adminNotifications = inject(AdminNotificationsService);
  private messageTimeoutId: ReturnType<typeof setTimeout> | null = null;

  form!: FormGroup;
  loading = signal<boolean>(false);
  successMessage = signal<string>('');
  errorMessage = signal<string>('');
  showPassword = signal<boolean>(false);

  ngOnInit(): void {
    this.form = this.fb.group({
      username: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(64)]],
      password: ['', [Validators.required, Validators.minLength(12), Validators.maxLength(128)]],
      accountId: ['', Validators.required],
    });
  }

  ngOnDestroy(): void {
    this.clearMessageTimer();
  }

  /** A field is shown as invalid only once the admin has left it or tried to submit */
  isInvalid(field: string): boolean {
    const control = this.form.get(field);
    return !!control && control.invalid && control.touched;
  }

  /** Show a specific validation message under the same rule as isInvalid() */
  showError(field: string, error: string): boolean {
    const control = this.form.get(field);
    return !!control && control.touched && control.hasError(error);
  }

  onSubmit(): void {
    if (this.form.invalid) {
      // Reveal every field's error now that a submit was attempted
      this.form.markAllAsTouched();
      this.showErrorMessage('Please fill all required fields correctly');
      return;
    }

    this.loading.set(true);
    this.clearMessages();

    const dto: RegisterRequest = { ...this.form.value, roles: [...REGISTERED_USER_ROLES] };

    this.authApi.register(dto).subscribe({
      next: () => {
        this.showSuccessMessage(`User '${dto.username}' registered successfully!`);
        this.announceRegistration(dto.username, Number(dto.accountId));
        this.form.reset();
        this.loading.set(false);
      },
      error: (err) => {
        this.showErrorMessage(err.error?.message || 'Registration failed');
        this.loading.set(false);
      },
    });
  }

  /**
   * Tell the admin on the channels they chose in their preferences: the server sends any email or
   * SMS and says whether in-app was chosen. If it can't be reached, the bell still gets the notice.
   */
  private announceRegistration(username: string, accountId: number): void {
    const addToBell = () =>
      this.notifications.notify({
        kind: 'user-registered',
        title: 'Registration successful',
        message: `Customer '${username}' was registered for account ${accountId}.`,
        link: { path: '/admin/register' },
      });
    this.adminNotifications.notifyCustomerRegistered({ username, accountId }).subscribe({
      next: (sent) => {
        if (sent.channels.includes(AlertChannel.InApp)) {
          addToBell();
        }
      },
      error: () => addToBell(),
    });
  }

  togglePassword(): void {
    this.showPassword.set(!this.showPassword());
  }

  private showSuccessMessage(message: string): void {
    this.successMessage.set(message);
    this.errorMessage.set('');
    this.scheduleMessageClear();
  }

  private showErrorMessage(message: string): void {
    this.errorMessage.set(message);
    this.successMessage.set('');
    this.scheduleMessageClear();
  }

  private clearMessages(): void {
    this.errorMessage.set('');
    this.successMessage.set('');
    this.clearMessageTimer();
  }

  private scheduleMessageClear(): void {
    this.clearMessageTimer();
    this.messageTimeoutId = setTimeout(() => {
      this.successMessage.set('');
      this.errorMessage.set('');
      this.messageTimeoutId = null;
    }, 5000);
  }

  private clearMessageTimer(): void {
    if (this.messageTimeoutId) {
      clearTimeout(this.messageTimeoutId);
      this.messageTimeoutId = null;
    }
  }
}
