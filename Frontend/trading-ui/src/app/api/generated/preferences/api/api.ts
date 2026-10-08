export * from './adminNotifications.service';
import { AdminNotificationsService } from './adminNotifications.service';
export * from './adminPreferences.service';
import { AdminPreferencesService } from './adminPreferences.service';
export * from './preferences.service';
import { PreferencesService } from './preferences.service';
export const APIS = [AdminNotificationsService, AdminPreferencesService, PreferencesService];
