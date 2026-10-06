import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { AccountsService } from '../../../../api/generated/trade';
import {
  PortfolioService,
  PortfolioSummary,
  PricedPosition,
} from '../../../../api/generated/porfolio';
import { CoreAuthService } from '../../../../core/auth/core-auth.service';
import { StatementPdfService, fileDate } from '../../../../shared/statement-pdf.service';
import { NotificationService } from '../../../../core/notifications/notification.service';

/**
 * Positions Component
 *
 * Displays the portfolio summary and priced holdings from the portfolio API.
 * Prices come from Fauxnance (or the market-data cache when it is unavailable),
 * so stale and unpriced holdings are flagged rather than shown as current.
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
  private readonly portfolioService = inject(PortfolioService);
  private readonly statementPdf = inject(StatementPdfService);
  private readonly notifications = inject(NotificationService);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly positions = signal<PricedPosition[]>([]);
  readonly summaryLoading = signal(false);
  readonly summaryError = signal<string | null>(null);
  readonly summary = signal<PortfolioSummary | null>(null);
  readonly downloading = signal(false);
  readonly downloadError = signal<string | null>(null);
  readonly totalUnits = computed(() =>
    this.positions().reduce((sum, p) => sum + (p.quantity || 0), 0)
  );
  readonly totalCostBasis = computed(() =>
    this.positions().reduce((sum, p) => sum + (p.costBasis || 0), 0)
  );
  /** Holdings priced from a stale quote or not priced at all */
  readonly stalePositionCount = computed(() => this.positions().filter((p) => p.stale).length);
  /** Stale/partial pricing banner, hidden for now. */
  readonly showPricingWarning = false;
  /** Per-row Stale badge next to the last price, hidden for now; the No price badge still shows. */
  readonly showStaleBadge = false;

  ngOnInit(): void {
    const accountId = this.authService.currentUser()?.accountId;
    if (!accountId) {
      this.error.set('No account found for current session. Please sign in again.');
      return;
    }

    this.loadPortfolio(accountId);
  }

  /** Reload the summary and holdings so prices are re-quoted. */
  refresh(): void {
    const accountId = this.authService.currentUser()?.accountId;
    if (accountId) {
      this.loadPortfolio(accountId);
    }
  }

  private loadPortfolio(accountId: number): void {
    this.loadSummary(accountId);
    this.loadPositions(accountId);
  }

  private loadSummary(accountId: number): void {
    this.summaryLoading.set(true);
    this.summaryError.set(null);

    this.portfolioService
      .getPortfolioSummary(accountId)
      .pipe(finalize(() => this.summaryLoading.set(false)))
      .subscribe({
        next: (summary) => {
          this.summary.set(summary ?? null);
        },
        error: () => {
          this.summaryError.set('Could not fetch the portfolio summary.');
        },
      });
  }

  private loadPositions(accountId: number): void {
    this.loading.set(true);
    this.error.set(null);

    this.portfolioService
      .getPricedPositions(accountId)
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
      const fileName = `holdings-statement-${accountNumber}-${fileDate()}`;
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
          this.formatNumber(p.costBasis, 2),
        ]),
        footerRow: ['Total', this.formatNumber(this.totalUnits(), 0), '', this.formatNumber(this.totalCostBasis(), 2)],
        emptyMessage: 'There are no open positions on this account.',
        fileName,
      });
      this.notifications.notify({
        kind: 'report',
        title: 'Report exported',
        message: `Holdings statement downloaded as ${fileName}.pdf.`,
        link: { path: '/positions' },
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

  /** Money with an explicit sign, for P&L figures; '—' when the value is unknown. */
  formatSigned(value: number | null | undefined, fractionDigits = 2): string {
    if (value === null || value === undefined) {
      return '—';
    }
    const formatted = this.formatNumber(Math.abs(value), fractionDigits);
    return value > 0 ? `+${formatted}` : value < 0 ? `-${formatted}` : formatted;
  }

  /** CSS tone for a P&L value. */
  pnlTone(value: number | null | undefined): string {
    if (!value) {
      return '';
    }
    return value > 0 ? 'pnl-up' : 'pnl-down';
  }

}
