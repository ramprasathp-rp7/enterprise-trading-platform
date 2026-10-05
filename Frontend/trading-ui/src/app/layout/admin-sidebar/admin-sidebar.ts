import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, RouterLinkActive } from '@angular/router';

interface NavItem {
  label: string;
  route: string;
  icon: string;
  description: string;
}

@Component({
  selector: 'app-admin-sidebar',
  standalone: true,
  imports: [CommonModule, RouterModule, RouterLinkActive],
  templateUrl: './admin-sidebar.html',
  styleUrls: ['../sidebar/sidebar.component.css'],
})
export class AdminSidebarComponent {
  isCollapsed = false;

  navigationItems: NavItem[] = [
    {
      label: 'Dashboard',
      route: '/admin/analytics',
      icon: 'fas fa-chart-line',
      description: 'Analytics overview'
    },
    {
      label: 'Account Management',
      route: '/admin/accounts',
      icon: 'fas fa-users',
      description: 'Manage user accounts'
    },
    {
      label: 'Register User',
      route: '/admin/register',
      icon: 'fas fa-user-plus',
      description: 'Register new users'
    },
    {
      label: 'Fee Management',
      route: '/admin/fees',
      icon: 'fas fa-coins',
      description: 'Manage asset class fees'
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