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
    .status-select {
      width: 168px;
    }

    .id-search-input {
      width: 220px;
    }

    @media (max-width: 768px) {
      .status-select,
      .id-search-input {
        width: 100%;
      }
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

