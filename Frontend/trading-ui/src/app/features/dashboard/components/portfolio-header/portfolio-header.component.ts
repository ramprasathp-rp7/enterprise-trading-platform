import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * Portfolio Header Component
 * Displays portfolio summary and key metrics
 */
@Component({
  selector: 'app-portfolio-header',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './portfolio-header.component.html',
  styleUrls: ['./portfolio-header.component.css']
})
export class PortfolioHeaderComponent {
  // TODO: Define input properties
  // @Input() totalValue = 0;
  // @Input() dayGain = 0;
  // @Input() dayGainPercent = 0;

  constructor() {
    // TODO: Initialize component
  }

  // TODO: Implement component methods
}
