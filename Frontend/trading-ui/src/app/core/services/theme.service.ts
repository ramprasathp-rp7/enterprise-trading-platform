import { Injectable, computed, inject, signal } from '@angular/core';
import { DOCUMENT } from '@angular/common';

export type Theme = 'light' | 'dark';

/**
 * Theme service
 *
 * Applies the colour theme as `<html data-theme="light|dark">`; every colour in
 * styles.css is a token that switches on that attribute.
 * The choice is remembered per browser; with no saved choice the OS preference wins.
 * index.html applies the saved theme before Angular boots to avoid a flash.
 */
@Injectable({
  providedIn: 'root',
})
export class ThemeService {
  static readonly STORAGE_KEY = 'etp-theme';

  private readonly document = inject(DOCUMENT);
  private readonly themeSignal = signal<Theme>(this.resolveInitialTheme());

  readonly theme = computed(() => this.themeSignal());
  readonly isDark = computed(() => this.themeSignal() === 'dark');

  constructor() {
    this.apply(this.themeSignal(), false);
  }

  toggle(): void {
    this.setTheme(this.isDark() ? 'light' : 'dark');
  }

  setTheme(theme: Theme): void {
    this.themeSignal.set(theme);
    this.apply(theme, true);
    try {
      localStorage.setItem(ThemeService.STORAGE_KEY, theme);
    } catch {
      // Storage unavailable (private mode, blocked) — theme still applies for this session.
    }
  }

  private resolveInitialTheme(): Theme {
    try {
      const saved = localStorage.getItem(ThemeService.STORAGE_KEY);
      if (saved === 'light' || saved === 'dark') {
        return saved;
      }
    } catch {
      // Fall through to the OS preference.
    }
    const prefersDark = this.document.defaultView?.matchMedia?.('(prefers-color-scheme: dark)').matches;
    return prefersDark ? 'dark' : 'light';
  }

  private apply(theme: Theme, animate: boolean): void {
    const root = this.document.documentElement;
    if (animate) {
      root.classList.add('theme-transition');
      this.document.defaultView?.setTimeout(() => root.classList.remove('theme-transition'), 250);
    }
    root.setAttribute('data-theme', theme);
  }
}
