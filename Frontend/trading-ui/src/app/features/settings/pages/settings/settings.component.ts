import { Component, DestroyRef, OnInit, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AlertChannel, AlertChannelEntry } from '../../../../api/generated/preferences';
import { PreferencesStore } from '../../preferences.store';

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
 * Preferences page: choose one or more channels the platform reaches you on about trades and alerts.
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

  readonly canSave = computed(
    () => !this.saving() && !this.nothingSelected() && !this.emailError() && !this.smsError(),
  );

  constructor() {
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
}
