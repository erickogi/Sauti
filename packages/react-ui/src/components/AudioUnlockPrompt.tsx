import type { ReactElement } from 'react';
import { resolveLabels } from '../labels.js';
import { joinClass, type BindingProps } from '../types.js';

export function AudioUnlockPrompt({
  binding,
  labels,
  className,
  style
}: BindingProps): ReactElement | null {
  const resolved = resolveLabels(labels);
  if (!binding.audioBlocked) return null;
  const handleClick = (): void => {
    void binding.unlockAudio();
  };
  return (
    <button
      type="button"
      className={joinClass('sauti-button', 'sauti-button--unlock', className)}
      style={style}
      onClick={handleClick}
    >
      {resolved.unlockAudio}
    </button>
  );
}