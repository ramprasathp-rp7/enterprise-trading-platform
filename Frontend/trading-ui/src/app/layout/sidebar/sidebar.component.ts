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
  /** Sidebar group the item belongs to */
  section: 'Trading' | 'Market Tools' | 'Funds';
}

interface NavSection {
  title: NavItem['section'];
  items: NavItem[];
}

/**
 * Sidebar Component
 * 
 * Left sidebar containing:
 * - Feature navigation in three groups: Trading, Market Tools, Funds
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
  /** On phones the sidebar is an overlay drawer, so it starts closed */
  isCollapsed = typeof window !== 'undefined' && window.innerWidth < 768;

  navigationItems: NavItem[] = [
    {
      label: 'Dashboard',
      route: '/dashboard',
      icon: 'fas fa-chart-line',
      description: 'Account overview',
      section: 'Trading'
    },
    {
      label: 'Positions',
      route: '/positions',
      icon: 'fas fa-layer-group',
      description: 'Holdings & P&L',
      section: 'Trading'
    },
    {
      label: 'Place Order',
      route: '/order-ticket',
      icon: 'fas fa-edit',
      description: 'Buy & sell',
      section: 'Trading'
    },
    {
      label: 'Order Blotter',
      route: '/blotter',
      icon: 'fas fa-list-ul',
      description: 'Track your orders',
      section: 'Trading'
    },
    {
      label: 'Instruments',
      route: '/instruments',
      icon: 'fas fa-table-list',
      description: 'Markets & live prices',
      section: 'Market Tools'
    },
    {
      label: 'Watchlists',
      route: '/watchlists',
      icon: 'fas fa-bookmark',
      description: 'Symbols you follow',
      section: 'Market Tools'
    },
    {
      label: 'Trade Advice',
      route: '/advice',
      icon: 'fas fa-lightbulb',
      description: 'Buy & sell signals',
      section: 'Market Tools'
    },
    {
      label: 'Strategies',
      route: '/strategies',
      icon: 'fas fa-robot',
      description: 'Automated trading',
      section: 'Market Tools'
    },
    {
      label: 'Transactions',
      route: '/transactions',
      icon: 'fas fa-money-bill-transfer',
      description: 'Deposit & withdraw',
      section: 'Funds'
    },
    {
      label: 'Transaction History',
      route: '/transaction-history',
      icon: 'fas fa-clock-rotate-left',
      description: 'Statements',
      section: 'Funds'
    }
  ];

  /** Items grouped for display: Trading, then Market Tools, then Funds (order within a group is preserved) */
  readonly navSections: NavSection[] = (['Trading', 'Market Tools', 'Funds'] as const).map((title) => ({
    title,
    items: this.navigationItems.filter((item) => item.section === title),
  }));

  toggleSidebar(): void {
    this.isCollapsed = !this.isCollapsed;
  }

  closeSidebar(): void {
    if (window.innerWidth < 768) {
      this.isCollapsed = true;
    }
  }
}
