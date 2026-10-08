export * from './priceAlerts.service';
import { PriceAlertsService } from './priceAlerts.service';
export * from './watchlists.service';
import { WatchlistsService } from './watchlists.service';
export const APIS = [PriceAlertsService, WatchlistsService];
