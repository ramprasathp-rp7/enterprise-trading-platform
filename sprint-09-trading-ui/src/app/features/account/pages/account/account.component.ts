import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * Account Component
 * 
 * Displays user account information and settings
 */
@Component({
  selector: 'app-account',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="account-container">
      <h1>Account</h1>
      <p>Account information and settings will be displayed here.</p>
    </div>
  `,
  styles: [`
    .account-container {
      padding: 20px;
    }
  `]
})
export class AccountComponent {}
