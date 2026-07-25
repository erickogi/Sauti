import { useMemo } from 'react';
import type { ParticipantView } from '@sauti/core';

export interface RosterEntry {
  participant: ParticipantView;
  isSelf: boolean;
  name: string;
}

export function shortId(value: string): string {
  return value.length > 12 ? `${value.slice(0, 12)}…` : value;
}

export function resolveName(participant: ParticipantView): string {
  const name = participant.metadata.name;
  if (typeof name === 'string' && name.trim().length > 0) {
    return name.trim();
  }
  return shortId(participant.participantId);
}

export function useParticipantRoster(
  participants: ParticipantView[],
  selfId?: string
): RosterEntry[] {
  return useMemo(() => {
    const sorted = [...participants].sort((a, b) => {
      if (!selfId) return 0;
      if (a.participantId === selfId) return -1;
      if (b.participantId === selfId) return 1;
      return 0;
    });
    return sorted.map((participant) => ({
      participant,
      isSelf: selfId != null && participant.participantId === selfId,
      name: resolveName(participant)
    }));
  }, [participants, selfId]);
}
