const symbols = new Map<string, string>();

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
