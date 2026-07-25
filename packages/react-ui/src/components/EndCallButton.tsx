import type { ReactElement } from 'react';
import { resolveLabels } from '../labels.js';
import { joinClass, type ControlBarProps } from '../types.js';

export function EndCallButton({
  binding,
  labels,
  onEnd,
  className,
  style
}: ControlBarProps): ReactElement {
  const resolved = resolveLabels(labels);
  const handleClick = (): void => {
    binding.leave();
    onEnd?.();
  };
  return (
    <button
      type="button"
      className={joinClass('sauti-button', 'sauti-button--end', className)}
      style={style}
      onClick={handleClick}
    >
      {resolved.end}
    </button>
  );
}