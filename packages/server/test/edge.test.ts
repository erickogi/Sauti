import { describe, it, expect, afterEach } from 'vitest';
import * as api from '../src/index.js';
import { createSautiServer } from '../src/server.js';
import { FakeRedisPort } from './fakeRedis.js';
import { makeDeps, tokenFor, turn } from './deps.js';
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

describe('package barrel', () => {
  it('exports the public server surface', () => {
    expect(typeof api.createSautiServer).toBe('function');
    expect(typeof api.mintIceServers).toBe('function');
    expect(typeof api.encodeSlot).toBe('function');
    expect(typeof api.decodeSlot).toBe('function');
    expect(api.CLAIM_SLOT).toContain('claim');
  });
});

describe('server without an event sink', () => {
  it('runs a full join and leave when onEvent is not provided', async () => {
    const redis = new FakeRedisPort();
    const http = (await import('node:http')).createServer();
    const server = createSautiServer({
      redis,
      turn,
      namespace: 'sauti',
      requireOrigin: false,
      authorize: async (token) => JSON.parse(token)
    });
    server.attach(http);
    await new Promise<void>((resolve) => http.listen(0, '127.0.0.1', resolve));
    const addr = http.address();
    const port = typeof addr === 'object' && addr ? addr.port : 0;
    const url = `ws://127.0.0.1:${port}`;
    const a = new TestClient(url, {});
    const ready = await a.join(tokenFor('room1', 'A'));
    expect(ready.type).toBe('ready');
    a.send({ v: 1, type: 'leave' });
    await delay(20);
    a.close();
    await server.close();
    await new Promise<void>((resolve) => http.close(() => resolve()));
  });
});

describe('resilience to odd inbound frames and lost slots', () => {
  it('ignores a non-object JSON frame after join', async () => {
    running = await startServer(makeDeps());
    const a = new TestClient(running.url);
    await a.join(tokenFor('room1', 'A'));
    a.ws.send('42');
    await delay(20);
    expect(a.closed).toBe(false);
    a.close();
  });

  it('ignores a state frame after the slot has been removed', async () => {
    const deps = makeDeps();
    running = await startServer(deps);
    const a = new TestClient(running.url);
    await a.join(tokenFor('room1', 'A'));
    const b = new TestClient(running.url);
    await b.join(tokenFor('room1', 'B'));
    await a.waitForType('participant-joined');
    await deps.redis.hdel('sauti:room:room1', 'A');
    a.send({ v: 1, type: 'state', state: { muted: true } });
    await delay(20);
    expect(b.received('participant-state')).toBe(false);
    a.close();
    b.close();
  });

  it('handles a socket drop after the slot was already gone without emitting unreachable', async () => {
    const deps = makeDeps({ graceMs: 5000 });
    running = await startServer(deps);
    const a = new TestClient(running.url);
    await a.join(tokenFor('room1', 'A'));
    const b = new TestClient(running.url);
    await b.join(tokenFor('room1', 'B'));
    await a.waitForType('participant-joined');
    await deps.redis.hdel('sauti:room:room1', 'A');
    a.ws.terminate();
    await delay(40);
    expect(b.received('participant-unreachable')).toBe(false);
    b.close();
  });

  it('treats a second leave frame as a no-op', async () => {
    running = await startServer(makeDeps());
    const a = new TestClient(running.url);
    await a.join(tokenFor('room1', 'A'));
    const b = new TestClient(running.url);
    await b.join(tokenFor('room1', 'B'));
    await a.waitForType('participant-joined');
    b.send({ v: 1, type: 'leave' });
    b.send({ v: 1, type: 'leave' });
    const left = await a.waitForType('participant-left');
    expect(left.participantId).toBe('B');
    await delay(20);
    a.close();
  });
});

describe('cross-pod grace sweep after a remote resume', () => {
  it('keeps the slot when a stale sweep timer fires after the participant resumed on another pod', async () => {
    const redis = new FakeRedisPort();
    const pod1 = await startServer(makeDeps({ redis, graceMs: 150 }));
    const pod2 = await startServer(makeDeps({ redis, graceMs: 150 }));
    const a = new TestClient(pod1.url);
    await a.join(tokenFor('room1', 'A', { slotGeneration: 'gA' }));
    a.ws.terminate();
    await delay(30);
    const a2 = new TestClient(pod2.url);
    const ready = await a2.join(tokenFor('room1', 'A', { slotGeneration: 'gA' }));
    expect(ready.resumed).toBe(true);
    await delay(200);
    const slot = await redis.hget('sauti:room:room1', 'A');
    expect(slot).not.toBeNull();
    a2.close();
    await stopServer(pod1);
    await stopServer(pod2);
  });
});
