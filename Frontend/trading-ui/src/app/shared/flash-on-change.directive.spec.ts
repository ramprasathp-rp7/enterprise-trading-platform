import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FlashOnChangeDirective } from './flash-on-change.directive';
import { ReplayAnimationDirective } from './replay-animation.directive';

@Component({
  standalone: true,
  imports: [FlashOnChangeDirective, ReplayAnimationDirective],
  template: `
    <span id="price" [appFlashOnChange]="price()">{{ price() }}</span>
    <section id="panel" [appReplayAnimation]="key()"></section>
  `,
})
class HostComponent {
  readonly price = signal<number | null>(100);
  readonly key = signal<string | null>('a');
}

describe('motion directives', () => {
  const setup = () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const el = (id: string) => fixture.nativeElement.querySelector('#' + id) as HTMLElement;
    return { fixture, host: fixture.componentInstance, el };
  };

  describe('FlashOnChangeDirective', () => {
    it('does not flash the first value', () => {
      const { el } = setup();

      expect(el('price').className).toBe('');
    });

    it('flashes up on a rise and down on a fall', () => {
      const { fixture, host, el } = setup();

      host.price.set(101);
      fixture.detectChanges();
      expect(el('price').classList.contains('flash-up')).toBe(true);

      host.price.set(99);
      fixture.detectChanges();
      expect(el('price').classList.contains('flash-down')).toBe(true);
      expect(el('price').classList.contains('flash-up')).toBe(false);
    });

    it('ignores changes to or from no price', () => {
      const { fixture, host, el } = setup();

      host.price.set(null);
      fixture.detectChanges();
      host.price.set(120);
      fixture.detectChanges();

      expect(el('price').className).toBe('');
    });

    it('clears the flash when its own animation ends, not a child one', () => {
      const { fixture, host, el } = setup();
      host.price.set(101);
      fixture.detectChanges();
      const child = document.createElement('i');
      el('price').appendChild(child);

      child.dispatchEvent(new Event('animationend', { bubbles: true }));
      expect(el('price').classList.contains('flash-up')).toBe(true);

      el('price').dispatchEvent(new Event('animationend'));
      expect(el('price').classList.contains('flash-up')).toBe(false);
    });
  });

  describe('ReplayAnimationDirective', () => {
    it('replays only when the key changes', () => {
      const { fixture, host, el } = setup();
      expect(el('panel').classList.contains('anim-swap')).toBe(false);

      host.key.set('a');
      fixture.detectChanges();
      expect(el('panel').classList.contains('anim-swap')).toBe(false);

      host.key.set('b');
      fixture.detectChanges();
      expect(el('panel').classList.contains('anim-swap')).toBe(true);

      el('panel').dispatchEvent(new Event('animationend'));
      expect(el('panel').classList.contains('anim-swap')).toBe(false);
    });
  });
});
