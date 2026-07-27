import { describe, it, expect, afterEach } from 'vitest';
import { makeDeps, tokenFor } from './deps.js';
import { startServer, stopServer, TestClient, type RunningServer } from './harness.js';

let running: RunningServer | undefined;

afterEach(async () => {
  if (running) await stopServer(running);
  running = undefined;
});

describe('heartbeat', () => {
  it('drops a peer whose device stops answering pings, then times it out of the room', async () => {
    const deps = makeDeps({ graceMs: 150, heartbeatMs: 40 });
    running = await startServer(deps);

    const a = new TestClient(running.url);
    await a.join(tokenFor('room1', 'A', { slotGeneration: 'gA' }));
    const b = new TestClient(running.url);
    await b.join(tokenFor('room1', 'B', { slotGeneration: 'gB' }));
    await a.waitForType('participant-joined');

    const socket = (a.ws as unknown as { _socket?: { pause: () => void } })._socket;
    socket?.pause();

    const unreachable = await b.waitForType('participant-unreachable', 2000);
    expect(unreachable.participantId).toBe('A');

    const left = await b.waitForType('participant-left', 2000);
    expect(left.participantId).toBe('A');

    expect(
      deps.events.some((e) => e.type === 'participant.unreachable' && e.participantId === 'A'),
    ).toBe(true);

    b.close();
  });

  it('keeps a healthy peer connected across heartbeat cycles', async () => {
    const deps = makeDeps({ graceMs: 5000, heartbeatMs: 40 });
    running = await startServer(deps);

    const a = new TestClient(running.url);
    await a.join(tokenFor('room1', 'A', { slotGeneration: 'gA' }));
    const b = new TestClient(running.url);
    await b.join(tokenFor('room1', 'B', { slotGeneration: 'gB' }));
    await a.waitForType('participant-joined');

    await new Promise((resolve) => setTimeout(resolve, 200));

    expect(a.closed).toBe(false);
    expect(b.closed).toBe(false);
    expect(b.received('participant-unreachable')).toBe(false);

    a.close();
    b.close();
  });
});
