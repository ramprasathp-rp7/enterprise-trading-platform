import { Provider } from '@angular/core';
import { Configuration } from '../../api/generated/trade/configuration';
import { CoreAuthService } from '../auth/core-auth.service';
import { environment } from './environment';

/**
 * Provides configuration for the Trade API services.
 * Dynamically injects the bearer token from CoreAuthService.
 */
export function provideTradeApiConfig(): Provider[] {
  return [
    {
      provide: Configuration,
      useFactory: (authService: CoreAuthService) => {
        return new Configuration({
          basePath: environment.TRADE_API_URL,
          credentials: {
            bearerAuth: () => authService.accessToken() || undefined,
          },
        });
      },
      deps: [CoreAuthService],
    },
  ];
}
