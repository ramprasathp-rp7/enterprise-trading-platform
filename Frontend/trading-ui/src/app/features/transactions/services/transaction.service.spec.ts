import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideApi as provideTransactionsApi } from '../../../api/generated/transactions/provide-api';
import { TransactionService } from './transaction.service';

describe('TransactionService', () => {
  const base = 'http://localhost:8081/api/v1/accounts';
  let service: TransactionService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTransactionsApi('http://localhost:8081'),
      ],
    });
    service = TestBed.inject(TransactionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('POSTs a deposit with the amount in the body', () => {
    service.deposit(7, 250).subscribe();

    const req = http.expectOne(`${base}/7/deposits`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ amount: 250 });
    req.flush({});
  });

  it('POSTs a withdrawal with the amount in the body', () => {
    service.withdraw(7, 99.5).subscribe();

    const req = http.expectOne(`${base}/7/withdrawals`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ amount: 99.5 });
    req.flush({});
  });

  it('GETs a single transaction by id', () => {
    service.getTransaction(7, 'tx-1').subscribe();

    const req = http.expectOne(`${base}/7/transactions/tx-1`);
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('GETs history without query params when no filter is given', () => {
    service.getTransactionHistory(7).subscribe();

    const req = http.expectOne((r) => r.url === `${base}/7/transactions`);
    expect(req.request.params.keys()).toEqual([]);
    req.flush([]);
  });

  it('passes type, from and to filters as query params', () => {
    service
      .getTransactionHistory(7, { type: 'DEPOSIT', from: '2026-01-01T00:00:00Z', to: '2026-12-31T23:59:59Z' })
      .subscribe();

    const req = http.expectOne((r) => r.url === `${base}/7/transactions`);
    expect(req.request.params.get('type')).toBe('DEPOSIT');
    // The generated client percent-encodes values once before handing them to HttpParams
    expect(decodeURIComponent(req.request.params.get('from')!)).toBe('2026-01-01T00:00:00Z');
    expect(decodeURIComponent(req.request.params.get('to')!)).toBe('2026-12-31T23:59:59Z');
    req.flush([]);
  });
});
