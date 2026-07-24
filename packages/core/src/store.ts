import type { ConnectionState, Json } from '@sauti/protocol';
import type { CallSnapshot, ParticipantView, Quality } from './types.js';

const QUALITY_RANK: Record<Quality, number> = { good: 0, degraded: 1, poor: 2 };

function worst(a: Quality, b: Quality): Quality {
  return QUALITY_RANK[a] >= QUALITY_RANK[b] ? a : b;
}

export interface ParticipantSeed {
  participantId: string;
  metadata: Record<string, Json>;
  muted: boolean;
  onHold: boolean;
  connectionState: ConnectionState;
}

export class CallStore {
  private readonly listeners = new Set<() => void>();
  private readonly participants = new Map<string, ParticipantView>();

  private selfId: string | null = null;
  private phase: CallSnapshot['phase'] = 'idle';
  private localMuted = false;
  private localOnHold = false;
  private startedAt: number | null = null;
  private clockOffset = 0;
  private socketReconnecting = false;
  private audioBlocked = false;
  private fallback = false;

  private snapshot: CallSnapshot = this.build();

  constructor(private readonly now: () => number) {}

  subscribe(listener: () => void): () => void {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  listenerCount(): number {
    return this.listeners.size;
  }

  getSnapshot(): CallSnapshot {
    return this.snapshot;
  }

  private build(): CallSnapshot {
    const participants: ParticipantView[] = [...this.participants.values()];
    let aggregate: Quality = 'good';
    let anyReconnecting = this.socketReconnecting;
    for (const p of participants) {
      if (p.participantId !== this.selfId) {
        aggregate = worst(aggregate, p.quality);
      }
      if (p.connectionState === 'reconnecting' || p.connectionState === 'unreachable') {
        anyReconnecting = true;
      }
    }
    return {
      phase: this.phase,
      participants,
      localMuted: this.localMuted,
      localOnHold: this.localOnHold,
      durationMs: this.computeDuration(),
      quality: aggregate,
      reconnecting: anyReconnecting,
      audioBlocked: this.audioBlocked,
      fallback: this.fallback
    };
  }

  private computeDuration(): number {
    if (this.startedAt === null) return 0;
    const elapsed = this.now() + this.clockOffset - this.startedAt;
    return elapsed > 0 ? elapsed : 0;
  }

  private commit(): void {
    this.snapshot = this.build();
    for (const listener of [...this.listeners]) listener();
  }

  setSelf(id: string): void {
    this.selfId = id;
  }

  getSelfId(): string | null {
    return this.selfId;
  }

  setPhase(phase: CallSnapshot['phase']): void {
    this.phase = phase;
    this.commit();
  }


  upsertParticipant(seed: ParticipantSeed): void {
    const existing = this.participants.get(seed.participantId);
    this.participants.set(seed.participantId, {
      participantId: seed.participantId,
      metadata: seed.metadata,
      muted: seed.muted,
      onHold: seed.onHold,
      connectionState: seed.connectionState,
      quality: existing?.quality ?? 'good'
    });
    this.commit();
  }

  removeParticipant(id: string): void {
    if (this.participants.delete(id)) this.commit();
  }

  mergeState(id: string, partial: { muted?: boolean; onHold?: boolean }): void {
    const p = this.participants.get(id);
    if (!p) return;
    this.participants.set(id, {
      ...p,
      muted: partial.muted ?? p.muted,
      onHold: partial.onHold ?? p.onHold
    });
    this.commit();
  }

  setConnectionState(id: string, state: ConnectionState): void {
    const p = this.participants.get(id);
    if (!p) return;
    this.participants.set(id, { ...p, connectionState: state });
    this.commit();
  }

  setQuality(id: string, quality: Quality): void {
    const p = this.participants.get(id);
    if (!p || p.quality === quality) return;
    this.participants.set(id, { ...p, quality });
    this.commit();
  }

  getParticipant(id: string): ParticipantView | undefined {
    return this.participants.get(id);
  }

  setLocalMuted(muted: boolean): void {
    this.localMuted = muted;
    if (this.selfId) this.mergeState(this.selfId, { muted });
    else this.commit();
  }

  setLocalOnHold(onHold: boolean): void {
    this.localOnHold = onHold;
    if (this.selfId) this.mergeState(this.selfId, { onHold });
    else this.commit();
  }

  setSocketReconnecting(value: boolean): void {
    this.socketReconnecting = value;
    this.commit();
  }

  setClockOffset(offset: number): void {
    this.clockOffset = offset;
  }

  setStartedAt(startedAt: number | null): void {
    this.startedAt = startedAt;
    this.commit();
  }

  setAudioBlocked(value: boolean): void {
    if (this.audioBlocked === value) return;
    this.audioBlocked = value;
    this.commit();
  }

  setFallback(value: boolean): void {
    if (this.fallback === value) return;
    this.fallback = value;
    this.commit();
  }

  tick(): void {
    this.commit();
  }

  reset(): void {
    this.participants.clear();
    this.selfId = null;
    this.phase = 'left';
    this.localMuted = false;
    this.localOnHold = false;
    this.startedAt = null;
    this.socketReconnecting = false;
    this.audioBlocked = false;
    this.fallback = false;
    this.commit();
  }
}
