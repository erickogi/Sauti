import type { ReactElement } from 'react';
import { resolveLabels } from '../labels.js';
import { joinClass, type BindingProps } from '../types.js';

export function WeakConnectionBanner({
  binding,
  labels,
  className,
  style
}: BindingProps): ReactElement | null {
  const resolved = resolveLabels(labels);
  if (!binding.fallback) return null;
  return (
    <div
      className={joinClass('sauti-banner', className)}
      style={style}
      role="status"
      aria-live="polite"
    >
      <span className="sauti-banner__title">{resolved.weakConnectionTitle}</span>
      <span className="sauti-banner__body">{resolved.weakConnectionBody}</span>
    </div>
  );
}