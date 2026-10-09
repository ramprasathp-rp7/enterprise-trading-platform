import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { NavbarComponent } from '../navbar/navbar.component';
import { SidebarComponent } from '../sidebar/sidebar.component';
import { FooterComponent } from '../footer/footer.component';
import { OrderStatusWatcherService } from '../../core/notifications/order-status-watcher.service';
import { PriceAlertWatcherService } from '../../features/watchlist/alerts/price-alert-watcher.service';
import { InstrumentCatalogService } from '../../features/instruments/services/instrument-catalog.service';
import { AccountSettingsStore } from '../../core/settings/account-settings.store';

/**
 * Shell Component
 * 
 * Root layout component that renders:
 * - Top navigation bar
 * - Left sidebar navigation
 * - Main content area with router outlet
 * - Footer
 */
@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [CommonModule, RouterOutlet, NavbarComponent, SidebarComponent, FooterComponent],
  templateUrl: './shell.component.html',
  styleUrls: ['./shell.component.css']
})
export class ShellComponent implements OnInit, OnDestroy {
  private readonly orderWatcher = inject(OrderStatusWatcherService);
  private readonly priceAlertWatcher = inject(PriceAlertWatcherService);
  private readonly catalog = inject(InstrumentCatalogService);
  private readonly settings = inject(AccountSettingsStore);

  /**
   * Customer pages: announce order status changes and fired price alerts wherever the user is,
   * and load the instrument catalogue (including discovered symbols) and the trading settings
   */
  ngOnInit(): void {
    this.orderWatcher.start();
    this.priceAlertWatcher.start();
    this.catalog.load();
    this.settings.ensureLoaded();
  }

  ngOnDestroy(): void {
    this.orderWatcher.stop();
    this.priceAlertWatcher.stop();
  }
}
