import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, ActivatedRoute } from '@angular/router';
import { vi } from 'vitest';
import { LoginComponent } from './login.component';
import { SignInStore } from '../../sign-in.store';
import { LoginCredentials } from '../../../../core/auth/auth.models';

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;
  let store: any;
  let router: any;

  beforeEach(async () => {
    store = {
      login: vi.fn(),
      logout: vi.fn(),
      isLoading: vi.fn(() => false),
      error: vi.fn(() => null),
      errorCode: vi.fn(() => null),
      errorMessage: vi.fn(() => null),
    };

    router = {
      navigateByUrl: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        { provide: SignInStore, useValue: store },
        { provide: Router, useValue: router },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParams: {},
            },
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('Valid credentials sign in and redirect', () => {
    it('should call store.login with valid credentials', async () => {
      store.login.mockResolvedValue(true);

      const credentials: LoginCredentials = {
        username: 'priya.menon',
        password: 'correct horse battery staple',
      };

      await component.onSubmit(credentials);

      expect(store.login).toHaveBeenCalledWith('priya.menon', 'correct horse battery staple');
    });

    it('should redirect to dashboard on successful login', async () => {
      store.login.mockResolvedValue(true);

      const credentials: LoginCredentials = {
        username: 'priya.menon',
        password: 'password123',
      };

      await component.onSubmit(credentials);

      expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
    });

    it('should redirect to returnUrl if provided in query params', async () => {
      const activatedRoute = TestBed.inject(ActivatedRoute);
      (activatedRoute.snapshot.queryParams as any) = { returnUrl: '/order-ticket' };
      store.login.mockResolvedValue(true);

      const credentials: LoginCredentials = {
        username: 'priya.menon',
        password: 'password123',
      };

      await component.onSubmit(credentials);

      expect(router.navigateByUrl).toHaveBeenCalledWith('/order-ticket');
    });
  });

  describe('Invalid credentials show a readable error', () => {
    it('should not redirect on failed login', async () => {
      store.login.mockResolvedValue(false);

      const credentials: LoginCredentials = {
        username: 'priya.menon',
        password: 'wrong-password',
      };

      await component.onSubmit(credentials);

      expect(router.navigateByUrl).not.toHaveBeenCalled();
    });
  });

  describe('Signing out clears the session', () => {
    it('should call store.logout when sign-out is triggered', () => {
      component.onSignOut();
      expect(store.logout).toHaveBeenCalled();
    });
  });

  describe('UI rendering', () => {
    it('should render sign-in title', () => {
      const title = fixture.nativeElement.querySelector('.login-title');
      expect(title).toBeTruthy();
      expect(title.textContent).toContain('Sign In');
    });

    it('should render credential form component', () => {
      const form = fixture.nativeElement.querySelector('app-credential-form');
      expect(form).toBeTruthy();
    });

    it('should render error message component', () => {
      const error = fixture.nativeElement.querySelector('app-sign-in-error-message');
      expect(error).toBeTruthy();
    });
  });
});
