import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withViewTransitions } from '@angular/router';
import { provideHttpClient, HTTP_INTERCEPTORS, withInterceptorsFromDi } from '@angular/common/http';
import { provideCharts, withDefaultRegisterables } from 'ng2-charts';
import { routes } from './app.routes';
import { AuthInterceptor } from './core/auth/auth.interceptor';
import { CoreAuthService } from './core/auth/core-auth.service';
import { provideAuthApiConfig } from './core/config/auth-api.config';
import { provideApi as provideAuthApi } from './api/generated/auth/provide-api';
import { provideApi as provideTradeApi } from './api/generated/trade/provide-api';
import { provideApi as provideAdminApi } from './api/generated/admin/provide-api';
import { provideApi as provideWatchlistApi } from './api/generated/watchlist/provide-api';
import { provideApi as providePreferencesApi } from './api/generated/preferences/provide-api';
import { provideApi as provideMarketDataApi } from './api/generated/market-data/provide-api';
import { provideApi as provideOrderNotificationsApi } from './api/generated/order-notifications/provide-api';
import { provideApi as provideAdviceApi } from './api/generated/advice/provide-api';
import { provideApi as providePortfolioApi } from './api/generated/portfolio/provide-api';
import { provideApi as provideAccountHistoryApi } from './api/generated/account-history/provide-api';
import { provideApi as provideCandleApi } from './api/generated/candle/provide-api';
import { provideApi as provideStrategyApi } from './api/generated/strategy/provide-api';
import { provideApi as provideTransactionsApi } from './api/generated/transactions/provide-api';
import { provideApi as provideInstrumentsApi } from './api/generated/instruments/provide-api';
import { provideApi as provideAccountSettingsApi } from './api/generated/account-settings/provide-api';
import { environment } from './core/config/environment';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // Pages cross-fade when the route changes (browsers without view transitions just switch)
    provideRouter(routes, withViewTransitions({ skipInitialTransition: true })),
    provideHttpClient(withInterceptorsFromDi()),
    provideCharts(withDefaultRegisterables()), 
    provideAuthApi(environment.AUTH_SERVICE_URL),
    provideTradeApi(environment.TRADE_API_URL),
    provideAdminApi(environment.TRADE_API_URL),
    provideWatchlistApi(environment.TRADE_API_URL),
    providePreferencesApi(environment.TRADE_API_URL),
    provideMarketDataApi(environment.TRADE_API_URL),
    provideOrderNotificationsApi(environment.TRADE_API_URL),
    provideAdviceApi(environment.TRADE_API_URL),
    providePortfolioApi(environment.TRADE_API_URL),
    provideAccountHistoryApi(environment.TRADE_API_URL),
    provideCandleApi(environment.TRADE_API_URL),
    provideStrategyApi(environment.TRADE_API_URL),
    provideTransactionsApi(environment.TRADE_API_URL),
    provideInstrumentsApi(environment.TRADE_API_URL),
    provideAccountSettingsApi(environment.TRADE_API_URL),
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

