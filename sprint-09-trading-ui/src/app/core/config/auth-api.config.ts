import { Provider } from '@angular/core';
import { Configuration } from '../../api/generated/auth/configuration';
import { CoreAuthService } from '../auth/core-auth.service';
import { environment } from './environment';

/**
 * Provides configuration for the Auth API services.
 * Dynamically injects the bearer token from CoreAuthService.
 * 
 * This ensures ProfileService and other auth services get the access token
 * for authenticated requests like GET /auth/me.
 */
export function provideAuthApiConfig(): Provider[] {
  return [
    {
      provide: Configuration,
      useFactory: (authService: CoreAuthService) => {
        return new Configuration({
          basePath: environment.AUTH_SERVICE_URL,
          credentials: {
            bearerAuth: () => authService.accessToken() || undefined,
          },
        });
      },
      deps: [CoreAuthService],
    },
  ];
}

