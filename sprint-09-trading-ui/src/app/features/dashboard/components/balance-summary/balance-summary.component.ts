import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * Balance Summary Component
 * Displays account balance and investment summary
 */
@Component({
  selector: 'app-balance-summary',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './balance-summary.component.html',
  styleUrls: ['./balance-summary.component.css']
})
export class BalanceSummaryComponent {
  // TODO: Define input properties
  // @Input() totalInvested = 0;
  // @Input() unrealizedPL = 0;
  // @Input() unrealizedPLPercent = 0;
  // @Input() availableCash = 0;

  constructor() {
    // TODO: Initialize component
  }

  // TODO: Implement component methods
}
