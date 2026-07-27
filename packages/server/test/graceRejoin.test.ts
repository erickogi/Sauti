import { describe, it, expect, afterEach, vi } from 'vitest';
import { makeDeps, tokenFor } from './deps.js';
import { FakeRedisPort } from './fakeRedis.js';
import {
  startServer,
  stopServer,
  TestClient,
  type RunningServer
} from './harness.js';

let running: RunningServer | undefined;

afterEach(async () => {
  if (running) await stopServer(running);
  running = undefined;
  vi.useRealTimers();
});

class ExpiringRedis extends FakeRedisPort {
  clock = 0;
  private readonly expiry = new Map<string, number>();

  advance(ms: number): void {
    this.clock += ms;
    this.purge();
  }

  private purge(): void {
    for (const [key, at] of this.expiry) {
      if (at <= this.clock) {
        this.hashes.delete(key);
        this.expiry.delete(key);
      }
    }
  }

  override async pexpire(key: string, ms: number): Promise<void> {
    this.expiry.set(key, this.clock + ms);
  }

  override async hget(key: string, field: string): Promise<string | null> {
    this.purge();
    return super.hget(key, field);
  }

  override async hgetall(key: string): Promise<Record<string, string>> {
    this.purge();
    return super.hgetall(key);
  }

  override async hsetnx(key: string, field: string, value: string): Promise<0 | 1> {
    this.purge();
    return super.hsetnx(key, field, value);
  }

  override async eval(
    script: string,
    keys: string[],
    args: string[]
  ): Promise<unknown> {
    this.purge();
    return super.eval(script, keys, args);
  }
}

describe('grace-expiry then rejoin', () => {
  it('lets B rejoin after its slot is swept and reconnects A to B', async () => {
    vi.useFakeTimers({
      toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval']
    });
    const redis = new ExpiringRedis();
    const graceMs = 30000;
    const deps = makeDeps({ redis, graceMs });
    running = await startServer(deps);

    const a = new TestClient(running.url);
    await a.join(tokenFor('room1', 'A', { slotGeneration: 'gA' }));
    const b = new TestClient(running.url);
    const bReady = await b.join(tokenFor('room1', 'B', { slotGeneration: 'gB' }));
    expect(
      (bReady.peers as Array<{ participantId: string }>).map((p) => p.participantId)
    ).toEqual(['A']);
    await a.waitForType('participant-joined');

    b.ws.terminate();
    await a.waitForType('participant-unreachable');

    redis.advance(graceMs);
    await vi.advanceTimersByTimeAsync(graceMs);

    const left = await a.waitForType('participant-left');
    expect(left.participantId).toBe('B');
    expect(await redis.hget('sauti:room:room1', 'B')).toBeNull();

    const b2 = new TestClient(running.url);
    const b2Ready = await b2.join(tokenFor('room1', 'B', { slotGeneration: 'gB' }));
    const peers = (b2Ready.peers as Array<{ participantId: string }>).map(
      (p) => p.participantId
    );
    const rejoinNotice = await a.waitForType('participant-joined');

    expect(b2Ready.type).toBe('ready');
    expect(b2Ready.resumed).toBe(false);
    expect(peers).toEqual(['A']);
    expect((rejoinNotice.participant as { participantId: string }).participantId).toBe(
      'B'
    );

    a.close();
    b2.close();
  });

  it('resumes B when it reconnects during grace and never tells A that B left', async () => {
    vi.useFakeTimers({
      toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval']
    });
    const redis = new ExpiringRedis();
    const graceMs = 30000;
    const deps = makeDeps({ redis, graceMs });
    running = await startServer(deps);

    const a = new TestClient(running.url);
    await a.join(tokenFor('room1', 'A', { slotGeneration: 'gA' }));
    const b = new TestClient(running.url);
    await b.join(tokenFor('room1', 'B', { slotGeneration: 'gB' }));
    await a.waitForType('participant-joined');

    b.ws.terminate();
    await a.waitForType('participant-unreachable');

    redis.advance(graceMs / 3);
    await vi.advanceTimersByTimeAsync(graceMs / 3);

    const b2 = new TestClient(running.url);
    const b2Ready = await b2.join(tokenFor('room1', 'B', { slotGeneration: 'gB' }));
    expect(b2Ready.resumed).toBe(true);
    const peers = (b2Ready.peers as Array<{ participantId: string }>).map(
      (p) => p.participantId
    );
    expect(peers).toEqual(['A']);

    redis.advance(graceMs);
    await vi.advanceTimersByTimeAsync(graceMs);

    expect(a.received('participant-left')).toBe(false);
    expect(a.received('participant-joined')).toBe(false);

    a.close();
    b2.close();
  });
});
