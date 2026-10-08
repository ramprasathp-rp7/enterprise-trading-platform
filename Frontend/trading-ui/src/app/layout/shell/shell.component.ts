import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { NavbarComponent } from '../navbar/navbar.component';
import { SidebarComponent } from '../sidebar/sidebar.component';
import { FooterComponent } from '../footer/footer.component';
import { OrderStatusWatcherService } from '../../core/notifications/order-status-watcher.service';
import { PriceAlertWatcherService } from '../../features/watchlist/alerts/price-alert-watcher.service';

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

  /** Customer pages: announce order status changes and fired price alerts wherever the user is */
  ngOnInit(): void {
    this.orderWatcher.start();
    this.priceAlertWatcher.start();
  }

  ngOnDestroy(): void {
    this.orderWatcher.stop();
    this.priceAlertWatcher.stop();
  }
}
