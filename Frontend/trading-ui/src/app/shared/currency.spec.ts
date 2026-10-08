import { describe, it, expect } from 'vitest';
import { currencySymbol, formatMoney, toAccountCurrency } from './currency';

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

describe('formatMoney', () => {
  it('prefixes the currency symbol', () => {
    expect(formatMoney(1234.5, 'USD')).toBe('$1,234.50');
    expect(formatMoney(1006.35, 'INR')).toBe('₹1,006.35');
  });

  it('puts the sign before the symbol and can force a plus', () => {
    expect(formatMoney(-12, 'USD')).toBe('-$12.00');
    expect(formatMoney(12, 'USD', { signed: true })).toBe('+$12.00');
    expect(formatMoney(0, 'USD', { signed: true })).toBe('$0.00');
  });

  it('honours the precision, e.g. 4 decimals for FX', () => {
    expect(formatMoney(0.925, 'USD', { decimals: 4 })).toBe('$0.9250');
  });

  it('falls back to a code suffix for codes with no symbol, and a dash for no amount', () => {
    expect(formatMoney(1234.5, 'CHF')).toBe('1,234.50 CHF');
    expect(formatMoney(null, 'USD')).toBe('—');
    expect(formatMoney(undefined, 'USD')).toBe('—');
  });
});

describe('toAccountCurrency', () => {
  it('multiplies by the instrument-to-account rate', () => {
    expect(toAccountCurrency(64000, 0.0125)).toBe(800);
  });

  it('is null when the amount or the rate is unknown', () => {
    expect(toAccountCurrency(null, 0.0125)).toBeNull();
    expect(toAccountCurrency(100, null)).toBeNull();
  });
});
