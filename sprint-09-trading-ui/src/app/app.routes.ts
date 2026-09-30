import { Routes } from '@angular/router';
import { LoginComponent } from './features/sign-in/pages/login/login.component';
import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  {
    path: '',
    redirectTo: '/dashboard',
    pathMatch: 'full',
  },
  {
    path: 'sign-in',
    component: LoginComponent,
    data: { title: 'Sign In' },
  },
  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/dashboard/pages/dashboard/dashboard.component').then(
        (m) => m.DashboardComponent
      ),
    data: { title: 'Dashboard' },
  },
  {
    path: 'order-ticket',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/order-ticket/pages/order-ticket/order-ticket.component').then(
        (m) => m.OrderTicketComponent
      ),
    data: { title: 'Place Order' },
  },
  {
    path: 'blotter',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/blotter/pages/blotter/blotter.component').then(
        (m) => m.BlotterComponent
      ),
    data: { title: 'Order Blotter' },
  },
  {
    path: '**',
    redirectTo: '/dashboard',
  },
];

