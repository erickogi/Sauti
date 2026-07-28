import type { ReactElement } from 'react';
import { isReconnecting, resolveLabels } from '../labels.js';
import { joinClass, type ParticipantTileProps } from '../types.js';
import { QualityIndicator } from './QualityIndicator.js';

export function ParticipantTile({
  participant,
  isSelf,
  name,
  labels,
  className,
  style
}: ParticipantTileProps): ReactElement {
  const resolved = resolveLabels(labels);
  const displayName = isSelf ? resolved.you : name;
  const meta: string[] = [];
  if (participant.muted) meta.push(resolved.muted);
  if (participant.onHold) meta.push(resolved.onHold);
  const reconnecting = isReconnecting(participant.connectionState);
  return (
    <li
      className={joinClass('sauti-tile', className)}
      style={style}
      data-self={isSelf}
      data-connection={participant.connectionState}
      data-reconnecting={reconnecting ? 'true' : undefined}
    >
      <span className="sauti-tile__body">
        <span className="sauti-tile__name">{displayName}</span>
        {reconnecting && (
          <span className="sauti-tile__status sauti-tile--reconnecting">
            {resolved.statusReconnecting}
          </span>
        )}
        {meta.length > 0 && (
          <span className="sauti-tile__meta">{meta.join(' · ')}</span>
        )}
      </span>
      {!isSelf && (
        <QualityIndicator
          quality={participant.quality}
          labels={labels}
          className="sauti-tile__quality"
        />
      )}
    </li>
  );
}