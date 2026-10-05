import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { finalize } from 'rxjs/operators';
import {
  AccountsService,
  PositionResponse,
} from '../../../../api/generated/trade';
import { CoreAuthService } from '../../../../core/auth/core-auth.service';

/**
 * Positions Component
 *
 * Displays current positions from the accounts positions API.
 */
@Component({
  selector: 'app-positions',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './positions.component.html',
  styleUrls: ['./positions.component.css'],
})
export class PositionsComponent implements OnInit {
  private readonly authService = inject(CoreAuthService);
  private readonly accountsService = inject(AccountsService);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly positions = signal<PositionResponse[]>([]);
  readonly totalUnits = computed(() =>
    this.positions().reduce((sum, p) => sum + (p.quantity || 0), 0)
  );
  readonly totalCostBasis = computed(() =>
    this.positions().reduce((sum, p) => sum + (p.quantity || 0) * (p.averageCost || 0), 0)
  );

  ngOnInit(): void {
    const accountId = this.authService.currentUser()?.accountId;
    if (!accountId) {
      this.error.set('No account found for current session. Please sign in again.');
      return;
    }

    this.loadPositions(accountId);
  }

  private loadPositions(accountId: number): void {
    this.loading.set(true);
    this.error.set(null);

    this.accountsService
      .getPositions(accountId)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (positions) => {
          this.positions.set(positions ?? []);
        },
        error: () => {
          this.error.set('Could not fetch current positions.');
        },
      });
  }

  formatNumber(value: number, fractionDigits: number): string {
    return new Intl.NumberFormat('en-US', {
      minimumFractionDigits: fractionDigits,
      maximumFractionDigits: fractionDigits,
    }).format(value);
  }

}
