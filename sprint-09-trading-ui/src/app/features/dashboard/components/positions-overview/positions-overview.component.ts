import { Component, OnInit, inject, signal, computed, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
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
  imports: [CommonModule],
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

  // Use defined theme variables from global styles.css so colors resolve correctly
  // Mild, but readable palette using theme tokens (mid-range tints)
  private palette = [
    // Pastel palette (soft, desaturated colors)
    '#ff8ab9', // pastel pink
    '#bd96f8', // pastel lavender
    '#fce39f', // pastel yellow
    '#90c9ff', // pastel blue
    '#93fab9', // pastel green
    '#7cfde3', // pastel teal
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
