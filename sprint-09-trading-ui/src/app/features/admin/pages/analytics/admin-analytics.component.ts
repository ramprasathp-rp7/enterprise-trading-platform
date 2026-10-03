import { Component, inject, signal, computed, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration } from 'chart.js';
import { firstValueFrom } from 'rxjs';
import { AdminService } from '../../services/admin.service';
import {
  TradeVolumeData,
  FillRateData,
  AccountActivityData,
  ExposureData,
} from '../../admin.models';

/**
 * Admin Analytics Dashboard Component
 * Shows key trading metrics, fill rates, active accounts, and exposure
 *
 * Requires provideCharts(withDefaultRegisterables()) in app.config.ts
 */
@Component({
  selector: 'app-admin-analytics',
  standalone: true,
  imports: [CommonModule, FormsModule, BaseChartDirective],
  templateUrl: './admin-analytics.component.html',
  styleUrls: ['./admin-analytics.component.css'],
})
export class AdminAnalyticsComponent {
  private adminService = inject(AdminService);

  // Filter controls
  dateRange = signal<'7d' | '30d' | '90d'>('30d');
  groupBy = signal<'DAY' | 'WEEK' | 'MONTH'>('DAY');
  selectedAssetClass = signal<string>('');

  // Data signals
  tradeVolumeData = signal<TradeVolumeData[]>([]);
  fillRateData = signal<FillRateData[]>([]);
  accountActivityData = signal<AccountActivityData[]>([]);
  exposureData = signal<ExposureData[]>([]);
  isLoading = signal(false);

  // Guards against stale responses overwriting newer ones
  private requestId = 0;

  // Computed aggregated metrics
  totalVolume = computed(() =>
    this.tradeVolumeData().reduce((sum, item) => sum + item.volume, 0)
  );

  totalTradeValue = computed(() =>
    this.tradeVolumeData().reduce((sum, item) => sum + item.value, 0)
  );

  averageFillRate = computed(() => {
    const data = this.fillRateData();
    if (data.length === 0) return 0;
    const totalFillRate = data.reduce((sum, item) => sum + item.fillRate, 0);
    return totalFillRate / data.length;
  });

  topAccounts = computed(() => this.accountActivityData().slice(0, 10));

  // Copy before sorting so the source signal's array is not mutated
  topInstruments = computed(() =>
    [...this.exposureData()]
      .sort((a, b) => b.totalValue - a.totalValue)
      .slice(0, 10)
  );

  // Chart data
  volumeChartData = computed(() => ({
    labels: this.tradeVolumeData().map((d) => d.date),
    datasets: [
      {
        label: 'Trade Volume',
        data: this.tradeVolumeData().map((d) => d.volume),
        borderColor: '#3b82f6',
        backgroundColor: 'rgba(59, 130, 246, 0.1)',
        borderWidth: 2,
        tension: 0.4,
        fill: true,
      },
    ],
  }));

  fillRateChartData = computed(() => ({
    labels: this.fillRateData().map((d) => d.date),
    datasets: [
      {
        label: 'Fill Rate %',
        data: this.fillRateData().map((d) => d.fillRate * 100),
        backgroundColor: '#10b981',
        borderColor: '#059669',
        borderWidth: 1,
      },
    ],
  }));

  accountsChartData = computed(() => ({
    labels: this.topAccounts().map((a) => a.accountId),
    datasets: [
      {
        label: 'Order Count',
        data: this.topAccounts().map((a) => a.orderCount),
        backgroundColor: '#8b5cf6',
        borderColor: '#7c3aed',
        borderWidth: 1,
      },
    ],
  }));

  exposureChartData = computed(() => ({
    labels: this.topInstruments().map((i) => i.symbol),
    datasets: [
      {
        label: 'Exposure Value',
        data: this.topInstruments().map((i) => i.totalValue),
        backgroundColor: [
          '#ec4899', '#f59e0b', '#14b8a6', '#06b6d4', '#6366f1',
          '#f97316', '#84cc16', '#0ea5e9', '#d946ef', '#14b8a6',
        ],
      },
    ],
  }));

  lineChartOptions: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: true,
    plugins: { legend: { display: true } },
    scales: { y: { beginAtZero: true } },
  };

  barChartOptions: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: true,
    plugins: { legend: { display: true } },
    scales: { y: { beginAtZero: true } },
  };

  pieChartOptions: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: true,
    plugins: { legend: { position: 'bottom' } },
  };

  currentDate = new Date();

  constructor() {
    // Runs once on creation, then again whenever dateRange, groupBy
    // or selectedAssetClass changes (so no separate ngOnInit load is needed).
    effect(() => {
      const range = this.dateRange();
      const group = this.groupBy();
      const assetClass = this.selectedAssetClass();
      this.loadAnalyticsData(range, group, assetClass);
    });
  }

  private loadAnalyticsData(
    range: '7d' | '30d' | '90d',
    groupBy: 'DAY' | 'WEEK' | 'MONTH',
    assetClass: string
  ): void {
    const endDate = new Date();
    const startDate = new Date();

    // Calculate start date based on range
    if (range === '7d') {
      startDate.setDate(startDate.getDate() - 7);
    } else if (range === '30d') {
      startDate.setMonth(startDate.getMonth() - 1);
    } else {
      startDate.setMonth(startDate.getMonth() - 3);
    }

    const currentRequest = ++this.requestId;
    this.isLoading.set(true);

    // Load all analytics data in parallel
    Promise.all([
      firstValueFrom(this.adminService.getTradeVolume(startDate, endDate, groupBy)),
      firstValueFrom(this.adminService.getFillRate(startDate, endDate, groupBy)),
      firstValueFrom(this.adminService.getActiveAccounts(10)),
      firstValueFrom(this.adminService.getExposure(assetClass || undefined)),
    ])
      .then(([volumes, fillRates, accounts, exposure]) => {
        // Ignore responses from superseded requests
        if (currentRequest !== this.requestId) return;
        this.tradeVolumeData.set(volumes || []);
        this.fillRateData.set(fillRates || []);
        this.accountActivityData.set(accounts || []);
        this.exposureData.set(exposure || []);
      })
      .catch((error) => {
        if (currentRequest !== this.requestId) return;
        console.error('Failed to load analytics data:', error);
      })
      .finally(() => {
        if (currentRequest === this.requestId) {
          this.isLoading.set(false);
        }
      });
  }

  formatCurrency(value: number): string {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).format(value);
  }

  formatNumber(value: number): string {
    return new Intl.NumberFormat('en-US', {
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).format(value);
  }

  formatPercent(value: number): string {
    return (value * 100).toFixed(2) + '%';
  }

  getStatusColor(fillRate: number): string {
    if (fillRate >= 0.8) return 'status-excellent';
    if (fillRate >= 0.6) return 'status-good';
    if (fillRate >= 0.4) return 'status-fair';
    return 'status-poor';
  }

  onDateRangeChange(range: '7d' | '30d' | '90d'): void {
    this.dateRange.set(range);
  }

  onGroupByChange(group: 'DAY' | 'WEEK' | 'MONTH'): void {
    this.groupBy.set(group);
  }
}