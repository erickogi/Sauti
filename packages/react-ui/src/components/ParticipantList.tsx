import type { ComponentType, ReactElement, ReactNode } from 'react';
import { useParticipantRoster, type RosterEntry } from '../hooks/useParticipantRoster.js';
import { resolveLabels } from '../labels.js';
import { joinClass, type BindingProps, type ParticipantTileProps } from '../types.js';
import { ParticipantTile } from './ParticipantTile.js';

export interface ParticipantListProps extends BindingProps {
  selfId?: string;
  tile?: ComponentType<ParticipantTileProps>;
  renderParticipant?: (entry: RosterEntry) => ReactNode;
}

export function ParticipantList({
  binding,
  selfId,
  labels,
  tile,
  renderParticipant,
  className,
  style
}: ParticipantListProps): ReactElement | null {
  const resolved = resolveLabels(labels);
  const roster = useParticipantRoster(binding.participants, selfId);
  if (roster.length === 0) return null;
  const Tile = tile ?? ParticipantTile;
  return (
    <ul
      className={joinClass('sauti-participants', className)}
      style={style}
      aria-label={resolved.participantsHeading}
    >
      {roster.map((entry) =>
        renderParticipant ? (
          renderParticipant(entry)
        ) : (
          <Tile
            key={entry.participant.participantId}
            participant={entry.participant}
            isSelf={entry.isSelf}
            name={entry.name}
            labels={labels}
          />
        )
      )}
    </ul>
  );
}
