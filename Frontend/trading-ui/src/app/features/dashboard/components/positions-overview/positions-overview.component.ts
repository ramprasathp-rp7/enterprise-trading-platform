import { Component, OnInit, inject, signal, computed, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AccountsService, PositionResponse } from '../../../../api/generated/trade';

interface AllocationSegment {
  symbol: string;
  quantity: number;
  percentage: number;
  color: string;
}

/**
 * Positions Overview Component
 * Displays list of current positions and holdings
 */
@Component({
  selector: 'app-positions-overview',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './positions-overview.component.html',
  styleUrls: ['./positions-overview.component.css']
})
export class PositionsOverviewComponent implements OnInit {
  @Input() accountId?: number | null = null;
  private accountsService = inject(AccountsService);
  // Auth service will be injected by parent or available globally; we'll read account id from window if not provided

  readonly positions = signal<PositionResponse[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  // Categorical palette for the allocation bar: distinct hues at similar
  // weight so no single holding looks more important than its share.
  private palette = [
    '#2563eb', // blue
    '#0d9488', // teal
    '#7c3aed', // violet
    '#d97706', // amber
    '#db2777', // pink
    '#0891b2', // cyan
  ];

  readonly allocation = computed(() => {
    const pos = this.positions();
    if (!pos || pos.length === 0) return [] as AllocationSegment[];
    const totalQty = pos.reduce((s, p) => s + (p.quantity || 0), 0);
    if (totalQty === 0) return [] as AllocationSegment[];
    return pos.map((p, i) => ({
      symbol: p.symbol,
      quantity: p.quantity,
      percentage: (p.quantity / totalQty) * 100,
      color: this.palette[i % this.palette.length]
    })).sort((a, b) => b.percentage - a.percentage);
  });
  
    // Helper for template to get color for a position (fallback)
    paletteIndex(p: PositionResponse): string {
      const idx = Math.abs(String(p.symbol).split('').reduce((s,c)=>s+c.charCodeAt(0),0)) % this.palette.length;
      return this.palette[idx];
    }

  // Expose simple counts for template
  readonly positionsCount = computed(() => this.positions().length);
  readonly totalQuantity = computed(() => this.positions().reduce((s,p) => s + (p.quantity || 0), 0));
  // Hover label state
  hoverLabel: string | null = null;

  // Top holding (most quantity) and rest list
  readonly topHolding = computed(() => {
    const pos = this.positions();
    if (!pos || pos.length === 0) return null as PositionResponse | null;
    return [...pos].sort((a,b) => (b.quantity || 0) - (a.quantity || 0))[0];
  });

  readonly otherHoldings = computed(() => {
    const pos = this.positions();
    if (!pos || pos.length <= 1) return [] as PositionResponse[];
    return [...pos].sort((a,b) => (b.quantity || 0) - (a.quantity || 0)).slice(1);
  });

  // Combined array for table (top holding first)
  readonly tableHoldings = computed(() => {
    const top = this.topHolding();
    const others = this.otherHoldings();
    if (!top) return others;
    return [top, ...others];
  });

  readonly topFour = computed(() => {
    const pos = this.positions();
    if (!pos || pos.length === 0) return [] as PositionResponse[];
    return [...pos].sort((a,b) => (b.quantity || 0) - (a.quantity || 0)).slice(0,4);
  });

  // Convenience: tableRows limited to 4 for compact display
  readonly tableRows = computed(() => this.tableHoldings().slice(0,4));

  formatAmount(value: number, digits: number = 2): string {
    return new Intl.NumberFormat('en-US', { minimumFractionDigits: digits, maximumFractionDigits: digits }).format(value);
  }

  ngOnInit(): void {
    const idFromWindow = (window as any)['__CURRENT_ACCOUNT_ID__'] || null;
    const useId = this.accountId ?? idFromWindow;
    if (useId) {
      this.loadPositions(useId as number);
    }
  }

  loadPositions(accountId: number): void {
    this.loading.set(true);
    this.error.set(null);
    this.accountsService.getPositions(accountId).subscribe({
      next: (positions) => {
        this.positions.set(positions ?? []);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Failed to load positions', err);
        this.error.set('Could not load positions');
        this.loading.set(false);
      }
    });
  }
}
