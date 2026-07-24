import { describe, it, expect } from 'vitest';
import { participantSchema, connectionStateSchema } from '../src/frames.js';
import type { Participant, Room } from '../src/model.js';

describe('Participant model', () => {
  it('exposes exactly the five contract fields with the five-value connectionState union', () => {
    const p: Participant = {
      participantId: 'a',
      joinedAt: 10,
      metadata: {},
      connectionState: 'connected',
      state: { muted: false, onHold: false }
    };
    const parsed = participantSchema.parse(p);
    expect(Object.keys(parsed).sort()).toEqual(
      ['connectionState', 'joinedAt', 'metadata', 'participantId', 'state'].sort()
    );
    expect(parsed.state).toEqual({ muted: false, onHold: false });
  });

  it('accepts every one of the five connection states and rejects any other', () => {
    for (const s of [
      'connecting',
      'connected',
      'reconnecting',
      'unreachable',
      'left'
    ]) {
      expect(connectionStateSchema.parse(s)).toBe(s);
    }
    expect(connectionStateSchema.safeParse('online').success).toBe(false);
    expect(connectionStateSchema.safeParse('idle').success).toBe(false);
  });

  it('has no role-like field anywhere in the source', async () => {
    const fs = await import('node:fs');
    const path = await import('node:path');
    const dir = path.resolve(import.meta.dirname, '../src');
    const files = fs.readdirSync(dir).filter((f) => f.endsWith('.ts'));
    const banned = /\b(role|CallRole|AUTO_ROLE_ORDER)\b/;
    for (const f of files) {
      const body = fs.readFileSync(path.join(dir, f), 'utf8');
      expect(banned.test(body), `${f} must not contain a role symbol`).toBe(false);
    }
  });

  it('never reads into a metadata key or index anywhere in the source', async () => {
    const fs = await import('node:fs');
    const path = await import('node:path');
    const dir = path.resolve(import.meta.dirname, '../src');
    const files = fs.readdirSync(dir).filter((f) => f.endsWith('.ts'));
    const dotAccess = /metadata\s*\.\s*[A-Za-z_$]/;
    const indexAccess = /metadata\s*\[/;
    for (const f of files) {
      const body = fs.readFileSync(path.join(dir, f), 'utf8');
      expect(dotAccess.test(body), `${f} must not read a metadata property`).toBe(
        false
      );
      expect(indexAccess.test(body), `${f} must not index into metadata`).toBe(
        false
      );
    }
  });

  it('rejects a participant missing connectionState or state', () => {
    expect(
      participantSchema.safeParse({
        participantId: 'a',
        joinedAt: 1,
        metadata: {}
      }).success
    ).toBe(false);
    expect(
      participantSchema.safeParse({
        participantId: 'a',
        joinedAt: 1,
        metadata: {},
        connectionState: 'connected'
      }).success
    ).toBe(false);
  });

  it('models a Room whose participants Map is keyed by participantId and startedAt is nullable', () => {
    const p: Participant = {
      participantId: 'x',
      joinedAt: 1,
      metadata: {},
      connectionState: 'connecting',
      state: { muted: false, onHold: false }
    };
    const room: Room = {
      roomId: 'r',
      participants: new Map([[p.participantId, p]]),
      metadata: {},
      maxParticipants: 3,
      startedAt: null
    };
    expect(room.participants.get('x')).toBe(p);
    expect(room.startedAt).toBeNull();
    const started: Room['startedAt'] = 1234;
    expect(started).toBe(1234);
  });
});
