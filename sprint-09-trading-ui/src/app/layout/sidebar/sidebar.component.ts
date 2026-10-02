import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, RouterLinkActive } from '@angular/router';

interface NavItem {
  label: string;
  route: string;
  icon: string;
  description: string;
  badge?: string;
  badgeClass?: string;
}

/**
 * Sidebar Component
 * 
 * Left sidebar containing:
 * - Feature navigation (Orders, Blotter, Account)
 * - Collapsible menu
 * - Feature quick access
 */
@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule, RouterModule, RouterLinkActive],
  templateUrl: './sidebar.component.html',
  styleUrls: ['./sidebar.component.css'],
})
export class SidebarComponent {
  isCollapsed = false;

  navigationItems: NavItem[] = [
    {
      label: 'Dashboard',
      route: '/dashboard',
      icon: 'fas fa-chart-line',
      description: 'Overview & analytics'
    },
    {
      label: 'Place Order',
      route: '/order-ticket',
      icon: 'fas fa-edit',
      description: 'Create new trades'
    },
    {
      label: 'Order Blotter',
      route: '/blotter',
      icon: 'fas fa-list-ul',
      description: 'View all orders'
    },
    {
      label: 'Transactions',
      route: '/transactions',
      icon: 'fas fa-money-bill-transfer',
      description: 'Deposit & withdraw'
    },
    {
      label: 'Transaction History',
      route: '/transaction-history',
      icon: 'fas fa-clock-rotate-left',
      description: 'View all transactions'
    },
    {
      label: 'Positions',
      route: '/positions',
      icon: 'fas fa-layer-group',
      description: 'Active positions'
    },
    {
      label: 'Account',
      route: '/account',
      icon: 'fas fa-user-circle',
      description: 'Profile & settings'
    }
  ];

  toggleSidebar(): void {
    this.isCollapsed = !this.isCollapsed;
  }

  closeSidebar(): void {
    if (window.innerWidth < 768) {
      this.isCollapsed = true;
    }
  }
}
