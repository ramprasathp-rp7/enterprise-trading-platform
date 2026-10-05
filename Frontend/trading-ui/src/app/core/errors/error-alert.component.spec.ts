import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ErrorAlertComponent } from './error-alert.component';
import { ProcessedError } from './error-response-handler';

describe('ErrorAlertComponent', () => {
  let fixture: ComponentFixture<ErrorAlertComponent>;
  let component: ErrorAlertComponent;

  const error = (overrides: Partial<ProcessedError> = {}): ProcessedError => ({
    code: 'ORD-409',
    message: 'Order is not cancellable',
    isNetworkError: false,
    isUnknownCode: false,
    ...overrides,
  });

  const render = (value: ProcessedError | null) => {
    fixture.componentRef.setInput('error', value);
    fixture.detectChanges();
  };

  beforeEach(async () => {
    vi.spyOn(console, 'log').mockImplementation(() => {});
    await TestBed.configureTestingModule({ imports: [ErrorAlertComponent] }).compileComponents();
    fixture = TestBed.createComponent(ErrorAlertComponent);
    component = fixture.componentInstance;
  });

  it('renders nothing without an error', () => {
    render(null);

    expect(fixture.nativeElement.querySelector('.error-container')).toBeNull();
    expect(component.getErrorClass()).toBe('');
    expect(component.shouldShowRetry()).toBe(false);
    expect(component.shouldShowSignIn()).toBe(false);
  });

  it('shows an API error with Try Again and Dismiss', () => {
    render(error());

    const container = fixture.nativeElement.querySelector('.error-container');
    expect(container.classList).toContain('api-error');
    expect(container.textContent).toContain('Order is not cancellable');
    const buttons = Array.from(fixture.nativeElement.querySelectorAll('button')).map((b: any) => b.textContent.trim());
    expect(buttons).toEqual(['Try Again', 'Dismiss']);
  });

  it('shows the connection hint for network errors', () => {
    render(error({ code: 'NETWORK', isNetworkError: true }));

    expect(component.getErrorClass()).toBe('network-error');
    expect(fixture.nativeElement.textContent).toContain('checking your internet connection');
  });

  it('shows the raw code for unknown errors', () => {
    render(error({ code: 'XYZ-999', isUnknownCode: true }));

    expect(component.getErrorClass()).toBe('unknown-error');
    expect(fixture.nativeElement.querySelector('code').textContent).toBe('XYZ-999');
  });

  it('offers Sign In instead of Try Again for AUTH-401', () => {
    render(error({ code: 'AUTH-401', message: 'Session expired' }));

    expect(component.shouldShowSignIn()).toBe(true);
    expect(component.shouldShowRetry()).toBe(false);
    const buttons = Array.from(fixture.nativeElement.querySelectorAll('button')).map((b: any) => b.textContent.trim());
    expect(buttons).toEqual(['Sign In', 'Dismiss']);
  });

  it('emits retry, signIn and dismiss events', () => {
    const retry = vi.fn();
    const signIn = vi.fn();
    const dismiss = vi.fn();
    component.retry.subscribe(retry);
    component.signIn.subscribe(signIn);
    component.dismiss.subscribe(dismiss);

    render(error());
    fixture.nativeElement.querySelector('.btn-primary').click();
    expect(retry).toHaveBeenCalled();

    component.onSignIn();
    expect(signIn).toHaveBeenCalled();

    fixture.nativeElement.querySelector('.btn-secondary').click();
    fixture.detectChanges();
    expect(dismiss).toHaveBeenCalled();
    expect(component.error).toBeNull();
    expect(fixture.nativeElement.querySelector('.error-container')).toBeNull();
  });
});
