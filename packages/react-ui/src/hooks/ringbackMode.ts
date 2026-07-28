import type { CallSnapshot } from '@sauti/core';

export type RingbackMode = 'ringing' | 'silent';

export function ringbackMode(
  snapshot: CallSnapshot,
  selfParticipantId?: string
): RingbackMode {
  const remotePresent =
    selfParticipantId == null
      ? snapshot.participants.length > 0
      : snapshot.participants.some((peer) => peer.participantId !== selfParticipantId);
  return snapshot.phase === 'connecting' ||
    (snapshot.phase === 'connected' && !remotePresent)
    ? 'ringing'
    : 'silent';
}
