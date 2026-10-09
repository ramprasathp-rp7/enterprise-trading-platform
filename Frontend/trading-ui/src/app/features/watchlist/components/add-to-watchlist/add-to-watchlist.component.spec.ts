import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AddToWatchlistComponent } from './add-to-watchlist.component';
import { WatchlistStore } from '../../watchlist.store';

interface List { watchlistId: number; name: string; items: { symbol: string }[] }

describe('AddToWatchlistComponent', () => {
  let fixture: ComponentFixture<AddToWatchlistComponent>;
  let component: AddToWatchlistComponent;
  const watchlists = signal<List[]>([]);
  const loading = signal(false);
  const loaded = signal(true);
  let store: Record<string, unknown> & {
    ensureLoaded: ReturnType<typeof vi.fn>;
    addSymbol: ReturnType<typeof vi.fn>;
    removeSymbol: ReturnType<typeof vi.fn>;
    create: ReturnType<typeof vi.fn>;
  };

  const q = (selector: string) => fixture.nativeElement.querySelector(selector) as HTMLElement | null;
  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  beforeEach(async () => {
    watchlists.set([
      { watchlistId: 1, name: 'Tech', items: [{ symbol: 'AAPL' }] },
      { watchlistId: 2, name: 'India', items: [] },
    ]);
    loading.set(false);
    loaded.set(true);
    store = {
      watchlists,
      isLoading: loading,
      isLoaded: loaded,
      ensureLoaded: vi.fn(),
      listsContaining: (symbol: string) => watchlists().filter((w) => w.items.some((i) => i.symbol === symbol)),
      find: (id: number) => watchlists().find((w) => w.watchlistId === id),
      addSymbol: vi.fn(() => of({})),
      removeSymbol: vi.fn(() => of(undefined)),
      create: vi.fn((name: string) => of({ watchlistId: 3, name, items: [] })),
    };

    await TestBed.configureTestingModule({
      imports: [AddToWatchlistComponent],
      providers: [provideRouter([{ path: '**', children: [] }]), { provide: WatchlistStore, useValue: store }],
    }).compileComponents();

    fixture = TestBed.createComponent(AddToWatchlistComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('symbol', 'AAPL');
    fixture.detectChanges();
  });

  const open = () => {
    q('[data-testid="add-to-watchlist"]')!.click();
    fixture.detectChanges();
  };

  it('loads the watchlists and shows that the symbol is already watched', () => {
    expect(store.ensureLoaded).toHaveBeenCalled();
    expect(q('[data-testid="add-to-watchlist"]')!.textContent).toContain('Watching');
    expect(q('[data-testid="add-to-watchlist"]')!.getAttribute('title')).toBe('In 1 watchlist(s)');
  });

  it('lists the watchlists with ticks for the ones holding the symbol', () => {
    open();

    expect(q('[data-testid="watchlist-menu"]')).not.toBeNull();
    expect((q('[data-testid="watchlist-option-1"]') as HTMLInputElement).checked).toBe(true);
    expect((q('[data-testid="watchlist-option-2"]') as HTMLInputElement).checked).toBe(false);
  });

  it('adds to and removes from a list, saying which', () => {
    component.toggleList(2);
    expect(store.addSymbol).toHaveBeenCalledWith(2, 'AAPL');
    expect(component.message()).toEqual({ text: 'Added to India', error: false });

    component.toggleList(1);
    expect(store.removeSymbol).toHaveBeenCalledWith(1, 'AAPL');
    expect(component.message()).toEqual({ text: 'Removed from Tech', error: false });
    expect(component.busyId()).toBeNull();
  });

  it('reports a failed change', () => {
    store.addSymbol.mockReturnValue(throwError(() => new Error('List is full')));
    open();

    component.toggleList(2);
    fixture.detectChanges();

    expect(text()).toContain('List is full');
  });

  it('creates a list and adds the symbol to it', () => {
    component.newName = '  Growth  ';
    component.createAndAdd();

    expect(store.create).toHaveBeenCalledWith('Growth');
    expect(store.addSymbol).toHaveBeenCalledWith(3, 'AAPL');
    expect(component.newName).toBe('');
  });

  it('ignores a blank name and reports a failed create', () => {
    component.newName = '   ';
    component.createAndAdd();
    expect(store.create).not.toHaveBeenCalled();

    store.create.mockReturnValue(throwError(() => new Error('Name already used')));
    component.newName = 'Tech';
    component.createAndAdd();
    expect(component.message()).toEqual({ text: 'Name already used', error: true });
    expect(component.busyId()).toBeNull();
  });

  it('says when there are no lists yet, or while they load', () => {
    watchlists.set([]);
    open();
    expect(text()).toContain('You have no watchlists yet');

    loading.set(true);
    loaded.set(false);
    fixture.detectChanges();
    expect(text()).toContain('Loading watchlists');
    expect(q('[data-testid="add-to-watchlist"]')!.textContent).toContain('Watchlist');
  });

  it('closes on an outside click, Escape, or the manage link', () => {
    open();
    component.onDocumentClick({ target: fixture.nativeElement.querySelector('.menu') } as unknown as MouseEvent);
    expect(component.open()).toBe(true);

    component.onDocumentClick({ target: document.body } as unknown as MouseEvent);
    expect(component.open()).toBe(false);

    component.toggle();
    component.onEscape();
    expect(component.open()).toBe(false);

    open();
    (q('.menu-link') as HTMLAnchorElement).click();
    expect(component.open()).toBe(false);
  });
});
