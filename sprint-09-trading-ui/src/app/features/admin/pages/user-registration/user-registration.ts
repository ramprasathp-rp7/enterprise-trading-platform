import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../core/config/environment';

interface RegisterDto {
  username: string;
  password: string;
  accountId: number;
  roles: string[];
}

@Component({
  selector: 'app-admin-user-registration',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './user-registration.html',
  styleUrls: ['./user-registration.css'],
})
export class AdminUserRegistrationComponent implements OnInit {
  private fb = inject(FormBuilder);
  private http = inject(HttpClient);

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
      roles: [['CUSTOMER'], Validators.required],
    });
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.errorMessage.set('Please fill all required fields correctly');
      return;
    }

    this.loading.set(true);
    this.errorMessage.set('');
    this.successMessage.set('');

    const dto: RegisterDto = this.form.value;

    this.http.post(`${environment.AUTH_SERVICE_URL}/auth/register`, dto).subscribe({
      next: () => {
        this.successMessage.set(`User '${dto.username}' registered successfully!`);
        this.form.reset({ roles: ['CUSTOMER'] });
        this.loading.set(false);
      },
      error: (err) => {
        this.errorMessage.set(err.error?.message || 'Registration failed');
        this.loading.set(false);
      },
    });
  }

  togglePassword(): void {
    this.showPassword.set(!this.showPassword());
  }
}
