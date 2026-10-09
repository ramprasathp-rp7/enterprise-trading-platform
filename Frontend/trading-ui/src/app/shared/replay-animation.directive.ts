import { Directive, ElementRef, effect, inject, input } from '@angular/core';

/**
 * Replays a short fade whenever the key changes, for panels whose content is swapped in place
 * (the selected notification, the selected watchlist). The first value doesn't animate; the page's
 * own entrance covers that.
 *
 *   <section [appReplayAnimation]="selectedId()">...</section>
 */
@Directive({
  selector: '[appReplayAnimation]',
  standalone: true,
})
export class ReplayAnimationDirective {
  readonly appReplayAnimation = input<unknown>(null);

  private readonly el = inject<ElementRef<HTMLElement>>(ElementRef);
  private initialised = false;
  private previous: unknown = null;

  constructor() {
    effect(() => {
      const key = this.appReplayAnimation();
      const changed = this.initialised && key !== this.previous;
      this.initialised = true;
      this.previous = key;
      if (!changed) {
        return;
      }
      const node = this.el.nativeElement;
      node.classList.remove('anim-swap');
      // Reading layout restarts the animation
      void node.offsetWidth;
      node.classList.add('anim-swap');
      // Only this element's own animation, not ones bubbling up from its content
      const done = (event: AnimationEvent) => {
        if (event.target === node) {
          node.classList.remove('anim-swap');
          node.removeEventListener('animationend', done);
        }
      };
      node.addEventListener('animationend', done);
    });
  }
}
