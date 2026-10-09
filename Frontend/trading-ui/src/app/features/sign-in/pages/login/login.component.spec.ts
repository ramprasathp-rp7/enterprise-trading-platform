import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, ActivatedRoute } from '@angular/router';
import { CoreAuthService } from '../../../../core/auth/core-auth.service';
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

    it('should not send an admin back to the customer dashboard', async () => {
      const auth = TestBed.inject(CoreAuthService);
      vi.spyOn(auth, 'isAdmin').mockReturnValue(true);
      vi.spyOn(auth, 'getDashboardRoute').mockReturnValue('/admin/analytics');
      (TestBed.inject(ActivatedRoute).snapshot.queryParams as any) = { returnUrl: '/dashboard?tab=1' };
      store.login.mockResolvedValue(true);

      await component.onSubmit({ username: 'demo.admin', password: 'password123' });

      expect(router.navigateByUrl).toHaveBeenCalledWith('/admin/analytics');
    });

    it('should refuse a protocol-relative returnUrl', async () => {
      (TestBed.inject(ActivatedRoute).snapshot.queryParams as any) = { returnUrl: '//evil.example/x' };
      store.login.mockResolvedValue(true);

      await component.onSubmit({ username: 'priya.menon', password: 'password123' });

      expect(router.navigateByUrl).not.toHaveBeenCalledWith('//evil.example/x');
    });

    it('should refuse an off-origin returnUrl and redirect to dashboard', async () => {
      const activatedRoute = TestBed.inject(ActivatedRoute);
      (activatedRoute.snapshot.queryParams as any) = {
        returnUrl: 'https://phishing.example/login-capture',
      };
      store.login.mockResolvedValue(true);

      const credentials: LoginCredentials = {
        username: 'priya.menon',
        password: 'password123',
      };

      await component.onSubmit(credentials);

      expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
    });

    it('should refuse a non-path returnUrl and redirect to dashboard', async () => {
      const activatedRoute = TestBed.inject(ActivatedRoute);
      (activatedRoute.snapshot.queryParams as any) = {
        returnUrl: 'order-ticket',
      };
      store.login.mockResolvedValue(true);

      const credentials: LoginCredentials = {
        username: 'priya.menon',
        password: 'password123',
      };

      await component.onSubmit(credentials);

      expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
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
      // title text may include product name; ensure Sign In appears somewhere after trimming
      expect(title.textContent.trim()).toMatch(/Sign\s*In/i);
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
