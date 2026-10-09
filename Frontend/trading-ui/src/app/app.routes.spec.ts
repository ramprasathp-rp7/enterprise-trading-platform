import { TestBed } from '@angular/core/testing';
import { Route, Router } from '@angular/router';
import { vi } from 'vitest';
import { routes } from './app.routes';
import { customerGuard } from './core/auth/customer.guard';
import { CoreAuthService } from './core/auth/core-auth.service';

/** Every route in the tree, children included, with its full path */
function flatten(list: Route[], parent = ''): { path: string; route: Route }[] {
  return list.flatMap((route) => {
    const path = [parent, route.path].filter((p) => p !== undefined && p !== '').join('/');
    return [{ path, route }, ...flatten(route.children ?? [], path)];
  });
}

describe('app routes', () => {
  const all = flatten(routes);

  it('every lazy route resolves to a component', async () => {
    const lazy = all.filter((r) => r.route.loadComponent);
    expect(lazy.length).toBeGreaterThan(10);

    for (const { path, route } of lazy) {
      const loaded = await (route.loadComponent as () => Promise<unknown>)();
      expect(loaded, `route ${path}`).toBeTruthy();
    }
  });

  it('has the customer pages, including notifications and instruments', () => {
    const paths = all.map((r) => r.path);
    for (const page of ['dashboard', 'positions', 'order-ticket', 'watchlists', 'instruments', 'notifications', 'settings']) {
      expect(paths).toContain(page);
    }
    expect(paths).toContain('admin/notifications');
  });

  it('sends unknown paths somewhere instead of a blank page', () => {
    const wildcard = all.find((r) => r.route.path === '**');
    expect(wildcard?.route.redirectTo).toBeTruthy();
  });
});

describe('customerGuard', () => {
  const run = (isAdmin: boolean) => {
    const navigateByUrl = vi.fn();
    TestBed.configureTestingModule({
      providers: [
        { provide: CoreAuthService, useValue: { isAdmin: () => isAdmin } },
        { provide: Router, useValue: { navigateByUrl } },
      ],
    });
    const result = TestBed.runInInjectionContext(() => customerGuard({} as never, {} as never));
    return { result, navigateByUrl };
  };

  afterEach(() => TestBed.resetTestingModule());

  it('lets customers through', () => {
    const { result, navigateByUrl } = run(false);
    expect(result).toBe(true);
    expect(navigateByUrl).not.toHaveBeenCalled();
  });

  it('sends admins to their analytics page', () => {
    vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    const { result, navigateByUrl } = run(true);
    expect(result).toBe(false);
    expect(navigateByUrl).toHaveBeenCalledWith('/admin/analytics');
  });
});
