import type {
  ConnectionState,
  Json,
  Participant,
  ParticipantState
} from '@sauti/protocol';

const SEP = '\u0001';

export interface SlotRecord {
  participantId: string;
  joinedAt: number;
  metadata: Record<string, Json>;
  connectionState: ConnectionState;
  state: ParticipantState;
  generation: string;
  dropEpoch: number;
}

export function encodeSlot(record: SlotRecord): string {
  const json = JSON.stringify({
    participantId: record.participantId,
    joinedAt: record.joinedAt,
    metadata: record.metadata,
    connectionState: record.connectionState,
    state: record.state,
    generation: record.generation,
    dropEpoch: record.dropEpoch
  });
  return `${record.generation}${SEP}${record.connectionState}${SEP}${record.dropEpoch}${SEP}${json}`;
}

export function decodeSlot(encoded: string): SlotRecord {
  const first = encoded.indexOf(SEP);
  const second = encoded.indexOf(SEP, first + 1);
  const third = encoded.indexOf(SEP, second + 1);
  const json = encoded.slice(third + 1);
  return JSON.parse(json) as SlotRecord;
}

export function slotToParticipant(record: SlotRecord): Participant {
  return {
    participantId: record.participantId,
    joinedAt: record.joinedAt,
    metadata: record.metadata,
    connectionState: record.connectionState,
    state: record.state
  };
}
