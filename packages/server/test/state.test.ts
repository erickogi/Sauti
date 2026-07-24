import { describe, it, expect, afterEach } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { makeDeps, tokenFor } from './deps.js';
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

describe('mute and hold state', () => {
  it('applies a mute to stored state and broadcasts changed keys to other members', async () => {
    const deps = makeDeps();
    running = await startServer(deps);
    const a = new TestClient(running.url);
    await a.join(tokenFor('room1', 'A'));
    const b = new TestClient(running.url);
    await b.join(tokenFor('room1', 'B'));
    const c = new TestClient(running.url);
    await c.join(tokenFor('room1', 'C'));
    await a.waitForType('participant-joined');
    await a.waitForType('participant-joined');

    a.send({ v: 1, type: 'state', state: { muted: true } });
    const bState = await b.waitForType('participant-state');
    const cState = await c.waitForType('participant-state');
    expect(bState.participantId).toBe('A');
    expect(bState.state).toEqual({ muted: true });
    expect(cState.state).toEqual({ muted: true });
    await delay(20);

    const slot = await deps.redis.hget('sauti:room:room1', 'A');
    expect(decodeSlot(slot!).state.muted).toBe(true);
    a.close();
    b.close();
    c.close();
  });

  it('does not echo the state change back to the sender', async () => {
    running = await startServer(makeDeps());
    const a = new TestClient(running.url);
    await a.join(tokenFor('room1', 'A'));
    const b = new TestClient(running.url);
    await b.join(tokenFor('room1', 'B'));
    await a.waitForType('participant-joined');
    a.send({ v: 1, type: 'state', state: { muted: true } });
    await b.waitForType('participant-state');
    await delay(20);
    expect(a.received('participant-state')).toBe(false);
    a.close();
    b.close();
  });

  it('persists mute so a later joiner sees it in ready.peers', async () => {
    running = await startServer(makeDeps());
    const a = new TestClient(running.url);
    await a.join(tokenFor('room1', 'A'));
    a.send({ v: 1, type: 'state', state: { muted: true } });
    await delay(20);
    const c = new TestClient(running.url);
    const ready = await c.join(tokenFor('room1', 'C'));
    const peers = ready.peers as Array<{
      participantId: string;
      state: { muted: boolean; onHold: boolean };
    }>;
    const peerA = peers.find((p) => p.participantId === 'A');
    expect(peerA?.state.muted).toBe(true);
    a.close();
    c.close();
  });

  it('routes onHold through the same relay and persistence path', async () => {
    running = await startServer(makeDeps());
    const a = new TestClient(running.url);
    await a.join(tokenFor('room1', 'A'));
    const b = new TestClient(running.url);
    await b.join(tokenFor('room1', 'B'));
    await a.waitForType('participant-joined');
    a.send({ v: 1, type: 'state', state: { onHold: true } });
    const bState = await b.waitForType('participant-state');
    expect(bState.state).toEqual({ onHold: true });
    await delay(20);
    const c = new TestClient(running.url);
    const ready = await c.join(tokenFor('room1', 'C'));
    const peers = ready.peers as Array<{
      participantId: string;
      state: { onHold: boolean };
    }>;
    expect(peers.find((p) => p.participantId === 'A')?.state.onHold).toBe(true);
    a.close();
    b.close();
    c.close();
  });

  it('carries only the changed key when a single field toggles', async () => {
    running = await startServer(makeDeps());
    const a = new TestClient(running.url);
    await a.join(tokenFor('room1', 'A'));
    const b = new TestClient(running.url);
    await b.join(tokenFor('room1', 'B'));
    await a.waitForType('participant-joined');
    a.send({ v: 1, type: 'state', state: { muted: true, onHold: true } });
    const first = await b.waitForType('participant-state');
    expect(first.state).toEqual({ muted: true, onHold: true });
    a.send({ v: 1, type: 'state', state: { muted: false } });
    const second = await b.waitForType('participant-state');
    expect(second.state).toEqual({ muted: false });
    a.close();
    b.close();
  });

  it('ignores a state frame that carries no known keys', async () => {
    const deps = makeDeps();
    running = await startServer(deps);
    const a = new TestClient(running.url);
    await a.join(tokenFor('room1', 'A'));
    const b = new TestClient(running.url);
    await b.join(tokenFor('room1', 'B'));
    await a.waitForType('participant-joined');
    a.send({ v: 1, type: 'state', state: {} });
    await delay(20);
    expect(b.received('participant-state')).toBe(false);
    a.close();
    b.close();
  });

  it('drops a state frame that injects a participantId', async () => {
    const deps = makeDeps();
    running = await startServer(deps);
    const a = new TestClient(running.url);
    await a.join(tokenFor('room1', 'A'));
    const b = new TestClient(running.url);
    await b.join(tokenFor('room1', 'B'));
    await a.waitForType('participant-joined');
    a.send({ v: 1, type: 'state', state: { muted: true }, participantId: 'B' });
    await delay(20);
    expect(b.received('participant-state')).toBe(false);
    a.close();
    b.close();
  });
});

describe('mute is declared, never inferred', () => {
  const serverSrc = readFileSync(
    join(process.cwd(), 'src', 'server.ts'),
    'utf8'
  );

  it('reads no audio level, track, or stats signal that could infer mute', () => {
    const inference =
      /audioLevel|getStats|analyser|createMediaStream|track\.enabled|track\.muted|\.getAudioTracks/i;
    expect(inference.test(serverSrc)).toBe(false);
  });

  it('assigns muted only inside the client state-frame handler', () => {
    const assignment = /muted\s*=(?!=)/g;
    const all = [...serverSrc.matchAll(assignment)].map((m) => m.index ?? -1);
    expect(all.length).toBeGreaterThan(0);
    const start = serverSrc.indexOf('async function handleState');
    const end = serverSrc.indexOf('async function handleLeave', start);
    expect(start).toBeGreaterThan(-1);
    expect(end).toBeGreaterThan(start);
    for (const idx of all) {
      expect(idx).toBeGreaterThan(start);
      expect(idx).toBeLessThan(end);
    }
  });
});
