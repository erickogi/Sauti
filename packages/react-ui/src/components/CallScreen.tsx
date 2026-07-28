import type { CSSProperties, ReactElement, ReactNode } from 'react';
import type { SautiCall } from '@sauti/core';
import { useSautiCall } from '@sauti/react';
import { resolveLabels, type SautiLabels } from '../labels.js';
import { joinClass, type CallScreenSlots } from '../types.js';
import type { RosterEntry } from '../hooks/useParticipantRoster.js';
import { CallStatus } from './CallStatus.js';
import { DurationTimer } from './DurationTimer.js';
import { QualityIndicator } from './QualityIndicator.js';
import { ParticipantList } from './ParticipantList.js';
import { AudioUnlockPrompt } from './AudioUnlockPrompt.js';
import { WeakConnectionBanner } from './WeakConnectionBanner.js';
import { ControlBar } from './ControlBar.js';

export interface CallScreenProps {
  call: SautiCall;
  selfParticipantId?: string;
  labels?: Partial<SautiLabels>;
  slots?: CallScreenSlots;
  renderParticipant?: (entry: RosterEntry) => ReactNode;
  onEnd?: () => void;
  className?: string;
  style?: CSSProperties;
}

export function CallScreen({
  call,
  selfParticipantId,
  labels,
  slots,
  renderParticipant,
  onEnd,
  className,
  style
}: CallScreenProps): ReactElement {
  const binding = useSautiCall(call);
  const resolved = resolveLabels(labels);
  const Controls = slots?.controlBar ?? ControlBar;
  const active = binding.phase === 'connecting' || binding.phase === 'connected';
  const connected = binding.phase === 'connected';
  return (
    <div className={joinClass('sauti-call', className)} style={style}>
      <div className="sauti-call__header">
        <CallStatus snapshot={binding} labels={labels} selfId={selfParticipantId} />
        {connected && (
          <DurationTimer durationMs={binding.durationMs} className="sauti-call__timer" />
        )}
        {active && (
          <QualityIndicator quality={binding.quality} labels={labels} live />
        )}
      </div>

      <WeakConnectionBanner binding={binding} labels={labels} />
      <AudioUnlockPrompt binding={binding} labels={labels} />

      <ParticipantList
        binding={binding}
        selfId={selfParticipantId}
        labels={labels}
        tile={slots?.participantTile}
        renderParticipant={renderParticipant}
      />

      {active && (
        <Controls binding={binding} labels={resolved} onEnd={onEnd} />
      )}
    </div>
  );
}
