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
    .filters-container {
      background-color: #f9f9f9;
      border: 1px solid #ddd;
      border-radius: 4px;
      padding: 16px;
      margin-bottom: 16px;
    }

    .filters-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 12px;
    }

    h3 {
      margin: 0;
      font-size: 0.95rem;
      color: #333;
      font-weight: 600;
    }

    .clear-button {
      background-color: #e9ecef;
      border: 1px solid #dee2e6;
      border-radius: 4px;
      padding: 6px 12px;
      font-size: 0.75rem;
      cursor: pointer;
      transition: background-color 0.2s ease;
      color: #495057;
      font-weight: 500;
    }

    .clear-button:hover {
      background-color: #dee2e6;
    }

    .filter-group {
      display: flex;
      flex-direction: column;
      gap: 6px;
      margin-bottom: 12px;
    }

    label {
      font-size: 0.85rem;
      font-weight: 500;
      color: #495057;
    }

    .filter-input {
      padding: 8px 12px;
      border: 1px solid #ddd;
      border-radius: 4px;
      font-size: 0.875rem;
      font-family: inherit;
      transition: border-color 0.2s ease, box-shadow 0.2s ease;
    }

    .filter-input:focus {
      outline: none;
      border-color: #0066cc;
      box-shadow: 0 0 4px rgba(0, 102, 204, 0.2);
    }

    .filter-info {
      padding: 8px;
      background-color: #e8f4f8;
      border-left: 3px solid #0066cc;
      border-radius: 2px;
      font-size: 0.8rem;
      color: #004f99;
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

