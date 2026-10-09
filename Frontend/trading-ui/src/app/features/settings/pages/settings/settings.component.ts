import { Component, DestroyRef, ElementRef, Injector, OnInit, afterNextRender, computed, effect, inject, signal, untracked, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { AlertChannel, AlertChannelEntry } from '../../../../api/generated/preferences';
import { AccountsService } from '../../../../api/generated/trade';
import { PreferencesStore } from '../../preferences.store';
import { CoreAuthService } from '../../../../core/auth/core-auth.service';
import { ThemeMode, ThemeService } from '../../../../core/services/theme.service';
import { AccountSettingsStore, Market } from '../../../../core/settings/account-settings.store';
import { InstrumentCatalogService, inMarket } from '../../../instruments/services/instrument-catalog.service';

type SectionId = 'appearance' | 'trading' | 'notifications';

interface SectionLink {
  id: SectionId;
  label: string;
  hint: string;
  icon: string;
}

interface ThemeOption {
  value: ThemeMode;
  label: string;
  hint: string;
  icon: string;
}

interface MarketOption {
  value: Market;
  label: string;
  hint: string;
  icon: string;
}

/** Same bounds as account-settings-api.yaml */
const MAX_BUDGET = 1_000_000_000;
const MAX_QUANTITY = 1_000_000;

/** The channels that need an address */
type ContactChannel = typeof AlertChannel.Email | typeof AlertChannel.Sms;

interface ChannelOption {
  value: AlertChannel;
  label: string;
  icon: string;
  hint: string;
}

/** Mirrors the server rules in contracts/preferences-api.yaml so mistakes show before saving */
const EMAIL_PATTERN = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}$/;
const E164_PATTERN = /^\+[1-9]\d{7,14}$/;

/** Display and request order, matching the order the server returns */
const CHANNEL_ORDER: AlertChannel[] = [AlertChannel.Email, AlertChannel.Sms, AlertChannel.InApp];

/**
 * Preferences page: choose one or more channels the platform reaches you on. Customers hear about
 * their trades and price alerts; admins (at /admin/preferences) about customer registrations.
 */
@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './settings.component.html',
  styleUrls: ['./settings.component.css'],
})
export class SettingsComponent implements OnInit {
  readonly store = inject(PreferencesStore);
  private readonly destroyRef = inject(DestroyRef);
  private readonly authService = inject(CoreAuthService);
  private readonly injector = inject(Injector);
  private readonly route = inject(ActivatedRoute, { optional: true });
  readonly theme = inject(ThemeService);
  readonly settingsStore = inject(AccountSettingsStore);
  private readonly catalog = inject(InstrumentCatalogService);

  /** Admins are notified about different events, so the page explains that */
  readonly isAdmin = this.authService.isAdmin();
  readonly subtitle = this.isAdmin
    ? 'Choose how we tell you when a customer you register is set up.'
    : 'Choose how we tell you about filled, rejected and cancelled orders and your price alerts.';

  readonly sections: SectionLink[] = this.isAdmin
    ? [
        { id: 'appearance', label: 'Appearance', hint: 'Theme', icon: 'fa-palette' },
        { id: 'notifications', label: 'Notifications', hint: 'Email, SMS, in-app', icon: 'fa-bell' },
      ]
    : [
        { id: 'appearance', label: 'Appearance', hint: 'Theme', icon: 'fa-palette' },
        { id: 'trading', label: 'Trading', hint: 'Budget, quantity, markets', icon: 'fa-sliders' },
        { id: 'notifications', label: 'Notifications', hint: 'Email, SMS, in-app', icon: 'fa-bell' },
      ];
  readonly activeSection = signal<SectionId>('appearance');
  private readonly column = viewChild<ElementRef<HTMLElement>>('column');
  private jumpingTo: SectionId | null = null;
  private jumpTimer?: ReturnType<typeof setTimeout>;

  readonly themeOptions: ThemeOption[] = [
    { value: 'light', label: 'Light', hint: 'Bright and crisp', icon: 'fa-sun' },
    { value: 'dark', label: 'Dark', hint: 'Easy on the eyes at night', icon: 'fa-moon' },
    { value: 'system', label: 'System', hint: 'Follows your device', icon: 'fa-circle-half-stroke' },
  ];

  readonly marketOptions: MarketOption[] = [
    { value: 'USA', label: 'US markets', hint: 'NYSE and NASDAQ', icon: 'fa-flag-usa' },
    { value: 'NSE', label: 'India · NSE', hint: 'National Stock Exchange of India', icon: 'fa-indian-rupee-sign' },
    { value: 'ALL', label: 'All markets', hint: 'Every exchange, FX and crypto', icon: 'fa-earth-americas' },
  ];

  /** Trading form: text fields so a half-typed number is never lost */
  readonly budgetText = signal('');
  readonly quantityText = signal('');
  readonly marketChoice = signal<Market>('ALL');
  readonly tradingDirty = signal(false);
  readonly tradingSaving = signal(false);
  readonly tradingFeedback = signal<{ text: string; error: boolean } | null>(null);
  /** The account's currency, for the budget field (cash and the budget are in it) */
  readonly accountCurrency = signal('USD');

  /** How many listed instruments each market shows */
  readonly marketCounts = computed<Record<Market, number>>(() => {
    const all = this.catalog.all();
    return {
      USA: all.filter((i) => inMarket(i, 'USA')).length,
      NSE: all.filter((i) => inMarket(i, 'NSE')).length,
      ALL: all.length,
    };
  });

  readonly budgetError = computed<string | null>(() => {
    const text = this.budgetText().trim();
    if (!text) {
      return null;
    }
    const value = Number(text);
    if (!Number.isFinite(value) || value <= 0) {
      return 'Enter an amount greater than zero, or leave it empty for no budget.';
    }
    return value > MAX_BUDGET ? 'That budget is too large.' : null;
  });

  readonly quantityError = computed<string | null>(() => {
    const text = this.quantityText().trim();
    if (!text) {
      return null;
    }
    const value = Number(text);
    if (!Number.isInteger(value) || value < 1) {
      return 'Enter a whole number of at least 1, or leave it empty.';
    }
    return value > MAX_QUANTITY ? 'That quantity is too large.' : null;
  });

  readonly canSaveTrading = computed(() => !this.tradingSaving() && !this.budgetError() && !this.quantityError());

  readonly tradingLastSaved = computed<Date | null>(() => {
    const updated = this.settingsStore.settings().updatedAt;
    const date = updated ? new Date(updated) : null;
    return date && !Number.isNaN(date.getTime()) ? date : null;
  });

  readonly channels: ChannelOption[] = [
    { value: AlertChannel.Email, label: 'Email', icon: 'fa-envelope', hint: 'We email the address below.' },
    { value: AlertChannel.Sms, label: 'SMS', icon: 'fa-mobile-screen', hint: 'We text the number below.' },
    { value: AlertChannel.InApp, label: 'In-app', icon: 'fa-bell', hint: 'Notifications appear in your inbox here.' },
  ];

  readonly selected = signal<ReadonlySet<AlertChannel>>(new Set([AlertChannel.InApp]));
  readonly contacts = signal<Record<ContactChannel, string>>({ EMAIL: '', SMS: '' });
  readonly saving = signal(false);
  readonly feedback = signal<{ text: string; error: boolean } | null>(null);

  readonly emailSelected = computed(() => this.selected().has(AlertChannel.Email));
  readonly smsSelected = computed(() => this.selected().has(AlertChannel.Sms));
  readonly nothingSelected = computed(() => this.selected().size === 0);

  /** Client-side checks per channel; the server applies the same rules and has the final say */
  readonly emailError = computed<string | null>(() => {
    if (!this.emailSelected()) {
      return null;
    }
    const value = this.contacts().EMAIL.trim();
    if (!value) {
      return 'Email address is required for email alerts.';
    }
    return value.length <= 254 && EMAIL_PATTERN.test(value) ? null : 'Enter a valid email address.';
  });

  readonly smsError = computed<string | null>(() => {
    if (!this.smsSelected()) {
      return null;
    }
    const value = this.contacts().SMS.trim();
    if (!value) {
      return 'Mobile number is required for SMS alerts.';
    }
    return E164_PATTERN.test(value.replace(/[\s\-().]/g, ''))
      ? null
      : 'Enter the number in international format, for example +447700900123.';
  });

  /** When the stored preferences were last saved, or null if never / unknown */
  readonly lastSaved = computed<Date | null>(() => {
    const prefs = this.store.preferences();
    if (!prefs?.configured || !prefs.updatedAt) {
      return null;
    }
    const date = new Date(prefs.updatedAt);
    return Number.isNaN(date.getTime()) ? null : date;
  });

  readonly canSave = computed(
    () => !this.saving() && !this.nothingSelected() && !this.emailError() && !this.smsError(),
  );

  constructor() {
    // Copy the stored trading settings into the form whenever they (re)load, unless being edited
    effect(() => {
      const settings = this.settingsStore.settings();
      if (untracked(() => this.tradingDirty())) {
        return;
      }
      this.budgetText.set(settings.orderBudget != null ? String(settings.orderBudget) : '');
      this.quantityText.set(settings.defaultQuantity != null ? String(settings.defaultQuantity) : '');
      this.marketChoice.set(settings.market);
    });

    // Deep link: /settings?section=trading scrolls to that card once rendered
    afterNextRender(() => {
      const wanted = this.route?.snapshot?.queryParamMap?.get('section') as SectionId | null;
      if (wanted && this.sections.some((section) => section.id === wanted)) {
        this.scrollTo(wanted);
      }
    });

    // Copy the stored preferences into the form whenever they (re)load
    effect(() => {
      const prefs = this.store.preferences();
      if (prefs) {
        this.selected.set(new Set(prefs.alertChannels.map((entry) => entry.channel)));
        const contactOf = (channel: ContactChannel) =>
          prefs.alertChannels.find((entry) => entry.channel === channel)?.contactDetail ?? '';
        this.contacts.set({ EMAIL: contactOf(AlertChannel.Email), SMS: contactOf(AlertChannel.Sms) });
      }
    });
  }

  ngOnInit(): void {
    this.store.load();
    if (!this.isAdmin) {
      this.settingsStore.ensureLoaded();
      this.loadAccountCurrency();
    }
  }

  isSelected(channel: AlertChannel): boolean {
    return this.selected().has(channel);
  }

  toggle(channel: AlertChannel): void {
    const next = new Set(this.selected());
    if (next.has(channel)) {
      next.delete(channel);
    } else {
      next.add(channel);
    }
    this.selected.set(next);
    this.feedback.set(null);
  }

  setContact(channel: ContactChannel, value: string): void {
    this.contacts.update((current) => ({ ...current, [channel]: value }));
  }

  save(): void {
    if (!this.canSave()) {
      return;
    }
    const entries: AlertChannelEntry[] = CHANNEL_ORDER.filter((channel) => this.selected().has(channel)).map(
      (channel) => ({
        channel,
        contactDetail: channel === AlertChannel.InApp ? null : this.contacts()[channel as ContactChannel],
      }),
    );
    this.saving.set(true);
    this.feedback.set(null);
    this.store
      .save(entries)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.feedback.set({ text: 'Your preferences have been saved.', error: false });
        },
        error: (err: Error) => {
          this.saving.set(false);
          this.feedback.set({ text: err.message, error: true });
        },
      });
  }

  resetToDefault(): void {
    this.saving.set(true);
    this.feedback.set(null);
    this.store
      .resetToDefault()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.feedback.set({ text: 'Preferences reset. You will be notified in-app.', error: false });
        },
        error: (err: Error) => {
          this.saving.set(false);
          this.feedback.set({ text: err.message, error: true });
        },
      });
  }

  /** Glides the cards column to a section; the page title and section links don't move */
  scrollTo(id: SectionId): void {
    this.activeSection.set(id);
    const column = this.column()?.nativeElement;
    const target = typeof document !== 'undefined' ? document.getElementById('section-' + id) : null;
    if (!column || !target) {
      return;
    }
    // While gliding, the scroll position shouldn't re-pick the section just clicked
    this.jumpingTo = id;
    clearTimeout(this.jumpTimer);
    this.jumpTimer = setTimeout(() => (this.jumpingTo = null), 900);
    const top = target.getBoundingClientRect().top - column.getBoundingClientRect().top + column.scrollTop - 4;
    if (typeof column.scrollTo === 'function') {
      column.scrollTo({ top, behavior: 'smooth' });
    } else {
      column.scrollTop = top;
    }
  }

  /** Highlights the section being read as the cards scroll */
  onColumnScroll(): void {
    const column = this.column()?.nativeElement;
    if (!column || this.jumpingTo) {
      return;
    }
    const atBottom = column.scrollTop + column.clientHeight >= column.scrollHeight - 2;
    const columnTop = column.getBoundingClientRect().top;
    let current = this.sections[0].id;
    for (const section of this.sections) {
      const el = document.getElementById('section-' + section.id);
      if (el && el.getBoundingClientRect().top - columnTop <= 80) {
        current = section.id;
      }
    }
    this.activeSection.set(atBottom ? this.sections[this.sections.length - 1].id : current);
  }

  setBudget(value: string): void {
    this.budgetText.set(value);
    this.tradingDirty.set(true);
    this.tradingFeedback.set(null);
  }

  clearBudget(): void {
    this.setBudget('');
  }

  setQuantity(value: string): void {
    this.quantityText.set(value);
    this.tradingDirty.set(true);
    this.tradingFeedback.set(null);
  }

  chooseMarket(market: Market): void {
    this.marketChoice.set(market);
    this.tradingDirty.set(true);
    this.tradingFeedback.set(null);
  }

  saveTrading(): void {
    if (!this.canSaveTrading()) {
      return;
    }
    const budget = this.budgetText().trim();
    const quantity = this.quantityText().trim();
    this.tradingSaving.set(true);
    this.tradingFeedback.set(null);
    this.settingsStore
      .save({
        // The server rounds to cents (decimal HALF_UP); rounding a binary number here could differ
        orderBudget: budget ? Number(budget) : null,
        market: this.marketChoice(),
        defaultQuantity: quantity ? Number(quantity) : null,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.tradingSaving.set(false);
          this.tradingDirty.set(false);
          this.tradingFeedback.set({ text: 'Your trading settings have been saved.', error: false });
        },
        error: (err: Error) => {
          this.tradingSaving.set(false);
          this.tradingFeedback.set({ text: err.message, error: true });
        },
      });
  }

  resetTrading(): void {
    this.tradingSaving.set(true);
    this.tradingFeedback.set(null);
    this.settingsStore
      .reset()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.tradingSaving.set(false);
          this.tradingDirty.set(false);
          this.budgetText.set('');
          this.quantityText.set('');
          this.marketChoice.set('ALL');
          this.tradingFeedback.set({ text: 'Trading settings reset: no budget, all markets, no default quantity.', error: false });
        },
        error: (err: Error) => {
          this.tradingSaving.set(false);
          this.tradingFeedback.set({ text: err.message, error: true });
        },
      });
  }

  /** The account currency comes from the balance (the account record does not carry one) */
  private loadAccountCurrency(): void {
    const accountId = this.authService.currentUser?.()?.accountId;
    if (accountId == null) {
      return;
    }
    try {
      this.injector
        .get(AccountsService)
        .getBalance(accountId)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({ next: (balance) => balance.currency && this.accountCurrency.set(balance.currency), error: () => undefined });
    } catch {
      // No HTTP client (e.g. in isolated tests): keep USD
    }
  }
}
