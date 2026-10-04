import { Component, Output, EventEmitter, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { OrderFilters } from '../../blotter.models';
import { OrderStatus } from '../../../../api/generated/trade';

/**
 * Order filters component
 * Provides status and symbol filtering for orders
 */
@Component({
  selector: 'app-order-filters',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './order-filters.component.html',
  styles: [`
    .history-controls {
      display: flex;
      align-items: center;
      gap: 12px;
      flex-wrap: nowrap;
      overflow-x: auto;
    }

    .filter-group {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: nowrap;
    }

    .id-search-group {
      gap: 8px;
    }

    label {
      font-size: 13px;
      font-weight: 600;
      color: #64748b;
      white-space: nowrap;
    }

    .filter-select {
      padding: 8px 12px;
      border: 1px solid #e2e8f0;
      border-radius: 6px;
      font-size: 13px;
      color: #0f172a;
      background: white;
      cursor: pointer;
      transition: all 0.2s;
    }

    .filter-select:hover {
      border-color: #cbd5e1;
    }

    .filter-select:focus {
      outline: none;
      border-color: #2563eb;
      box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.1);
    }

    .id-search-input {
      min-width: 280px;
      width: 320px;
      padding: 8px 12px;
      border: 1px solid #e2e8f0;
      border-radius: 6px;
      font-size: 13px;
      color: #0f172a;
      background: #ffffff;
    }

    .id-search-input::placeholder {
      color: #94a3b8;
    }

    .id-search-input:focus {
      outline: none;
      border-color: #2563eb;
      box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.1);
    }

    .clear-search-btn {
      border: 1px solid #cbd5e1;
      background: #f8fafc;
      color: #334155;
      border-radius: 6px;
      padding: 7px 10px;
      font-size: 12px;
      font-weight: 600;
      cursor: pointer;
    }

    .clear-search-btn:hover {
      background: #f1f5f9;
    }
  `],
})
export class OrderFiltersComponent {
  @Output() filtersChanged = new EventEmitter<OrderFilters>();

  selectedStatus: string = '';
  selectedSymbol: string = '';

  statusOptions = OrderStatus;

  isFilterActive(): boolean {
    return !!this.selectedStatus || !!this.selectedSymbol;
  }

  activeFilterCount(): number {
    let count = 0;
    if (this.selectedStatus) count++;
    if (this.selectedSymbol) count++;
    return count;
  }

  onStatusChange(): void {
    this.emitFilters();
  }

  onSymbolChange(): void {
    this.emitFilters();
  }

  onClearFilters(): void {
    this.selectedStatus = '';
    this.selectedSymbol = '';
    this.emitFilters();
  }

  private emitFilters(): void {
    const filters: OrderFilters = {};
    if (this.selectedStatus) {
      filters.status = this.selectedStatus as unknown as OrderStatus;
    }
    if (this.selectedSymbol) {
      filters.symbol = this.selectedSymbol;
    }
    this.filtersChanged.emit(filters);
  }
}

