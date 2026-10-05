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
});
