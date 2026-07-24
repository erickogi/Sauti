import { describe, it, expect, afterEach } from 'vitest';
import { readyFrameSchema } from '@sauti/protocol';
import { makeDeps, tokenFor } from './deps.js';
import { startServer, stopServer, TestClient, type RunningServer } from './harness.js';

let running: RunningServer | undefined;

afterEach(async () => {
  if (running) await stopServer(running);
  running = undefined;
});

describe('fresh join', () => {
  it('admits a participant and returns a schema-valid ready with self, empty peers, and iceServers', async () => {
    const deps = makeDeps();
    running = await startServer(deps);
    const a = new TestClient(running.url);
    const ready = await a.join(tokenFor('room1', 'alice'));
    expect(ready.type).toBe('ready');
    expect(readyFrameSchema.safeParse(ready).success).toBe(true);
    expect((ready.self as { participantId: string }).participantId).toBe('alice');
    expect(ready.peers).toEqual([]);
    expect(ready.resumed).toBe(false);
    expect((ready.room as { maxParticipants: number }).maxParticipants).toBe(3);
    expect(Array.isArray(ready.iceServers)).toBe(true);
    a.close();
  });

  it('announces a new participant to existing peers with a full Participant', async () => {
    const deps = makeDeps();
    running = await startServer(deps);
    const a = new TestClient(running.url);
    await a.join(tokenFor('room1', 'alice'));
    const b = new TestClient(running.url);
    await b.join(tokenFor('room1', 'bob'));
    const joined = await a.waitForType('participant-joined');
    expect(
      (joined.participant as { participantId: string }).participantId
    ).toBe('bob');
    expect(
      (joined.participant as { connectionState: string }).connectionState
    ).toBe('connected');
    a.close();
    b.close();
  });

  it('gives a later joiner the earlier peers in ready.peers', async () => {
    const deps = makeDeps();
    running = await startServer(deps);
    const a = new TestClient(running.url);
    await a.join(tokenFor('room1', 'alice'));
    const b = new TestClient(running.url);
    const ready = await b.join(tokenFor('room1', 'bob'));
    const peers = ready.peers as Array<{ participantId: string }>;
    expect(peers.map((p) => p.participantId)).toEqual(['alice']);
    a.close();
    b.close();
  });
});

describe('capacity', () => {
  it('rejects the fourth participant at the default max of 3 with room_full', async () => {
    const deps = makeDeps();
    running = await startServer(deps);
    const clients: TestClient[] = [];
    for (const id of ['a', 'b', 'c']) {
      const c = new TestClient(running.url);
      const ready = await c.join(tokenFor('room1', id));
      expect(ready.type).toBe('ready');
      clients.push(c);
    }
    const d = new TestClient(running.url);
    const res = await d.join(tokenFor('room1', 'd'));
    expect(res.type).toBe('error');
    expect(res.code).toBe('room_full');
    for (const c of clients) c.close();
  });

  it('honours a custom maxParticipantsPerRoom', async () => {
    const deps = makeDeps({ maxParticipantsPerRoom: 2 });
    running = await startServer(deps);
    const a = new TestClient(running.url);
    await a.join(tokenFor('room1', 'a'));
    const b = new TestClient(running.url);
    await b.join(tokenFor('room1', 'b'));
    const c = new TestClient(running.url);
    const res = await c.join(tokenFor('room1', 'c'));
    expect(res.code).toBe('room_full');
    a.close();
    b.close();
  });
});

describe('handshake failures', () => {
  it('rejects an unauthorized token with an error and closes', async () => {
    const deps = makeDeps();
    running = await startServer(deps);
    const a = new TestClient(running.url);
    const res = await a.join('deny');
    expect(res.type).toBe('error');
    expect(res.code).toBe('unauthorized');
  });

  it('rejects a first frame that is not a join', async () => {
    const deps = makeDeps();
    running = await startServer(deps);
    const a = new TestClient(running.url);
    await a.connect();
    a.send({ v: 1, type: 'state', state: { muted: true } });
    const res = await a.waitForType('error');
    expect(res.code).toBe('bad_handshake');
  });

  it('rejects a version-mismatched join before parsing it as a join', async () => {
    const deps = makeDeps();
    running = await startServer(deps);
    const a = new TestClient(running.url);
    await a.connect();
    a.send({ v: 2, type: 'join', token: tokenFor('room1', 'a') });
    const res = await a.waitForType('error');
    expect(res.code).toBe('version_mismatch');
  });

  it('ignores a non-JSON message without crashing', async () => {
    const deps = makeDeps();
    running = await startServer(deps);
    const a = new TestClient(running.url);
    await a.connect();
    a.ws.send('not json');
    a.send({ v: 1, type: 'join', token: tokenFor('room1', 'a') });
    const ready = await a.waitForType('ready');
    expect(ready.type).toBe('ready');
    a.close();
  });
});

describe('explicit leave', () => {
  it('broadcasts participant-left to peers and frees the slot', async () => {
    const deps = makeDeps();
    running = await startServer(deps);
    const a = new TestClient(running.url);
    await a.join(tokenFor('room1', 'alice'));
    const b = new TestClient(running.url);
    await b.join(tokenFor('room1', 'bob'));
    await a.waitForType('participant-joined');
    b.send({ v: 1, type: 'leave' });
    const left = await a.waitForType('participant-left');
    expect(left.participantId).toBe('bob');
    const slot = await deps.redis.hget('sauti:room:room1', 'bob');
    expect(slot).toBeNull();
    a.close();
  });
});

describe('listActiveRooms', () => {
  it('returns the shape and counts for active rooms without scanning keys', async () => {
    const deps = makeDeps();
    running = await startServer(deps);
    const a = new TestClient(running.url);
    await a.join(tokenFor('roomA', 'a1'));
    const b = new TestClient(running.url);
    await b.join(tokenFor('roomA', 'a2'));
    const c = new TestClient(running.url);
    await c.join(tokenFor('roomB', 'b1'));
    const rooms = await running.server.listActiveRooms();
    const byId = new Map(rooms.map((r) => [r.roomId, r]));
    expect(byId.get('roomA')?.participantCount).toBe(2);
    expect(byId.get('roomA')?.participantIds.sort()).toEqual(['a1', 'a2']);
    expect(typeof byId.get('roomA')?.startedAt).toBe('number');
    expect(byId.get('roomB')?.participantCount).toBe(1);
    expect(byId.get('roomB')?.startedAt).toBeNull();
    a.close();
    b.close();
    c.close();
  });
});
