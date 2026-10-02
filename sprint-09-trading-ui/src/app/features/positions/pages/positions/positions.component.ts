import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * Positions Component
 * 
 * Displays active trading positions and holdings
 */
@Component({
  selector: 'app-positions',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="positions-container">
      <h1>Positions</h1>
      <p>Active positions and holdings will be displayed here.</p>
    </div>
  `,
  styles: [`
    .positions-container {
      padding: 20px;
    }
  `]
})
export class PositionsComponent {}
