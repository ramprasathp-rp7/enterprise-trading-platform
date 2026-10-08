const symbols = new Map<string, string>();

/**
 * Currency of every account's cash, fees and transactions, for views that span accounts (admin
 * analytics, the fee schedule) and so cannot read one account's balance. All accounts are USD and
 * the fee schedule is in USD (decision log 0008); per-account views use the account's own currency.
 */
export const PLATFORM_ACCOUNT_CURRENCY = 'USD';

/**
 * Short symbol for an ISO 4217 currency code, e.g. INR → ₹, USD → $, JPY → ¥.
 * Returns '' when the code is empty or has no symbol distinct from the code itself.
 */
export function currencySymbol(code: string | null | undefined): string {
  const currency = (code ?? '').trim().toUpperCase();
  if (!currency) {
    return '';
  }
  let symbol = symbols.get(currency);
  if (symbol === undefined) {
    try {
      symbol =
        new Intl.NumberFormat('en-US', { style: 'currency', currency, currencyDisplay: 'narrowSymbol' })
          .formatToParts(0)
          .find((part) => part.type === 'currency')?.value ?? '';
    } catch {
      symbol = ''; // not a valid ISO 4217 code
    }
    if (symbol === currency) {
      symbol = '';
    }
    symbols.set(currency, symbol);
  }
  return symbol;
}

export interface MoneyFormatOptions {
  /** Fraction digits (default 2; FX prices use 4) */
  decimals?: number;
  /** Show up to this many fraction digits when the amount has them (default: decimals) */
  maxDecimals?: number;
  /** Prefix positive amounts with +, for P&L figures */
  signed?: boolean;
}

/**
 * An amount with its currency, e.g. $1,234.50, ₹1,006.35, -$12.00, or 1,234.50 CHF for a code
 * with no symbol of its own. Returns '—' for a missing amount. Every money figure in the app goes
 * through here so a currency is never implied: account money (cash, fees, P&L, totals) is shown in
 * the account's currency and instrument prices in the instrument's.
 */
export function formatMoney(
  amount: number | null | undefined,
  code: string | null | undefined,
  options: MoneyFormatOptions = {}
): string {
  if (amount === null || amount === undefined || Number.isNaN(amount)) {
    return '—';
  }
  const decimals = options.decimals ?? 2;
  const digits = new Intl.NumberFormat('en-US', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: Math.max(decimals, options.maxDecimals ?? decimals),
  }).format(Math.abs(amount));
  const sign = amount < 0 ? '-' : options.signed && amount > 0 ? '+' : '';
  const symbol = currencySymbol(code);
  if (symbol) {
    return `${sign}${symbol}${digits}`;
  }
  const suffix = (code ?? '').trim().toUpperCase();
  return suffix ? `${sign}${digits} ${suffix}` : `${sign}${digits}`;
}

/**
 * Converts an instrument-currency amount into the account's currency at {@code fxRate} (account
 * units per instrument unit, as the portfolio and trade APIs return it). Null when either is unknown.
 */
export function toAccountCurrency(amount: number | null | undefined, fxRate: number | null | undefined): number | null {
  if (amount === null || amount === undefined || fxRate === null || fxRate === undefined) {
    return null;
  }
  return amount * fxRate;
}
