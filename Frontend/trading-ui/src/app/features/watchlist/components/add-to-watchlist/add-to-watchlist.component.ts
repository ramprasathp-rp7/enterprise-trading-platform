import { Component, ElementRef, HostListener, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { RouterModule } from '@angular/router';
import { WatchlistStore } from '../../watchlist.store';

/**
 * "Watchlist" button with a menu of the user's watchlists. Ticking a list adds the
 * instrument to it, unticking removes it, and a new list can be created (and the
 * instrument added to it) from the bottom of the menu.
 */
@Component({
  selector: 'app-add-to-watchlist',
  standalone: true,
  imports: [FormsModule, RouterModule],
  template: `
    <button
      type="button"
      class="watch-btn"
      [class.watching]="containing().length > 0"
      (click)="toggle()"
      [attr.aria-expanded]="open()"
      aria-haspopup="menu"
      data-testid="add-to-watchlist"
      [title]="containing().length ? 'In ' + containing().length + ' watchlist(s)' : 'Add to a watchlist'"
    >
      <i class="fa-bookmark" [class.fas]="containing().length > 0" [class.far]="containing().length === 0"></i>
      {{ containing().length ? 'Watching' : 'Watchlist' }}
    </button>

    @if (open()) {
      <div class="menu anim-pop" animate.leave="anim-pop-out" role="menu" data-testid="watchlist-menu">
        <p class="menu-title">Save {{ symbol() }} to…</p>

        @if (store.isLoading() && !store.isLoaded()) {
          <p class="menu-note"><i class="fas fa-spinner fa-spin"></i> Loading watchlists…</p>
        } @else if (store.watchlists().length === 0) {
          <p class="menu-note">You have no watchlists yet. Create one below.</p>
        } @else {
          <ul class="menu-list">
            @for (list of store.watchlists(); track list.watchlistId) {
              <li>
                <label class="menu-item" [class.busy]="busyId() === list.watchlistId">
                  <input
                    type="checkbox"
                    [checked]="isIn(list.watchlistId)"
                    [disabled]="busyId() !== null"
                    (change)="toggleList(list.watchlistId)"
                    [attr.data-testid]="'watchlist-option-' + list.watchlistId"
                  />
                  <span class="item-name">{{ list.name }}</span>
                  <span class="item-count">{{ list.items.length }}</span>
                </label>
              </li>
            }
          </ul>
        }

        <form class="create-row" (ngSubmit)="createAndAdd()">
          <input
            class="form-input create-input"
            name="newWatchlist"
            [(ngModel)]="newName"
            placeholder="New watchlist name"
            maxlength="60"
            [disabled]="busyId() !== null"
            data-testid="new-watchlist-input"
          />
          <button type="submit" class="btn btn-primary btn-sm" [disabled]="!newName.trim() || busyId() !== null">
            <i class="fas fa-plus"></i> Create
          </button>
        </form>

        @if (message(); as m) {
          <p class="menu-msg" [class.error]="m.error" role="status">{{ m.text }}</p>
        }

        <a class="menu-link" routerLink="/watchlists" (click)="open.set(false)">Manage watchlists <i class="fas fa-arrow-right"></i></a>
      </div>
    }
  `,
  styles: [`
    :host {
      position: relative;
      display: inline-block;
    }

    .watch-btn {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      height: 26px;
      padding: 0 11px;
      border: 1px solid var(--line-strong);
      border-radius: var(--r-pill);
      background: var(--surface);
      color: var(--ink-700);
      font-size: 12px;
      font-weight: 600;
      cursor: pointer;
      transition: border-color var(--t-fast), color var(--t-fast), background var(--t-fast);
    }

    .watch-btn:hover {
      border-color: var(--brand);
      color: var(--brand);
    }

    .watch-btn.watching {
      border-color: transparent;
      background: var(--brand-soft);
      color: var(--brand);
    }

    .menu {
      position: absolute;
      top: calc(100% + 8px);
      right: 0;
      z-index: 30;
      width: 280px;
      padding: 12px;
      background: var(--surface);
      border: 1px solid var(--line);
      border-radius: var(--r-md);
      box-shadow: var(--shadow-raised);
    }

    .menu-title {
      margin: 0 0 8px;
      font-size: 12px;
      font-weight: 600;
      letter-spacing: 0.04em;
      text-transform: uppercase;
      color: var(--ink-500);
    }

    .menu-note {
      margin: 4px 0 10px;
      font-size: 13px;
      color: var(--ink-500);
    }

    .menu-list {
      list-style: none;
      margin: 0 0 10px;
      padding: 0;
      max-height: 200px;
      overflow-y: auto;
    }

    .menu-item {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 7px 8px;
      border-radius: 8px;
      font-size: 13.5px;
      cursor: pointer;
    }

    .menu-item:hover {
      background: var(--surface-sunken);
    }

    .menu-item.busy {
      opacity: 0.6;
    }

    .item-name {
      flex: 1;
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      color: var(--ink-900);
    }

    .item-count {
      font-size: 12px;
      color: var(--ink-500);
      font-variant-numeric: tabular-nums;
    }

    .create-row {
      display: flex;
      gap: 8px;
      padding-top: 10px;
      border-top: 1px solid var(--line);
    }

    .create-input {
      height: 32px;
      font-size: 13px;
    }

    .create-row .btn {
      flex-shrink: 0;
    }

    .menu-msg {
      margin: 8px 0 0;
      font-size: 12.5px;
      color: var(--positive);
    }

    .menu-msg.error {
      color: var(--negative);
    }

    .menu-link {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      margin-top: 10px;
      font-size: 12.5px;
      font-weight: 600;
      color: var(--brand-strong);
    }

    .menu-link i {
      font-size: 10px;
    }
  `],
})
export class AddToWatchlistComponent implements OnInit {
  readonly store = inject(WatchlistStore);
  private readonly host = inject(ElementRef<HTMLElement>);

  readonly symbol = input.required<string>();

  readonly open = signal(false);
  /** Watchlist currently being changed; -1 while creating a new one */
  readonly busyId = signal<number | null>(null);
  readonly message = signal<{ text: string; error: boolean } | null>(null);
  newName = '';

  readonly containing = computed(() => this.store.listsContaining(this.symbol()));

  ngOnInit(): void {
    this.store.ensureLoaded();
  }

  toggle(): void {
    this.open.update((o) => !o);
    this.message.set(null);
  }

  isIn(watchlistId: number): boolean {
    return this.containing().some((w) => w.watchlistId === watchlistId);
  }

  toggleList(watchlistId: number): void {
    const symbol = this.symbol();
    const name = this.store.find(watchlistId)?.name ?? 'watchlist';
    const removing = this.isIn(watchlistId);
    const request: Observable<unknown> = removing ? this.store.removeSymbol(watchlistId, symbol) : this.store.addSymbol(watchlistId, symbol);

    this.busyId.set(watchlistId);
    request.subscribe({
      next: () => {
        this.busyId.set(null);
        this.message.set({ text: removing ? `Removed from ${name}` : `Added to ${name}`, error: false });
      },
      error: (err: Error) => {
        this.busyId.set(null);
        this.message.set({ text: err.message, error: true });
      },
    });
  }

  createAndAdd(): void {
    const name = this.newName.trim();
    if (!name) {
      return;
    }
    this.busyId.set(-1);
    this.store.create(name).subscribe({
      next: (list) => {
        this.newName = '';
        this.busyId.set(null);
        this.toggleList(list.watchlistId);
      },
      error: (err: Error) => {
        this.busyId.set(null);
        this.message.set({ text: err.message, error: true });
      },
    });
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.open.set(false);
  }
}
