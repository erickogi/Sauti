import { describe, it, expect, afterEach } from 'vitest';
import { makeDeps, tokenFor } from './deps.js';
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

describe('call duration anchor', () => {
  it('leaves startedAt null for a solo first participant', async () => {
    running = await startServer(makeDeps());
    const a = new TestClient(running.url);
    const ready = await a.join(tokenFor('room1', 'A'));
    expect((ready.room as { startedAt: number | null }).startedAt).toBeNull();
    a.close();
  });

  it('stamps startedAt once when the second participant connects and broadcasts room-started', async () => {
    running = await startServer(makeDeps());
    const before = Date.now();
    const a = new TestClient(running.url);
    await a.join(tokenFor('room1', 'A'));
    const b = new TestClient(running.url);
    const readyB = await b.join(tokenFor('room1', 'B'));
    const roomStarted = await a.waitForType('room-started');
    const stamped = roomStarted.startedAt as number;
    expect(stamped).toBeGreaterThanOrEqual(before);
    expect(stamped).toBeLessThanOrEqual(Date.now());
    expect((readyB.room as { startedAt: number }).startedAt).toBe(stamped);
    a.close();
    b.close();
  });

  it('never re-stamps startedAt on a third join or a reconnect', async () => {
    running = await startServer(makeDeps({ graceMs: 5000 }));
    const a = new TestClient(running.url);
    await a.join(tokenFor('room1', 'A', { slotGeneration: 'gA' }));
    const b = new TestClient(running.url);
    await b.join(tokenFor('room1', 'B', { slotGeneration: 'gB' }));
    const roomStarted = await a.waitForType('room-started');
    const captured = roomStarted.startedAt as number;

    const c = new TestClient(running.url);
    const readyC = await c.join(tokenFor('room1', 'C', { slotGeneration: 'gC' }));
    expect((readyC.room as { startedAt: number }).startedAt).toBe(captured);

    b.ws.terminate();
    await delay(40);
    const b2 = new TestClient(running.url);
    const readyB2 = await b2.join(tokenFor('room1', 'B', { slotGeneration: 'gB' }));
    expect(readyB2.resumed).toBe(true);
    expect((readyB2.room as { startedAt: number }).startedAt).toBe(captured);
    a.close();
    c.close();
    b2.close();
  });

  it('includes serverNow approximating wall clock in every ready', async () => {
    running = await startServer(makeDeps());
    const a = new TestClient(running.url);
    const before = Date.now();
    const ready = await a.join(tokenFor('room1', 'A'));
    const after = Date.now();
    expect(typeof ready.serverNow).toBe('number');
    expect(ready.serverNow as number).toBeGreaterThanOrEqual(before);
    expect(ready.serverNow as number).toBeLessThanOrEqual(after);
    a.close();
  });

  it('emits room.started exactly once', async () => {
    const deps = makeDeps();
    running = await startServer(deps);
    const a = new TestClient(running.url);
    await a.join(tokenFor('room1', 'A'));
    const b = new TestClient(running.url);
    await b.join(tokenFor('room1', 'B'));
    const c = new TestClient(running.url);
    await c.join(tokenFor('room1', 'C'));
    await delay(30);
    expect(deps.events.filter((e) => e.type === 'room.started').length).toBe(1);
    a.close();
    b.close();
    c.close();
  });
});
