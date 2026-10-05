import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { finalize } from 'rxjs/operators';
import {
  AccountsService,
  PositionResponse,
} from '../../../../api/generated/trade';
import { CoreAuthService } from '../../../../core/auth/core-auth.service';
import { StatementPdfService, fileDate } from '../../../../shared/statement-pdf.service';

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
  private readonly statementPdf = inject(StatementPdfService);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly positions = signal<PositionResponse[]>([]);
  readonly downloading = signal(false);
  readonly downloadError = signal<string | null>(null);
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

  /** Download the current holdings as a PDF statement. */
  async downloadStatement(): Promise<void> {
    const user = this.authService.currentUser();
    const positions = this.positions();

    this.downloading.set(true);
    this.downloadError.set(null);
    try {
      const accountNumber = await this.customerAccountNumber();
      await this.statementPdf.download({
        title: 'Holdings Statement',
        details: [
          ['Account', accountNumber],
          ['Prepared for', user?.username ?? 'N/A'],
        ],
        summary: [
          ['Open positions', String(positions.length)],
          ['Total units', this.formatNumber(this.totalUnits(), 0)],
          ['Total cost basis', this.formatNumber(this.totalCostBasis(), 2)],
        ],
        columns: [
          { header: 'Symbol' },
          { header: 'Quantity', numeric: true },
          { header: 'Average cost', numeric: true },
          { header: 'Cost basis', numeric: true },
        ],
        rows: positions.map((p) => [
          p.symbol,
          this.formatNumber(p.quantity, 0),
          this.formatNumber(p.averageCost, 2),
          this.formatNumber(p.quantity * p.averageCost, 2),
        ]),
        footerRow: ['Total', this.formatNumber(this.totalUnits(), 0), '', this.formatNumber(this.totalCostBasis(), 2)],
        emptyMessage: 'There are no open positions on this account.',
        fileName: `holdings-statement-${accountNumber}-${fileDate()}`,
      });
    } catch (err) {
      console.error('Failed to generate holdings statement', err);
      this.downloadError.set('Could not generate the statement. Please try again.');
    } finally {
      this.downloading.set(false);
    }
  }

  /** Customer-facing account number (e.g. ACC-000001) for statements, not the internal numeric key. */
  private async customerAccountNumber(): Promise<string> {
    const accountId = this.authService.currentUser()?.accountId;
    if (!accountId) {
      throw new Error('No account for the current session');
    }
    const account = await firstValueFrom(this.accountsService.getAccount(accountId));
    if (!account?.accountId) {
      throw new Error('Account has no customer-facing number');
    }
    return account.accountId;
  }

  formatNumber(value: number, fractionDigits: number): string {
    return new Intl.NumberFormat('en-US', {
      minimumFractionDigits: fractionDigits,
      maximumFractionDigits: fractionDigits,
    }).format(value);
  }

}
