import { Injectable } from '@angular/core';
import type { jsPDF } from 'jspdf';

/** A column in the statement table */
export interface StatementColumn {
  header: string;
  /** Right-align numeric columns */
  numeric?: boolean;
}

/** Everything needed to render one downloadable PDF statement */
export interface StatementDefinition {
  /** e.g. "Holdings Statement" */
  title: string;
  /** Label/value pairs shown under the title, e.g. Account, Prepared for */
  details: [label: string, value: string][];
  /** Headline figures shown above the table, e.g. Total cost basis */
  summary: [label: string, value: string][];
  columns: StatementColumn[];
  rows: string[][];
  /** Optional closing row (e.g. totals), rendered in bold */
  footerRow?: string[];
  /** Shown instead of the table when there are no rows */
  emptyMessage: string;
  /** Download file name, without extension */
  fileName: string;
  generatedAt?: Date;
}

const BRAND: [number, number, number] = [37, 99, 235];
const INK: [number, number, number] = [11, 19, 36];
const MUTED: [number, number, number] = [100, 116, 139];
const LINE: [number, number, number] = [226, 232, 240];
const MARGIN = 40;

/**
 * Builds and downloads PDF statements (transactions, holdings).
 * jsPDF is loaded on demand so it stays out of the initial bundle.
 */
@Injectable({ providedIn: 'root' })
export class StatementPdfService {
  /** Build the statement and trigger a browser download. */
  async download(statement: StatementDefinition): Promise<void> {
    const doc = await this.build(statement);
    doc.save(`${statement.fileName}.pdf`);
  }

  /** Build the statement document (exposed for testing). */
  async build(statement: StatementDefinition): Promise<jsPDF> {
    const [{ jsPDF }, { autoTable }] = await Promise.all([import('jspdf'), import('jspdf-autotable')]);
    const doc = new jsPDF({ unit: 'pt', format: 'a4' });
    const pageWidth = doc.internal.pageSize.getWidth();
    const generatedAt = statement.generatedAt ?? new Date();

    // Brand bar + title
    doc.setFillColor(...BRAND);
    doc.rect(0, 0, pageWidth, 6, 'F');
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(10);
    doc.setTextColor(...BRAND);
    doc.text('ENTERPRISE TRADING PLATFORM', MARGIN, 44);
    doc.setFontSize(20);
    doc.setTextColor(...INK);
    doc.text(statement.title, MARGIN, 70);

    // Details (label: value), two per line
    let y = 96;
    doc.setFontSize(9.5);
    const details: [string, string][] = [...statement.details, ['Generated', formatDateTime(generatedAt)]];
    const colWidth = (pageWidth - MARGIN * 2) / 2;
    details.forEach(([label, value], i) => {
      const x = MARGIN + (i % 2) * colWidth;
      if (i > 0 && i % 2 === 0) {
        y += 16;
      }
      doc.setFont('helvetica', 'normal');
      doc.setTextColor(...MUTED);
      doc.text(`${label}:`, x, y);
      doc.setFont('helvetica', 'bold');
      doc.setTextColor(...INK);
      doc.text(value, x + doc.getTextWidth(`${label}: `), y);
    });

    // Summary figures
    y += 22;
    if (statement.summary.length) {
      const boxWidth = (pageWidth - MARGIN * 2) / statement.summary.length;
      doc.setDrawColor(...LINE);
      doc.setFillColor(248, 250, 252);
      doc.roundedRect(MARGIN, y, pageWidth - MARGIN * 2, 50, 6, 6, 'FD');
      statement.summary.forEach(([label, value], i) => {
        const x = MARGIN + 14 + i * boxWidth;
        doc.setFont('helvetica', 'normal');
        doc.setFontSize(8.5);
        doc.setTextColor(...MUTED);
        doc.text(label.toUpperCase(), x, y + 19);
        doc.setFont('helvetica', 'bold');
        doc.setFontSize(13);
        doc.setTextColor(...INK);
        doc.text(value, x, y + 37);
      });
      y += 50;
    }

    // Table
    const columnStyles: Record<number, { halign: 'right' }> = {};
    statement.columns.forEach((column, i) => {
      if (column.numeric) {
        columnStyles[i] = { halign: 'right' };
      }
    });

    if (statement.rows.length) {
      autoTable(doc, {
        startY: y + 18,
        margin: { left: MARGIN, right: MARGIN, bottom: 50 },
        head: [statement.columns.map((c) => c.header)],
        body: statement.rows,
        foot: statement.footerRow ? [statement.footerRow] : undefined,
        showFoot: 'lastPage',
        theme: 'grid',
        styles: { font: 'helvetica', fontSize: 9, cellPadding: 6, textColor: INK, lineColor: LINE, lineWidth: 0.5 },
        headStyles: { fillColor: BRAND, textColor: [255, 255, 255], fontStyle: 'bold' },
        footStyles: { fillColor: [241, 245, 249], textColor: INK, fontStyle: 'bold' },
        alternateRowStyles: { fillColor: [248, 250, 252] },
        columnStyles,
        didParseCell: (data) => {
          if ((data.section === 'head' || data.section === 'foot') && statement.columns[data.column.index]?.numeric) {
            data.cell.styles.halign = 'right';
          }
        },
      });
    } else {
      doc.setFont('helvetica', 'normal');
      doc.setFontSize(11);
      doc.setTextColor(...MUTED);
      doc.text(statement.emptyMessage, MARGIN, y + 40);
    }

    // Footer on every page
    const pages = doc.getNumberOfPages();
    const pageHeight = doc.internal.pageSize.getHeight();
    for (let page = 1; page <= pages; page++) {
      doc.setPage(page);
      doc.setFont('helvetica', 'normal');
      doc.setFontSize(8);
      doc.setTextColor(...MUTED);
      doc.text(`${statement.title} · Enterprise Trading Platform`, MARGIN, pageHeight - 24);
      doc.text(`Page ${page} of ${pages}`, pageWidth - MARGIN, pageHeight - 24, { align: 'right' });
    }

    return doc;
  }
}

function formatDateTime(date: Date): string {
  return date.toLocaleString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

/** yyyy-mm-dd, for file names */
export function fileDate(date: Date = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}
