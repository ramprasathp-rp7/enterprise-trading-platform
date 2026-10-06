import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { SettingsComponent } from './settings.component';
import { PreferencesStore } from '../../preferences.store';
import { AlertChannelEntry, PreferencesResponse } from '../../../../api/generated/preferences';

describe('SettingsComponent', () => {
  let fixture: ComponentFixture<SettingsComponent>;
  let component: SettingsComponent;
  let preferences: ReturnType<typeof signal<PreferencesResponse | null>>;
  let store: Record<string, unknown>;

  const el = (testId: string): HTMLElement | null =>
    fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);

  beforeEach(() => {
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
    TestBed.configureTestingModule({
      imports: [SettingsComponent],
      providers: [{ provide: PreferencesStore, useValue: store }],
    });
    fixture = TestBed.createComponent(SettingsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
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
});
