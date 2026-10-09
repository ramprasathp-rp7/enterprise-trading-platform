import { Directive, ElementRef, effect, inject, input } from '@angular/core';

/**
 * Flashes the element green or red when the bound number goes up or down, so live price updates
 * are noticed. The first value, and changes to or from "no value", don't flash.
 *
 *   <td [appFlashOnChange]="row.price">...</td>
 */
@Directive({
  selector: '[appFlashOnChange]',
  standalone: true,
})
export class FlashOnChangeDirective {
  readonly appFlashOnChange = input<number | null | undefined>(null);

  private readonly el = inject<ElementRef<HTMLElement>>(ElementRef);
  private previous: number | null = null;

  constructor() {
    effect(() => {
      const raw = this.appFlashOnChange();
      const value = raw == null || Number.isNaN(Number(raw)) ? null : Number(raw);
      const before = this.previous;
      this.previous = value;
      if (before === null || value === null || value === before) {
        return;
      }
      this.flash(value > before ? 'flash-up' : 'flash-down');
    });
  }

  private flash(cls: 'flash-up' | 'flash-down'): void {
    const node = this.el.nativeElement;
    node.classList.remove('flash-up', 'flash-down');
    // Reading layout restarts the animation when the same direction repeats
    void node.offsetWidth;
    node.classList.add(cls);
    // Only this element's own animation, not ones bubbling up from its content
    const done = (event: AnimationEvent) => {
      if (event.target === node) {
        node.classList.remove(cls);
        node.removeEventListener('animationend', done);
      }
    };
    node.addEventListener('animationend', done);
  }
}
