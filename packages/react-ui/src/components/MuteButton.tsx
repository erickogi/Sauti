import type { ReactElement } from 'react';
import { useMuteToggle } from '../hooks/useMuteToggle.js';
import { resolveLabels } from '../labels.js';
import { joinClass, type BindingProps } from '../types.js';

export function MuteButton({
  binding,
  labels,
  className,
  style
}: BindingProps): ReactElement {
  const resolved = resolveLabels(labels);
  const { muted, toggle } = useMuteToggle(binding);
  return (
    <button
      type="button"
      className={joinClass('sauti-button', 'sauti-button--mute', className)}
      style={style}
      aria-pressed={muted}
      onClick={toggle}
    >
      {muted ? resolved.unmute : resolved.mute}
    </button>
  );
}