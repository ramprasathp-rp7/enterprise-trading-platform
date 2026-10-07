import { describe, it, expect } from 'vitest';
import { currencySymbol } from './currency';

describe('currencySymbol', () => {
  it('returns the short symbol for common codes', () => {
    expect(currencySymbol('INR')).toBe('₹');
    expect(currencySymbol('USD')).toBe('$');
    expect(currencySymbol('JPY')).toBe('¥');
    expect(currencySymbol('EUR')).toBe('€');
    expect(currencySymbol('GBP')).toBe('£');
  });

  it('ignores case and spaces', () => {
    expect(currencySymbol(' inr ')).toBe('₹');
  });

  it('returns an empty string for empty, unknown or symbol-less codes', () => {
    expect(currencySymbol('')).toBe('');
    expect(currencySymbol(null)).toBe('');
    expect(currencySymbol(undefined)).toBe('');
    expect(currencySymbol('NOT-A-CODE')).toBe('');
    expect(currencySymbol('CHF')).toBe('');
  });
});
