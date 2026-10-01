import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, HTTP_INTERCEPTORS } from '@angular/common/http';
import { routes } from './app.routes';
import { AuthInterceptor } from './core/auth/auth.interceptor';
import { CoreAuthService } from './core/auth/core-auth.service';
import { provideAuthApiConfig } from './core/config/auth-api.config';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(),
    // Configure auth API services with dynamic token injection
    ...provideAuthApiConfig(),
    // Register HTTP interceptor for trade API and fallback token injection
    {
      provide: HTTP_INTERCEPTORS,
      useClass: AuthInterceptor,
      multi: true,
    },
    // Ensure CoreAuthService is eagerly initialized so signals are set up before any HTTP requests
    {
      provide: 'CORE_AUTH_INIT',
      useFactory: (authService: CoreAuthService) => authService,
      deps: [CoreAuthService],
    },
  ],
};

