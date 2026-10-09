import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { DOCUMENT } from '@angular/common';

export type Theme = 'light' | 'dark';
/** What the user chose: a fixed theme, or follow the operating system */
export type ThemeMode = Theme | 'system';

/**
 * Theme service
 *
 * Applies the colour theme as `<html data-theme="light|dark">`; every colour in
 * styles.css is a token that switches on that attribute.
 * The choice (light, dark or system) is remembered per browser; "system" follows the OS
 * preference and changes with it. index.html applies the saved theme before Angular boots
 * to avoid a flash.
 */
@Injectable({
  providedIn: 'root',
})
export class ThemeService {
  static readonly STORAGE_KEY = 'etp-theme';

  private readonly document = inject(DOCUMENT);
  private readonly media = this.document.defaultView?.matchMedia?.('(prefers-color-scheme: dark)') ?? null;
  private readonly systemDark = signal(!!this.media?.matches);
  private readonly modeSignal = signal<ThemeMode>(this.resolveInitialMode());

  /** The user's choice */
  readonly mode = computed(() => this.modeSignal());
  /** The theme actually applied */
  readonly theme = computed<Theme>(() => {
    const mode = this.modeSignal();
    return mode === 'system' ? (this.systemDark() ? 'dark' : 'light') : mode;
  });
  readonly isDark = computed(() => this.theme() === 'dark');

  constructor() {
    this.apply(this.theme(), false);
    const onChange = (event: MediaQueryListEvent) => {
      this.systemDark.set(event.matches);
      if (this.modeSignal() === 'system') {
        this.apply(this.theme(), true);
      }
    };
    this.media?.addEventListener?.('change', onChange);
    inject(DestroyRef, { optional: true })?.onDestroy(() => this.media?.removeEventListener?.('change', onChange));
  }

  /** Flips between light and dark (leaving "system" for an explicit choice) */
  toggle(): void {
    this.setTheme(this.isDark() ? 'light' : 'dark');
  }

  setTheme(theme: Theme): void {
    this.setMode(theme);
  }

  setMode(mode: ThemeMode): void {
    this.modeSignal.set(mode);
    this.apply(this.theme(), true);
    try {
      localStorage.setItem(ThemeService.STORAGE_KEY, mode);
    } catch {
      // Storage unavailable (private mode, blocked) — theme still applies for this session.
    }
  }

  private resolveInitialMode(): ThemeMode {
    try {
      const saved = localStorage.getItem(ThemeService.STORAGE_KEY);
      if (saved === 'light' || saved === 'dark' || saved === 'system') {
        return saved;
      }
    } catch {
      // Fall through to following the OS.
    }
    return 'system';
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
