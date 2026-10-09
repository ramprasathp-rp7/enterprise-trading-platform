import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { ThemeService } from './theme.service';

describe('ThemeService', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.removeAttribute('data-theme');
    TestBed.resetTestingModule();
  });

  afterEach(() => {
    localStorage.clear();
    document.documentElement.removeAttribute('data-theme');
  });

  it('restores a saved theme and applies it to <html>', () => {
    localStorage.setItem(ThemeService.STORAGE_KEY, 'dark');

    const service = TestBed.inject(ThemeService);

    expect(service.isDark()).toBe(true);
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
  });

  it('toggles between light and dark and remembers the choice', () => {
    localStorage.setItem(ThemeService.STORAGE_KEY, 'light');
    const service = TestBed.inject(ThemeService);

    service.toggle();
    expect(service.theme()).toBe('dark');
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(localStorage.getItem(ThemeService.STORAGE_KEY)).toBe('dark');

    service.toggle();
    expect(service.theme()).toBe('light');
    expect(localStorage.getItem(ThemeService.STORAGE_KEY)).toBe('light');
  });

  it('ignores an invalid saved value', () => {
    localStorage.setItem(ThemeService.STORAGE_KEY, 'purple');

    const service = TestBed.inject(ThemeService);

    expect(['light', 'dark']).toContain(service.theme());
  });

  it('follows the operating system in system mode and remembers the mode', () => {
    const service = TestBed.inject(ThemeService);

    service.setMode('system');

    const osDark = window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false;
    expect(service.mode()).toBe('system');
    expect(service.theme()).toBe(osDark ? 'dark' : 'light');
    expect(localStorage.getItem(ThemeService.STORAGE_KEY)).toBe('system');
  });

  it('starts in system mode when nothing is saved, and restores a saved system mode', () => {
    expect(TestBed.inject(ThemeService).mode()).toBe('system');

    localStorage.setItem(ThemeService.STORAGE_KEY, 'system');
    TestBed.resetTestingModule();
    expect(TestBed.inject(ThemeService).mode()).toBe('system');
  });

  it('toggling from system picks an explicit theme', () => {
    localStorage.setItem(ThemeService.STORAGE_KEY, 'system');
    const service = TestBed.inject(ThemeService);
    const before = service.theme();

    service.toggle();

    expect(service.mode()).toBe(before === 'dark' ? 'light' : 'dark');
  });
});
