import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { AdminService } from '../../services/admin.service';
import { AssetFee } from '../../admin.models';

/**
 * Admin Fee Management Component
 * Displays asset class fee structures
 */
@Component({
  selector: 'app-admin-fee-management',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './admin-fee-management.component.html',
  styleUrls: ['./admin-fee-management.component.css'],
})
export class AdminFeeManagementComponent implements OnInit {
  private adminService = inject(AdminService);

  // State
  assetFees = signal<AssetFee[]>([]);
  isLoading = signal(false);
  errorMessage = signal<string>('');

  currentDate = new Date();

  ngOnInit(): void {
    this.loadFees();
  }

  loadFees(): void {
    this.isLoading.set(true);
    this.errorMessage.set('');
    this.adminService.getAssetFees().subscribe({
      next: (fees) => {
        this.assetFees.set(fees);
        this.isLoading.set(false);
      },
      error: (error) => {
        console.error('Failed to load fees:', error);
        this.errorMessage.set('Failed to load fees. Please try again.');
        this.isLoading.set(false);
      },
    });
  }

  formatCurrency(value: number): string {
    return new Intl.NumberFormat('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 6,
    }).format(value);
  }

  formatAssetClass(assetType: string): string {
    return assetType.charAt(0).toUpperCase() + assetType.slice(1).toLowerCase();
  }

  getAssetIcon(assetType: string): string {
    switch (assetType.toUpperCase()) {
      case 'EQUITY':
        return 'fas fa-chart-pie';
      case 'FX':
        return 'fas fa-money-bill';
      case 'CRYPTO':
        return 'fas fa-bitcoin';
      default:
        return 'fas fa-coins';
    }
  }
}
