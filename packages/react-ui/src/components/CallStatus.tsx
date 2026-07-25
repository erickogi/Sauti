import type { ReactElement } from 'react';
import type { CallSnapshot } from '@sauti/core';
import { useCallStatus } from '../hooks/useCallStatus.js';
import { resolveLabels } from '../labels.js';
import { joinClass, type Styleable } from '../types.js';
import type { SautiLabels } from '../labels.js';

export interface CallStatusProps extends Styleable {
  snapshot: CallSnapshot;
  labels?: Partial<SautiLabels>;
}

export function CallStatus({
  snapshot,
  labels,
  className,
  style
}: CallStatusProps): ReactElement {
  const resolved = resolveLabels(labels);
  const status = useCallStatus(snapshot, resolved);
  return (
    <span
      className={joinClass('sauti-status', `sauti-status--${status.key}`, className)}
      style={style}
      data-status={status.key}
      role="status"
      aria-live="polite"
    >
      {status.text}
    </span>
  );
}