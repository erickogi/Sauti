import type { Json } from './json.js';

export type ConnectionState =
  | 'connecting'
  | 'connected'
  | 'reconnecting'
  | 'unreachable'
  | 'left';

export interface ParticipantState {
  muted: boolean;
  onHold: boolean;
}

export interface Participant {
  participantId: string;
  joinedAt: number;
  metadata: Record<string, Json>;
  connectionState: ConnectionState;
  state: ParticipantState;
}

export interface Room {
  roomId: string;
  participants: Map<string, Participant>;
  metadata: Record<string, Json>;
  maxParticipants: number;
  startedAt: number | null;
}
