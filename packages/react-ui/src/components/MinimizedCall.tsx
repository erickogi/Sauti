import type { ReactElement } from 'react';
import { CallStatus } from './CallStatus.js';
import { DurationTimer } from './DurationTimer.js';
import { MuteButton } from './MuteButton.js';
import { EndCallButton } from './EndCallButton.js';
import { resolveLabels } from '../labels.js';
import { joinClass, type BindingProps } from '../types.js';

export interface MinimizedCallProps extends BindingProps {
  onExpand?: () => void;
  onEnd?: () => void;
}

export function MinimizedCall({
  binding,
  labels,
  onExpand,
  onEnd,
  className,
  style
}: MinimizedCallProps): ReactElement {
  const resolved = resolveLabels(labels);
  return (
    <div className={joinClass('sauti-minimized', className)} style={style}>
      <button
        type="button"
        className={joinClass('sauti-minimized__return')}
        aria-label={resolved.returnToCall}
        onClick={onExpand}
      >
        <CallStatus snapshot={binding} labels={labels} />
        {binding.phase === 'connected' ? (
          <DurationTimer durationMs={binding.durationMs} />
        ) : null}
      </button>
      <MuteButton binding={binding} labels={labels} />
      <EndCallButton binding={binding} labels={labels} onEnd={onEnd} />
    </div>
  );
}
