import { Routes } from '@angular/router';
import { ShellComponent } from './layout/shell/shell.component';
import { AdminShell } from './layout/admin-shell/admin-shell';
import { LoginComponent } from './features/sign-in/pages/login/login.component';
import { authGuard, canActivateChildGuard } from './core/auth/auth.guard';
import { unauthenticatedGuard } from './core/auth/unauthenticated.guard';
import { adminGuard } from './core/auth/admin.guard';
import { customerGuard } from './core/auth/customer.guard';
import { AdminAnalyticsComponent } from './features/admin/pages/analytics/admin-analytics.component';
import { AdminFeeManagementComponent } from './features/admin/pages/fee-management/admin-fee-management.component';
import { AdminUserRegistrationComponent } from './features/admin/pages/user-registration/user-registration';
export const routes: Routes = [
  // Sign-in route (public, no auth required)
  {
    path: 'sign-in',
    component: LoginComponent,
    canActivate: [unauthenticatedGuard],
    data: { title: 'Sign In' },
  },

  // Protected routes - all under Shell layout with auth guard
  // Customers only - admins redirected to /admin/analytics
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard, customerGuard],
    children: [
      // Default route redirects to dashboard
      {
        path: '',
        redirectTo: 'dashboard',
        pathMatch: 'full',
      },
      {
        path: 'dashboard',
        canActivateChild: [canActivateChildGuard],
        loadComponent: () =>
          import('./features/dashboard/pages/dashboard/dashboard.component').then(
            (m) => m.DashboardComponent
          ),
        data: { title: 'Dashboard' },
      },
      {
        path: 'order-ticket',
        canActivateChild: [canActivateChildGuard],
        loadComponent: () =>
          import('./features/order-ticket/pages/order-ticket/order-ticket.component').then(
            (m) => m.OrderTicketComponent
          ),
        data: { title: 'Place Order' },
      },
      {
        path: 'order-confirmation/:orderId',
        canActivateChild: [canActivateChildGuard],
        loadComponent: () =>
          import('./features/order-ticket/pages/order-confirmation/order-confirmation.component').then(
            (m) => m.OrderConfirmationComponent
          ),
        data: { title: 'Order Confirmation' },
      },
      {
        path: 'blotter',
        canActivateChild: [canActivateChildGuard],
        loadComponent: () =>
          import('./features/blotter/pages/blotter/blotter.component').then(
            (m) => m.BlotterComponent
          ),
        data: { title: 'Order Blotter' },
      },
      {
        path: 'positions',
        canActivateChild: [canActivateChildGuard],
        loadComponent: () =>
          import('./features/positions/pages/positions/positions.component').then(
            (m) => m.PositionsComponent
          ),
        data: { title: 'Positions' },
      },
      {
        path: 'watchlists',
        canActivateChild: [canActivateChildGuard],
        loadComponent: () =>
          import('./features/watchlist/pages/watchlists/watchlists.component').then(
            (m) => m.WatchlistsComponent
          ),
        data: { title: 'Watchlists' },
      },
      {
        path: 'instruments',
        canActivateChild: [canActivateChildGuard],
        loadComponent: () =>
          import('./features/instruments/pages/instruments/instruments.component').then(
            (m) => m.InstrumentsComponent
          ),
        data: { title: 'Instruments' },
      },
      {
        path: 'notifications',
        canActivateChild: [canActivateChildGuard],
        loadComponent: () =>
          import('./features/notifications/pages/notifications/notifications.component').then(
            (m) => m.NotificationsComponent
          ),
        data: { title: 'Notifications' },
      },
      {
        path: 'advice',
        canActivateChild: [canActivateChildGuard],
        loadComponent: () =>
          import('./features/advice/pages/trade-advice/trade-advice.component').then(
            (m) => m.TradeAdviceComponent
          ),
        data: { title: 'Trade Advice' },
      },
      {
        path: 'strategies',
        canActivateChild: [canActivateChildGuard],
        loadComponent: () =>
          import('./features/strategies/pages/strategies/strategies.component').then(
            (m) => m.StrategiesComponent
          ),
        data: { title: 'Strategies' },
      },
      {
        path: 'settings',
        canActivateChild: [canActivateChildGuard],
        loadComponent: () =>
          import('./features/settings/pages/settings/settings.component').then(
            (m) => m.SettingsComponent
          ),
        data: { title: 'Preferences' },
      },
      {
        path: 'account',
        canActivateChild: [canActivateChildGuard],
        loadComponent: () =>
          import('./features/dashboard/pages/dashboard/dashboard.component').then(
            (m) => m.DashboardComponent
          ),
        data: { title: 'Account' },
      },
      {
        path: 'transactions',
        canActivateChild: [canActivateChildGuard],
        loadComponent: () =>
          import('./features/transactions/pages/transaction-actions/transaction-actions.component').then(
            (m) => m.TransactionActionsComponent
          ),
        data: { title: 'Transactions' },
      },
      {
        path: 'transaction-confirmation/:transactionId',
        canActivateChild: [canActivateChildGuard],
        loadComponent: () =>
          import('./features/transactions/pages/transaction-confirmation/transaction-confirmation.component').then(
            (m) => m.TransactionConfirmationComponent
          ),
        data: { title: 'Transaction Confirmation' },
      },
      {
        path: 'transaction-history',
        canActivateChild: [canActivateChildGuard],
        loadComponent: () =>
          import('./features/transactions/pages/transactions/transactions.component').then(
            (m) => m.TransactionsComponent
          ),
        data: { title: 'Transaction History' },
      },
    ],
  },

  // Admin routes - wrapped in AdminShell with admin-specific sidebar
  {
    path: 'admin',
    component: AdminShell,
    canActivate: [adminGuard],
    children: [
      {
        path: 'analytics',
        component: AdminAnalyticsComponent,
        data: { title: 'Admin Analytics' },
      },
      {
        path: 'register',
        component: AdminUserRegistrationComponent,
        data: { title: 'Register User' },
      },
      {
        path: 'fees',
        component: AdminFeeManagementComponent,
        data: { title: 'Fee Management' },
      },
      {
        path: 'notifications',
        loadComponent: () =>
          import('./features/notifications/pages/notifications/notifications.component').then(
            (m) => m.NotificationsComponent
          ),
        data: { title: 'Notifications' },
      },
      {
        path: 'preferences',
        loadComponent: () =>
          import('./features/settings/pages/settings/settings.component').then(
            (m) => m.SettingsComponent
          ),
        data: { title: 'Preferences' },
      },
      {
        path: '',
        redirectTo: 'analytics',
        pathMatch: 'full',
      },
    ],
  },

  // Wildcard route redirects to dashboard
  {
    path: '**',
    redirectTo: '/dashboard',
  },
];