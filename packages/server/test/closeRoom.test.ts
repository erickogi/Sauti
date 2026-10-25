import { describe, it, expect, afterEach } from 'vitest';
import { makeDeps, tokenFor } from './deps.js';
import { FakeRedisPort } from './fakeRedis.js';
import { startServer, stopServer, TestClient, delay, type RunningServer } from './harness.js';

let running: RunningServer | undefined;

afterEach(async () => {
  if (running) await stopServer(running);
  running = undefined;
});

describe('closeRoom', () => {
  it('ends a two-party call: each peer is told the other left and the room is freed', async () => {
    const deps = makeDeps();
    running = await startServer(deps);
    const a = new TestClient(running.url);
    await a.join(tokenFor('room1', 'alice'));
    const b = new TestClient(running.url);
    await b.join(tokenFor('room1', 'bob'));
    await a.waitForType('participant-joined');

    const evicted = await running.server.closeRoom('room1');
    expect(evicted).toBe(2);

    const aLeft = await a.waitForType('participant-left');
    const bLeft = await b.waitForType('participant-left');
    expect(aLeft.participantId).toBe('bob');
    expect(bLeft.participantId).toBe('alice');
    expect(await deps.redis.hget('sauti:room:room1', 'alice')).toBeNull();
    expect(await deps.redis.hget('sauti:room:room1', 'bob')).toBeNull();
    a.close();
    b.close();
  });

  it('emits participant.left(room_closed) and room.closed, and does not self-notify a lone participant', async () => {
    const deps = makeDeps();
    running = await startServer(deps);
    const a = new TestClient(running.url);
    await a.join(tokenFor('room2', 'alice'));

    const evicted = await running.server.closeRoom('room2');
    expect(evicted).toBe(1);

    await delay(30);
    expect(a.received('participant-left')).toBe(false);

    const left = deps.events.filter(
      (e) => e.type === 'participant.left' && e.data?.reason === 'room_closed',
    );
    expect(left.map((e) => e.participantId)).toContain('alice');
    expect(deps.events.some((e) => e.type === 'room.closed' && e.roomId === 'room2')).toBe(true);
    a.close();
  });

  it('is a silent no-op on an unknown room: returns 0 and emits nothing', async () => {
    const deps = makeDeps();
    running = await startServer(deps);
    const evicted = await running.server.closeRoom('ghost');
    expect(evicted).toBe(0);
    expect(deps.events.some((e) => e.type === 'participant.left')).toBe(false);
    expect(deps.events.some((e) => e.type === 'room.closed')).toBe(false);
  });

  it('evicts a participant hosted on another pod', async () => {
    const redis = new FakeRedisPort();
    const pod1 = await startServer(makeDeps({ redis }));
    running = pod1;
    const pod2 = await startServer(makeDeps({ redis }));
    try {
      const a = new TestClient(pod1.url);
      await a.join(tokenFor('roomX', 'A'));
      const b = new TestClient(pod2.url);
      await b.join(tokenFor('roomX', 'B'));
      await a.waitForType('participant-joined');

      const evicted = await pod1.server.closeRoom('roomX');
      expect(evicted).toBe(2);
      const bLeft = await b.waitForType('participant-left');
      expect(bLeft.participantId).toBe('A');
      a.close();
      b.close();
    } finally {
      await stopServer(pod2);
    }
  });

  it('is best-effort: a failure evicting one participant still evicts the others and throws', async () => {
    class FlakyRedis extends FakeRedisPort {
      override async hdel(key: string, field: string): Promise<void> {
        if (field === 'bad') throw new Error('redis down');
        return super.hdel(key, field);
      }
    }
    const redis = new FlakyRedis();
    running = await startServer(makeDeps({ redis }));
    const good = new TestClient(running.url);
    await good.join(tokenFor('room3', 'good'));
    const bad = new TestClient(running.url);
    await bad.join(tokenFor('room3', 'bad'));
    await good.waitForType('participant-joined');

    await expect(running.server.closeRoom('room3')).rejects.toThrow(/evict/);
    expect(await redis.hget('sauti:room:room3', 'good')).toBeNull();
    good.close();
    bad.close();
  });
});
