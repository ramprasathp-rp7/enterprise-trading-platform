import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { PositionsComponent } from './positions.component';
import { AccountsService, PositionResponse } from '../../../../api/generated/trade';
import { CoreAuthService } from '../../../../core/auth/core-auth.service';

describe('PositionsComponent', () => {
  let fixture: ComponentFixture<PositionsComponent>;
  let component: PositionsComponent;
  let accounts: { getPositions: ReturnType<typeof vi.fn> };
  let currentUser: ReturnType<typeof vi.fn>;

  const positions: PositionResponse[] = [
    { symbol: 'AAPL', quantity: 100, averageCost: 182.4 } as PositionResponse,
    { symbol: 'MSFT', quantity: 20, averageCost: 351.1 } as PositionResponse,
  ];

  async function create(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [PositionsComponent],
      providers: [
        provideRouter([]),
        { provide: AccountsService, useValue: accounts },
        { provide: CoreAuthService, useValue: { currentUser } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(PositionsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(() => {
    accounts = { getPositions: vi.fn(() => of(positions)) };
    currentUser = vi.fn(() => ({ accountId: 7 }));
  });

  it('loads positions and renders a row per holding with cost basis', async () => {
    await create();

    expect(accounts.getPositions).toHaveBeenCalledWith(7);
    const rows = fixture.nativeElement.querySelectorAll('tbody tr.transaction-row');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('AAPL');
    expect(rows[0].textContent).toContain('18,240.00');
    expect(component.loading()).toBe(false);
  });

  it('derives total units and total cost basis', async () => {
    await create();

    expect(component.totalUnits()).toBe(120);
    expect(component.totalCostBasis()).toBeCloseTo(18240 + 7022);
    const tiles = fixture.nativeElement.querySelectorAll('.stat-value');
    expect(tiles[0].textContent.trim()).toBe('2');
    expect(tiles[1].textContent.trim()).toBe('120');
  });

  it('shows the empty state when there are no positions', async () => {
    accounts.getPositions.mockReturnValue(of(null));
    await create();

    expect(component.positions()).toEqual([]);
    expect(fixture.nativeElement.querySelector('.empty-state').textContent).toContain('No active positions');
  });

  it('shows an error without calling the API when there is no account', async () => {
    currentUser.mockReturnValue(null);
    await create();

    expect(accounts.getPositions).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('.alert-error').textContent).toContain('No account found');
  });

  it('shows an error when the request fails', async () => {
    accounts.getPositions.mockReturnValue(throwError(() => new Error('down')));
    await create();

    expect(component.error()).toBe('Could not fetch current positions.');
    expect(component.loading()).toBe(false);
  });

  it('formats numbers with the requested precision', async () => {
    await create();

    expect(component.formatNumber(1234.567, 2)).toBe('1,234.57');
    expect(component.formatNumber(1234.567, 0)).toBe('1,235');
  });
});
