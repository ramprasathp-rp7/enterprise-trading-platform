import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * Footer Component
 * 
 * Bottom footer containing:
 * - Copyright information
 * - Links to support, legal, etc.
 * - Version information
 * - System status
 */
@Component({
  selector: 'app-footer',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './footer.component.html',
  styleUrls: ['./footer.component.css'],
})
export class FooterComponent {
  currentYear = new Date().getFullYear();
  version = '1.0.0';
  systemStatus = 'LIVE';

  footerLinks = [
    { label: 'Documentation', href: '#' },
    { label: 'Support', href: '#' },
    { label: 'Privacy', href: '#' },
    { label: 'Terms', href: '#' }
  ];
}
