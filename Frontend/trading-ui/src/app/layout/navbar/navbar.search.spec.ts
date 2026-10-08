import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, DeferBlockBehavior, DeferBlockState, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { NavbarComponent } from './navbar.component';
import { CoreAuthService } from '../../core/auth/core-auth.service';
import { SignInStore } from '../../features/sign-in/sign-in.store';
import { AccountsService } from '../../api/generated/trade';
import { Instrument } from '../../features/instruments/instrument.models';

/** Instrument search from the navbar (icon, Ctrl/⌘+K and "/"). */
describe('NavbarComponent – instrument search', () => {
  let fixture: ComponentFixture<NavbarComponent>;
  let component: NavbarComponent;
  let isAdmin: ReturnType<typeof vi.fn>;
  let navigate: ReturnType<typeof vi.fn>;

  async function create(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [NavbarComponent],
      deferBlockBehavior: DeferBlockBehavior.Manual,
      providers: [
        provideRouter([]),
        { provide: CoreAuthService, useValue: { currentUser: () => ({ accountId: 7, username: 'rkumar' }), isAdmin } },
        { provide: SignInStore, useValue: { logout: vi.fn() } },
        { provide: AccountsService, useValue: { getAccount: vi.fn(() => of({ accountId: 'ACC-000007' })) } },
      ],
    }).compileComponents();
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true) as any;
    fixture = TestBed.createComponent(NavbarComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  const press = (init: KeyboardEventInit, target: EventTarget = document) => {
    target.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true, cancelable: true, ...init }));
    fixture.detectChanges();
  };

  beforeEach(() => {
    isAdmin = vi.fn(() => false);
  });

  it('shows a Preferences shortcut next to the bell for customers', async () => {
    await create();

    const link: HTMLAnchorElement = fixture.nativeElement.querySelector('[data-testid="nav-preferences-icon"]');
    expect(link).not.toBeNull();
    expect(link.getAttribute('href')).toBe('/settings');
    expect(link.getAttribute('aria-label')).toBe('Preferences');
  });

  it('points the Preferences shortcut at the admin page for admins', async () => {
    isAdmin.mockReturnValue(true);
    await create();

    const link: HTMLAnchorElement = fixture.nativeElement.querySelector('[data-testid="nav-preferences-icon"]');
    expect(link.getAttribute('href')).toBe('/admin/preferences');
  });

  it('opens the search dialog from the search icon', async () => {
    await create();

    fixture.nativeElement.querySelector('[data-testid="instrument-search-btn"]').click();
    fixture.detectChanges();
    const [dialogBlock] = await fixture.getDeferBlocks();
    await dialogBlock.render(DeferBlockState.Complete);

    expect(component.searchOpen()).toBe(true);
    expect(fixture.nativeElement.querySelector('[data-testid="search-dialog"]')).not.toBeNull();
  });

  it('opens with Ctrl+K, ⌘+K and "/"', async () => {
    await create();

    press({ key: 'k', ctrlKey: true });
    expect(component.searchOpen()).toBe(true);

    component.closeSearch();
    press({ key: 'K', metaKey: true });
    expect(component.searchOpen()).toBe(true);

    component.closeSearch();
    press({ key: '/' });
    expect(component.searchOpen()).toBe(true);
  });

  it('ignores "/" while typing in a field, and other keys', async () => {
    await create();
    const field = document.createElement('input');
    document.body.appendChild(field);

    press({ key: '/' }, field);
    press({ key: 'k' });
    expect(component.searchOpen()).toBe(false);

    field.remove();
  });

  it('opens the trade view for the picked instrument and closes the dialog', async () => {
    await create();
    component.openSearch();

    component.openInstrument({ symbol: 'INFY.NS' } as Instrument);

    expect(component.searchOpen()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/order-ticket'], { queryParams: { symbol: 'INFY.NS' } });
  });

  it('is not offered to admins, who cannot open customer trading pages', async () => {
    isAdmin.mockReturnValue(true);
    await create();

    expect(fixture.nativeElement.querySelector('[data-testid="instrument-search-btn"]')).toBeNull();
    press({ key: 'k', ctrlKey: true });
    expect(component.searchOpen()).toBe(false);
  });
});
