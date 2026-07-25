import { describe, it, expect, afterEach, vi } from 'vitest';
import { makeDeps, tokenFor } from './deps.js';
import { FakeRedisPort } from './fakeRedis.js';
import {
  startServer,
  stopServer,
  TestClient,
  delay,
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

describe('leave then rejoin the same room', () => {
  it('gives the rejoiner a peer list with the stayer after a long call', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
    const redis = new ExpiringRedis();
    const deps = makeDeps({ redis, graceMs: 5000 });
    running = await startServer(deps);

    const a = new TestClient(running.url);
    await a.join(tokenFor('room1', 'A', { slotGeneration: 'gA' }));
    const b = new TestClient(running.url);
    const bReady = await b.join(tokenFor('room1', 'B', { slotGeneration: 'gB' }));
    expect(
      (bReady.peers as Array<{ participantId: string }>).map((p) => p.participantId)
    ).toEqual(['A']);
    await a.waitForType('participant-joined');

    for (let i = 0; i < 3; i += 1) {
      redis.advance(30000);
      await vi.advanceTimersByTimeAsync(30000);
    }

    b.send({ v: 1, type: 'leave' });
    await a.waitForType('participant-left');
    await b.waitClosed();

    const b2 = new TestClient(running.url);
    const b2Ready = await b2.join(tokenFor('room1', 'B', { slotGeneration: 'gB' }));
    const peers = (b2Ready.peers as Array<{ participantId: string }>).map(
      (p) => p.participantId
    );
    const rejoinNotice = await a.waitForType('participant-joined', 1000);

    expect((rejoinNotice.participant as { participantId: string }).participantId).toBe('B');
    expect(peers).toEqual(['A']);

    a.close();
    b2.close();
  });
});
