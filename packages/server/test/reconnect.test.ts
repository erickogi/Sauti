import { describe, it, expect, afterEach } from 'vitest';
import { makeDeps, tokenFor } from './deps.js';
import { FakeRedisPort } from './fakeRedis.js';
import { decodeSlot } from '../src/slot.js';
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
});

describe('drop without eager release', () => {
  it('marks the participant unreachable, holds the slot, and broadcasts participant-unreachable', async () => {
    const deps = makeDeps({ graceMs: 5000 });
    running = await startServer(deps);
    const a = new TestClient(running.url);
    await a.join(tokenFor('room1', 'A', { slotGeneration: 'gA' }));
    const b = new TestClient(running.url);
    await b.join(tokenFor('room1', 'B', { slotGeneration: 'gB' }));
    await a.waitForType('participant-joined');

    a.ws.terminate();
    const unreachable = await b.waitForType('participant-unreachable');
    expect(unreachable.participantId).toBe('A');
    expect(b.received('participant-left')).toBe(false);

    const slot = await deps.redis.hget('sauti:room:room1', 'A');
    expect(slot).not.toBeNull();
    expect(decodeSlot(slot!).connectionState).toBe('unreachable');
    b.close();
  });
});

describe('resume within grace', () => {
  it('reclaims the same slot with resumed:true and does not re-announce the resumer', async () => {
    const deps = makeDeps({ graceMs: 5000 });
    running = await startServer(deps);
    const a = new TestClient(running.url);
    await a.join(tokenFor('room1', 'A', { slotGeneration: 'gA' }));
    const b = new TestClient(running.url);
    await b.join(tokenFor('room1', 'B', { slotGeneration: 'gB' }));
    const c = new TestClient(running.url);
    await c.join(tokenFor('room1', 'C', { slotGeneration: 'gC' }));
    await a.waitForType('participant-joined');
    await a.waitForType('participant-joined');
    await b.waitForType('participant-joined');

    c.ws.terminate();
    await a.waitForType('participant-unreachable');
    await b.waitForType('participant-unreachable');
    await delay(20);

    const c2 = new TestClient(running.url);
    const ready = await c2.join(tokenFor('room1', 'C', { slotGeneration: 'gC' }));
    expect(ready.resumed).toBe(true);
    const peers = ready.peers as Array<{ participantId: string }>;
    expect(peers.map((p) => p.participantId).sort()).toEqual(['A', 'B']);

    await delay(40);
    expect(a.received('participant-joined')).toBe(false);
    expect(b.received('participant-joined')).toBe(false);
    a.close();
    b.close();
    c2.close();
  });

  it('carries current peer state into the resumed snapshot', async () => {
    const deps = makeDeps({ graceMs: 5000 });
    running = await startServer(deps);
    const a = new TestClient(running.url);
    await a.join(tokenFor('room1', 'A', { slotGeneration: 'gA' }));
    const b = new TestClient(running.url);
    await b.join(tokenFor('room1', 'B', { slotGeneration: 'gB' }));
    await a.waitForType('participant-joined');
    a.send({ v: 1, type: 'state', state: { muted: true } });
    await b.waitForType('participant-state');
    await delay(20);

    b.ws.terminate();
    await a.waitForType('participant-unreachable');
    await delay(20);
    const b2 = new TestClient(running.url);
    const ready = await b2.join(tokenFor('room1', 'B', { slotGeneration: 'gB' }));
    expect(ready.resumed).toBe(true);
    const peers = ready.peers as Array<{
      participantId: string;
      state: { muted: boolean };
    }>;
    expect(peers.find((p) => p.participantId === 'A')?.state.muted).toBe(true);
    a.close();
    b2.close();
  });

  it('treats a stale slotGeneration as not-a-resume and does not hijack the slot', async () => {
    const deps = makeDeps({ graceMs: 5000 });
    running = await startServer(deps);
    const a = new TestClient(running.url);
    await a.join(tokenFor('room1', 'A', { slotGeneration: 'gA' }));
    const b = new TestClient(running.url);
    await b.join(tokenFor('room1', 'B', { slotGeneration: 'gB' }));
    await a.waitForType('participant-joined');

    a.ws.terminate();
    await b.waitForType('participant-unreachable');
    await delay(20);

    const aStale = new TestClient(running.url);
    const res = await aStale.join(tokenFor('room1', 'A', { slotGeneration: 'stale' }));
    expect(res.type).toBe('error');
    expect(res.code).toBe('slot_conflict');
    const slot = await deps.redis.hget('sauti:room:room1', 'A');
    expect(decodeSlot(slot!).generation).toBe('gA');
    b.close();
  });

  it('falls through to a fresh join when a concurrent sweep already won the slot', async () => {
    class SweptRedis extends FakeRedisPort {
      forceGoneOnce = true;
      override async eval(
        script: string,
        keys: string[],
        args: string[]
      ): Promise<unknown> {
        if (/local op = 'reclaim'/.test(script) && this.forceGoneOnce) {
          this.forceGoneOnce = false;
          await this.hdel(keys[0]!, args[0]!);
          return 'gone';
        }
        return super.eval(script, keys, args);
      }
    }
    const redis = new SweptRedis();
    const deps = makeDeps({ redis, graceMs: 5000 });
    running = await startServer(deps);
    const a = new TestClient(running.url);
    await a.join(tokenFor('room1', 'A', { slotGeneration: 'gA' }));
    a.ws.terminate();
    await delay(30);
    const a2 = new TestClient(running.url);
    const ready = await a2.join(tokenFor('room1', 'A', { slotGeneration: 'gA' }));
    expect(ready.type).toBe('ready');
    expect(ready.resumed).toBe(false);
    a2.close();
  });
});

describe('grace expiry', () => {
  it('releases the slot and broadcasts participant-left after graceMs with no reconnect', async () => {
    const deps = makeDeps({ graceMs: 60 });
    running = await startServer(deps);
    const a = new TestClient(running.url);
    await a.join(tokenFor('room1', 'A', { slotGeneration: 'gA' }));
    const b = new TestClient(running.url);
    await b.join(tokenFor('room1', 'B', { slotGeneration: 'gB' }));
    await a.waitForType('participant-joined');

    a.ws.terminate();
    await b.waitForType('participant-unreachable');
    const left = await b.waitForType('participant-left', 1000);
    expect(left.participantId).toBe('A');
    const slot = await deps.redis.hget('sauti:room:room1', 'A');
    expect(slot).toBeNull();
    b.close();
  });

  it('uses a default graceMs of 30000', async () => {
    const src = await import('node:fs').then((fs) =>
      fs.readFileSync(
        new URL('../src/server.ts', import.meta.url),
        'utf8'
      )
    );
    expect(/DEFAULT_GRACE_MS\s*=\s*30000/.test(src)).toBe(true);
  });
});
