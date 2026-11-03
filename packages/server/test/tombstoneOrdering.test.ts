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

  override async eval(script: string, keys: string[], args: string[]): Promise<unknown> {
    this.purge();
    return super.eval(script, keys, args);
  }
}

function recordFrameTypes(client: TestClient): string[] {
  const order: string[] = [];
  client.ws.on('message', (data) => {
    order.push((JSON.parse(data.toString()) as { type: string }).type);
  });
  return order;
}

describe('participant-joined ordering on rejoin', () => {
  it('delivers participant-joined before the relayed offer on an explicit-leave rejoin', async () => {
    running = await startServer(makeDeps());

    const a = new TestClient(running.url);
    const order = recordFrameTypes(a);
    await a.join(tokenFor('room1', 'A', { slotGeneration: 'gA' }));

    const b = new TestClient(running.url);
    await b.join(tokenFor('room1', 'B', { slotGeneration: 'gB1' }));
    await a.waitForType('participant-joined');

    b.send({ v: 1, type: 'leave' });
    await a.waitForType('participant-left');
    await b.waitClosed();

    const b2 = new TestClient(running.url);
    await b2.join(tokenFor('room1', 'B', { slotGeneration: 'gB2' }));
    b2.send({ v: 1, type: 'offer', to: 'A', sdp: 'rejoin-offer' });
    await a.waitForType('offer');

    const offerIndex = order.indexOf('offer');
    const lastJoinedIndex = order.lastIndexOf('participant-joined');
    expect(order.filter((t) => t === 'participant-joined')).toHaveLength(2);
    expect(offerIndex).toBeGreaterThan(-1);
    expect(lastJoinedIndex).toBeLessThan(offerIndex);

    a.close();
    b2.close();
  });

  it('delivers participant-joined before the relayed offer after a grace-sweep rejoin', async () => {
    vi.useFakeTimers({
      toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval']
    });
    const redis = new ExpiringRedis();
    const graceMs = 30000;
    running = await startServer(makeDeps({ redis, graceMs }));

    const a = new TestClient(running.url);
    const order = recordFrameTypes(a);
    await a.join(tokenFor('room1', 'A', { slotGeneration: 'gA' }));

    const b = new TestClient(running.url);
    await b.join(tokenFor('room1', 'B', { slotGeneration: 'gB' }));
    await a.waitForType('participant-joined');

    b.ws.terminate();
    await a.waitForType('participant-unreachable');
    redis.advance(graceMs);
    await vi.advanceTimersByTimeAsync(graceMs);
    await a.waitForType('participant-left');

    const b2 = new TestClient(running.url);
    await b2.join(tokenFor('room1', 'B', { slotGeneration: 'gB' }));
    b2.send({ v: 1, type: 'offer', to: 'A', sdp: 'rejoin-offer' });
    await a.waitForType('offer');

    const offerIndex = order.indexOf('offer');
    const lastJoinedIndex = order.lastIndexOf('participant-joined');
    expect(offerIndex).toBeGreaterThan(-1);
    expect(lastJoinedIndex).toBeLessThan(offerIndex);

    a.close();
    b2.close();
  });
});
