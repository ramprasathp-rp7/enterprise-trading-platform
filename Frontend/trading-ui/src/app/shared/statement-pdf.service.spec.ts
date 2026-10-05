import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { StatementPdfService, StatementDefinition, fileDate } from './statement-pdf.service';

describe('StatementPdfService', () => {
  let service: StatementPdfService;

  const statement = (overrides: Partial<StatementDefinition> = {}): StatementDefinition => ({
    title: 'Holdings Statement',
    details: [
      ['Account', '1001'],
      ['Prepared for', 'rkumar'],
    ],
    summary: [
      ['Open positions', '2'],
      ['Total cost basis', '25,262.00'],
    ],
    columns: [{ header: 'Symbol' }, { header: 'Quantity', numeric: true }],
    rows: [
      ['AAPL', '100'],
      ['MSFT', '20'],
    ],
    footerRow: ['Total', '120'],
    emptyMessage: 'There are no open positions on this account.',
    fileName: 'holdings-statement-1001-2026-10-05',
    generatedAt: new Date('2026-10-05T10:00:00Z'),
    ...overrides,
  });

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(StatementPdfService);
  });

  it('renders the title, details, summary, rows and footer into the PDF', async () => {
    const doc = await service.build(statement());
    const pdf = doc.output();

    expect(pdf.startsWith('%PDF')).toBe(true);
    for (const text of ['Holdings Statement', 'ENTERPRISE TRADING PLATFORM', 'rkumar', 'TOTAL COST BASIS', '25,262.00', 'AAPL', 'MSFT', 'Total', 'Page 1 of 1']) {
      expect(pdf).toContain(text);
    }
  });

  it('paginates long statements and numbers every page', async () => {
    const rows = Array.from({ length: 120 }, (_, i) => [`SYM${i}`, String(i)]);

    const doc = await service.build(statement({ rows }));

    const pages = doc.getNumberOfPages();
    expect(pages).toBeGreaterThan(1);
    expect(doc.output()).toContain(`Page ${pages} of ${pages}`);
  });

  it('shows the empty message instead of a table when there are no rows', async () => {
    const doc = await service.build(statement({ rows: [], footerRow: undefined }));
    const pdf = doc.output();

    expect(pdf).toContain('There are no open positions on this account.');
    expect(pdf).not.toContain('MSFT');
  });

  it('downloads the built document with a .pdf file name', async () => {
    const save = vi.fn();
    vi.spyOn(service, 'build').mockResolvedValue({ save } as any);

    await service.download(statement());

    expect(save).toHaveBeenCalledWith('holdings-statement-1001-2026-10-05.pdf');
  });

  it('formats file dates as yyyy-mm-dd', () => {
    expect(fileDate(new Date(2026, 0, 5))).toBe('2026-01-05');
  });
});
