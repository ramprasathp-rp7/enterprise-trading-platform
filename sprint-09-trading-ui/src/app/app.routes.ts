import { Routes } from '@angular/router';
import { ShellComponent } from './layout/shell/shell.component';
import { LoginComponent } from './features/sign-in/pages/login/login.component';
import { authGuard, canActivateChildGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  // Sign-in route (public, no auth required)
  {
    path: 'sign-in',
    component: LoginComponent,
    data: { title: 'Sign In' },
  },
  
  // Protected routes - all under Shell layout with auth guard
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
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
          import('./features/blotter/pages/blotter/blotter.component').then(
            (m) => m.BlotterComponent
          ),
        data: { title: 'Positions' },
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
    ],
  },
  
  // Wildcard route redirects to dashboard
  {
    path: '**',
    redirectTo: '/dashboard',
  },
];