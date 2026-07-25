import type { ReactElement } from 'react';
import { useDurationLabel } from '../hooks/useDurationLabel.js';
import { joinClass, type Styleable } from '../types.js';

export interface DurationTimerProps extends Styleable {
  durationMs: number;
}

export function DurationTimer({
  durationMs,
  className,
  style
}: DurationTimerProps): ReactElement {
  const label = useDurationLabel(durationMs);
  return (
    <span className={joinClass('sauti-timer', className)} style={style}>
      {label}
    </span>
  );
}