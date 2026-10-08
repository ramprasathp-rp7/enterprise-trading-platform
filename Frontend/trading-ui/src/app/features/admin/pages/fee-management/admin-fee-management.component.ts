import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { AdminService } from '../../services/admin.service';
import { FeeStructureResponse } from '../../../../api/generated/admin';
import { PLATFORM_ACCOUNT_CURRENCY, formatMoney } from '../../../../shared/currency';

/** Values being typed into an edited row; the percentage is in percent (0.1 = 0.1%) */
interface FeeDraft {
  flatFee: number | null;
  percentFee: number | null;
}

/**
 * Admin Fee Management Component
 * Displays asset class fee structures and lets an admin edit them in place.
 * A saved edit applies to every order placed afterwards.
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
  assetFees = signal<FeeStructureResponse[]>([]);
  isLoading = signal(false);
  errorMessage = signal<string>('');
  successMessage = signal<string>('');

  // Edit state: one row at a time
  editingAssetType = signal<string | null>(null);
  draft: FeeDraft = { flatFee: null, percentFee: null };
  editError = signal<string>('');
  isSaving = signal(false);

  currentDate = new Date();

  ngOnInit(): void {
    this.loadFees();
  }

  loadFees(): void {
    this.isLoading.set(true);
    this.errorMessage.set('');
    this.adminService.getAssetFees().subscribe({
      next: (fees: FeeStructureResponse[]) => {
        this.assetFees.set(fees);
        this.isLoading.set(false);
      },
      error: (error: any) => {
        console.error('Failed to load fees:', error);
        this.errorMessage.set('Failed to load fees. Please try again.');
        this.isLoading.set(false);
      },
    });
  }

  startEdit(fee: FeeStructureResponse): void {
    this.editingAssetType.set(fee.assetType);
    this.draft = {
      flatFee: fee.flatFee,
      percentFee: Number((fee.percentageFee * 100).toFixed(4)),
    };
    this.editError.set('');
    this.successMessage.set('');
  }

  cancelEdit(): void {
    this.editingAssetType.set(null);
    this.editError.set('');
  }

  isEditing(fee: FeeStructureResponse): boolean {
    return this.editingAssetType() === fee.assetType;
  }

  /** Mirrors the server rules so the admin sees a problem before saving; null when valid */
  validateDraft(): string | null {
    const { flatFee, percentFee } = this.draft;
    if (flatFee === null || flatFee === undefined || percentFee === null || percentFee === undefined
        || Number.isNaN(flatFee) || Number.isNaN(percentFee)) {
      return 'Enter both a flat fee and a percentage fee.';
    }
    if (flatFee < 0 || percentFee < 0) {
      return 'Fees cannot be negative.';
    }
    if (flatFee === 0 && percentFee === 0) {
      return 'At least one fee must be greater than zero.';
    }
    if (percentFee > 100) {
      return 'Percentage fee cannot exceed 100%.';
    }
    if (!this.hasAtMostDecimals(flatFee, 2)) {
      return 'Flat fee can have at most 2 decimal places.';
    }
    if (!this.hasAtMostDecimals(percentFee, 4)) {
      return 'Percentage fee can have at most 4 decimal places.';
    }
    return null;
  }

  saveEdit(): void {
    const assetType = this.editingAssetType();
    if (!assetType || this.isSaving()) return;

    const invalid = this.validateDraft();
    if (invalid) {
      this.editError.set(invalid);
      return;
    }

    const request = {
      flatFee: this.draft.flatFee as number,
      // percent → decimal form stored by the platform (0.1% → 0.001)
      percentageFee: Number(((this.draft.percentFee as number) / 100).toFixed(6)),
    };

    this.isSaving.set(true);
    this.editError.set('');
    this.adminService.updateAssetFee(assetType, request).subscribe({
      next: (updated: FeeStructureResponse) => {
        // Reflect the stored values in the table straight away
        this.assetFees.update((fees) =>
          fees.map((f) => (f.assetType === updated.assetType ? updated : f))
        );
        this.isSaving.set(false);
        this.editingAssetType.set(null);
        this.successMessage.set(
          `${this.formatAssetClass(updated.assetType)} fees updated. New orders will use the new fees.`
        );
      },
      error: (error: unknown) => {
        console.error('Failed to update fee:', error);
        this.isSaving.set(false);
        this.editError.set(this.describeSaveError(error));
      },
    });
  }

  /** The flat fee, charged in the account's currency (the fee schedule is USD) */
  formatCurrency(value: number): string {
    return formatMoney(value, PLATFORM_ACCOUNT_CURRENCY, { maxDecimals: 6 });
  }

  /** Decimal-form fee as a percentage: 0.001 → "0.10%", 0.00005 → "0.005%" */
  formatPercentage(value: number): string {
    return (
      (value * 100).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 4 }) + '%'
    );
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
        return 'fab fa-bitcoin';
      default:
        return 'fas fa-coins';
    }
  }

  private hasAtMostDecimals(value: number, decimals: number): boolean {
    return Math.abs(value - Number(value.toFixed(decimals))) < 1e-9;
  }

  private describeSaveError(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      switch (error.status) {
        case 403:
          return 'You do not have permission to change fees.';
        case 404:
          return 'This asset class no longer exists. Reload the page.';
        case 422:
          return error.error?.message && error.error.message !== 'Invalid input'
            ? error.error.message
            : 'The server rejected these fees. Check the values and try again.';
      }
    }
    return 'Failed to save fees. Please try again.';
  }
}
