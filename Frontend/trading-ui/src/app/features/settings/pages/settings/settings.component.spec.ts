import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { SettingsComponent } from './settings.component';
import { PreferencesStore } from '../../preferences.store';
import { AlertChannelEntry, PreferencesResponse } from '../../../../api/generated/preferences';
import { CoreAuthService } from '../../../../core/auth/core-auth.service';
import { AccountSettings, AccountSettingsStore, DEFAULT_ACCOUNT_SETTINGS } from '../../../../core/settings/account-settings.store';
import { ThemeService } from '../../../../core/services/theme.service';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { AccountsService } from '../../../../api/generated/trade';

describe('SettingsComponent', () => {
  let fixture: ComponentFixture<SettingsComponent>;
  let component: SettingsComponent;
  let preferences: ReturnType<typeof signal<PreferencesResponse | null>>;
  let store: Record<string, unknown>;
  let admin: boolean;
  let tradingSettings: ReturnType<typeof signal<AccountSettings>>;
  let settingsStore: Record<string, unknown>;

  const el = (testId: string): HTMLElement | null =>
    fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);

  const create = (extraProviders: unknown[] = []) => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [SettingsComponent],
      providers: [
        ...(extraProviders as never[]),
        { provide: PreferencesStore, useValue: store },
        { provide: AccountSettingsStore, useValue: settingsStore },
        { provide: CoreAuthService, useValue: { isAdmin: () => admin, currentUser: () => (admin ? null : { id: 'u-7', accountId: 7 }) } },
      ],
    });
    fixture = TestBed.createComponent(SettingsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  };

  beforeEach(() => {
    admin = false;
    preferences = signal<PreferencesResponse | null>({
      accountId: 7,
      alertChannels: [{ channel: 'IN_APP', contactDetail: null }],
      configured: false,
      updatedAt: null,
    });
    store = {
      preferences,
      isLoading: signal(false),
      error: signal<string | null>(null),
      load: vi.fn(),
      save: vi.fn((alertChannels: AlertChannelEntry[]) => {
        const saved = { accountId: 7, alertChannels, configured: true, updatedAt: 'now' } as PreferencesResponse;
        preferences.set(saved);
        return of(saved);
      }),
      resetToDefault: vi.fn(() => of(undefined)),
    };
    localStorage.removeItem(ThemeService.STORAGE_KEY);
    tradingSettings = signal<AccountSettings>({ ...DEFAULT_ACCOUNT_SETTINGS });
    settingsStore = {
      settings: tradingSettings,
      error: signal<string | null>(null),
      ensureLoaded: vi.fn(),
      save: vi.fn((input: Pick<AccountSettings, 'orderBudget' | 'market' | 'defaultQuantity'>) => {
        const saved = { ...input, configured: true, updatedAt: '2026-10-09T08:00:00Z' };
        tradingSettings.set(saved);
        return of(saved);
      }),
      reset: vi.fn(() => {
        tradingSettings.set({ ...DEFAULT_ACCOUNT_SETTINGS });
        return of(undefined);
      }),
    };
    create();
  });

  describe('appearance', () => {
    it('offers light, dark and system themes and applies a choice straight away', () => {
      const theme = TestBed.inject(ThemeService);

      (el('theme-dark') as HTMLButtonElement).click();
      fixture.detectChanges();
      expect(theme.mode()).toBe('dark');
      expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
      expect(el('theme-dark')?.getAttribute('aria-checked')).toBe('true');

      (el('theme-system') as HTMLButtonElement).click();
      fixture.detectChanges();
      expect(theme.mode()).toBe('system');
      expect(localStorage.getItem(ThemeService.STORAGE_KEY)).toBe('system');
    });
  });

  describe('trading settings', () => {
    const type = (testId: string, value: string) => {
      const input = el(testId) as HTMLInputElement;
      input.value = value;
      input.dispatchEvent(new Event('input'));
      fixture.detectChanges();
    };

    it('loads the stored settings into the form', () => {
      expect(settingsStore['ensureLoaded']).toHaveBeenCalled();
      tradingSettings.set({ orderBudget: 5000, market: 'NSE', defaultQuantity: 10, configured: true, updatedAt: null });
      fixture.detectChanges();

      expect(component.budgetText()).toBe('5000');
      expect(component.quantityText()).toBe('10');
      expect(component.marketChoice()).toBe('NSE');
      expect((el('market-NSE') as HTMLInputElement).checked).toBe(true);
    });

    it('saves budget, market and default quantity', async () => {
      type('budget-input', '2500.555');
      type('quantity-input', '25');
      (el('market-USA') as HTMLInputElement).click();
      fixture.detectChanges();

      (el('save-trading') as HTMLButtonElement).click();
      fixture.detectChanges();

      expect(settingsStore['save']).toHaveBeenCalledWith({ orderBudget: 2500.555, market: 'USA', defaultQuantity: 25 });
      expect(el('trading-feedback')?.textContent).toContain('saved');
    });

    it('treats empty fields as no budget and no default quantity', () => {
      tradingSettings.set({ orderBudget: 5000, market: 'ALL', defaultQuantity: 10, configured: true, updatedAt: null });
      fixture.detectChanges();
      (el('budget-clear') as HTMLButtonElement).click();
      type('quantity-input', '');

      (el('save-trading') as HTMLButtonElement).click();

      expect(settingsStore['save']).toHaveBeenCalledWith({ orderBudget: null, market: 'ALL', defaultQuantity: null });
    });

    it('will not save an invalid budget or quantity', () => {
      type('budget-input', '-5');
      expect(el('budget-error')).not.toBeNull();
      expect((el('save-trading') as HTMLButtonElement).disabled).toBe(true);

      type('budget-input', '100');
      type('quantity-input', '1.5');
      expect(el('quantity-error')).not.toBeNull();
      expect((el('save-trading') as HTMLButtonElement).disabled).toBe(true);
    });

    it('resets to the defaults', () => {
      tradingSettings.set({ orderBudget: 5000, market: 'USA', defaultQuantity: 10, configured: true, updatedAt: null });
      fixture.detectChanges();

      (el('reset-trading') as HTMLButtonElement).click();
      fixture.detectChanges();

      expect(settingsStore['reset']).toHaveBeenCalled();
      expect(component.marketChoice()).toBe('ALL');
      expect(component.budgetText()).toBe('');
    });

    it('is not offered to admins, who have no trading account', () => {
      admin = true;
      create();

      expect(el('trading-section')).toBeNull();
      expect(el('appearance-section')).not.toBeNull();
      expect(el('notifications-section')).not.toBeNull();
      expect(settingsStore['ensureLoaded']).not.toHaveBeenCalledTimes(2);
    });
  });

  it('tells customers it is about their orders and price alerts', () => {
    expect(el('preferences-subtitle')?.textContent).toContain('orders and your price alerts');
  });

  it('tells admins it is about customer registrations, with the same channel choices', () => {
    admin = true;
    create();

    expect(el('preferences-subtitle')?.textContent).toContain('customer you register');
    expect(el('channel-EMAIL')).not.toBeNull();
    expect(el('channel-SMS')).not.toBeNull();
    expect(el('channel-IN_APP')).not.toBeNull();
  });

  it('loads preferences and explains the default when nothing is stored', () => {
    expect(store['load']).toHaveBeenCalled();
    expect(el('preferences-default-banner')).not.toBeNull();
    expect([...component.selected()]).toEqual(['IN_APP']);
    expect(el('contact-email-input')).toBeNull();
    expect(el('contact-sms-input')).toBeNull();
    expect(el('reset-preferences')).toBeNull();
  });

  it('fills the form from several stored channels', () => {
    preferences.set({
      accountId: 7,
      alertChannels: [
        { channel: 'EMAIL', contactDetail: 'customer@example.com' },
        { channel: 'SMS', contactDetail: '+447700900123' },
      ],
      configured: true,
      updatedAt: 'x',
    });
    fixture.detectChanges();

    expect(component.isSelected('EMAIL')).toBe(true);
    expect(component.isSelected('SMS')).toBe(true);
    expect(component.isSelected('IN_APP')).toBe(false);
    expect(component.contacts()).toEqual({ EMAIL: 'customer@example.com', SMS: '+447700900123' });
    expect(el('contact-email-input')).not.toBeNull();
    expect(el('contact-sms-input')).not.toBeNull();
    expect(el('preferences-default-banner')).toBeNull();
    expect(el('reset-preferences')).not.toBeNull();
  });

  it('shows when the preferences were last saved, or that the default is in use', () => {
    const status = () => fixture.nativeElement.querySelector('[data-testid="preferences-status"]').textContent;
    expect(status()).toContain('Using the default: in-app only');

    preferences.set({ accountId: 7, alertChannels: [{ channel: 'IN_APP' }], configured: true, updatedAt: '2026-10-06T09:00:00Z' } as PreferencesResponse);
    fixture.detectChanges();
    expect(component.lastSaved()).toEqual(new Date('2026-10-06T09:00:00Z'));
    expect(status()).toContain('Last saved Oct 6, 2026');

    preferences.set({ accountId: 7, alertChannels: [{ channel: 'IN_APP' }], configured: true, updatedAt: 'not-a-date' } as PreferencesResponse);
    fixture.detectChanges();
    expect(component.lastSaved()).toBeNull();
    expect(status()).toContain('Saved');
  });

  it('requires at least one channel', () => {
    component.toggle('IN_APP');
    fixture.detectChanges();

    expect(component.nothingSelected()).toBe(true);
    expect(component.canSave()).toBe(false);
    expect(el('channels-error')).not.toBeNull();
  });

  it('requires a valid email once email is ticked', () => {
    component.toggle('EMAIL');
    fixture.detectChanges();
    expect(el('contact-email-input')).not.toBeNull();
    expect(component.canSave()).toBe(false);

    component.setContact('EMAIL', 'not-an-email');
    fixture.detectChanges();
    expect(component.emailError()).toBe('Enter a valid email address.');
    expect(el('contact-email-error')).not.toBeNull();

    component.setContact('EMAIL', 'customer@example.com');
    expect(component.canSave()).toBe(true);
  });

  it('accepts an international number with spaces for SMS and rejects a local one', () => {
    component.toggle('SMS');
    component.setContact('SMS', '07700 900123');
    fixture.detectChanges();
    expect(component.smsError()).toContain('international format');
    expect(el('contact-sms-error')).not.toBeNull();

    component.setContact('SMS', '+44 7700 900123');
    expect(component.smsError()).toBeNull();
  });

  it('asks for a number when SMS is ticked but left empty', () => {
    component.toggle('SMS');

    expect(component.smsError()).toBe('Mobile number is required for SMS alerts.');
    expect(component.canSave()).toBe(false);
  });

  it('ignores a contact detail for a channel that is no longer ticked', () => {
    component.toggle('EMAIL');
    component.setContact('EMAIL', 'nope');
    expect(component.canSave()).toBe(false);

    component.toggle('EMAIL');

    expect(component.emailError()).toBeNull();
    expect(component.canSave()).toBe(true);
  });

  it('saves every ticked channel in channel order', () => {
    component.toggle('SMS');
    component.toggle('EMAIL');
    component.setContact('SMS', '+447700900123');
    component.setContact('EMAIL', 'customer@example.com');

    component.save();
    fixture.detectChanges();

    expect(store['save']).toHaveBeenCalledWith([
      { channel: 'EMAIL', contactDetail: 'customer@example.com' },
      { channel: 'SMS', contactDetail: '+447700900123' },
      { channel: 'IN_APP', contactDetail: null },
    ]);
    expect(el('preferences-feedback')?.textContent).toContain('saved');
  });

  it('does not save while a contact detail is invalid', () => {
    component.toggle('EMAIL');

    component.save();

    expect(store['save']).not.toHaveBeenCalled();
  });

  it('shows the server error when saving fails', () => {
    (store['save'] as ReturnType<typeof vi.fn>).mockReturnValueOnce(throwError(() => new Error('Contact detail is not a valid email address')));

    component.save();
    fixture.detectChanges();

    expect(el('preferences-feedback')?.textContent).toContain('not a valid email');
    expect(component.saving()).toBe(false);
  });

  it('resets to default', () => {
    component.resetToDefault();
    fixture.detectChanges();

    expect(store['resetToDefault']).toHaveBeenCalled();
    expect(el('preferences-feedback')?.textContent).toContain('in-app');
  });

  it('shows a reset failure', () => {
    (store['resetToDefault'] as ReturnType<typeof vi.fn>).mockReturnValueOnce(throwError(() => new Error('Cannot reach the trading service.')));

    component.resetToDefault();
    fixture.detectChanges();

    expect(el('preferences-feedback')?.textContent).toContain('Cannot reach');
  });

  describe('section navigation', () => {
    afterEach(() => vi.useRealTimers());

    it('glides the cards to a section and keeps the clicked one highlighted while it moves', () => {
      vi.useFakeTimers();
      const column = el('settings-scroll') as HTMLElement;
      const scrollTo = vi.fn();
      (column as unknown as { scrollTo: unknown }).scrollTo = scrollTo;

      (el('nav-section-trading') as HTMLButtonElement).click();
      fixture.detectChanges();

      expect(component.activeSection()).toBe('trading');
      expect(scrollTo).toHaveBeenCalledWith(expect.objectContaining({ behavior: 'smooth' }));
      // A scroll event mid-glide does not move the highlight
      column.dispatchEvent(new Event('scroll'));
      expect(component.activeSection()).toBe('trading');

      vi.advanceTimersByTime(1000);
      column.dispatchEvent(new Event('scroll'));
      // Scrolled to the end (an empty test layout): the last section is the one being read
      expect(component.activeSection()).toBe('notifications');
    });

    it('falls back to setting scrollTop where smooth scrolling is unavailable', () => {
      const column = el('settings-scroll') as HTMLElement;
      (column as unknown as { scrollTo: unknown }).scrollTo = undefined;

      component.scrollTo('notifications');

      expect(component.activeSection()).toBe('notifications');
      // Lands 4px above the card; jsdom has no layout and does not clamp the value as browsers do
      expect(column.scrollTop).toBe(-4);
    });

    it('opens on the section named in the link', async () => {
      create([{ provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({ section: 'trading' }) } } }]);
      await fixture.whenStable();

      expect(component.activeSection()).toBe('trading');
    });
  });

  describe('trading settings errors and currency', () => {
    it('shows a failed save and a failed reset', () => {
      component.budgetText.set('100');
      component.tradingDirty.set(true);
      (settingsStore['save'] as ReturnType<typeof vi.fn>).mockReturnValue(throwError(() => new Error('Budget too large')));
      component.saveTrading();
      expect(component.tradingFeedback()).toEqual({ text: 'Budget too large', error: true });

      (settingsStore['reset'] as ReturnType<typeof vi.fn>).mockReturnValue(throwError(() => new Error('Offline')));
      component.resetTrading();
      expect(component.tradingFeedback()).toEqual({ text: 'Offline', error: true });
      expect(component.tradingSaving()).toBe(false);
    });

    it('shows the budget in the account currency', () => {
      create([{ provide: AccountsService, useValue: { getBalance: () => of({ accountId: 7, cashBalance: 1, currency: 'INR' }) } }]);

      expect(component.accountCurrency()).toBe('INR');
    });
  });
});
