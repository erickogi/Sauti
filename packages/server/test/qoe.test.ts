import { describe, it, expect, afterEach } from 'vitest';
import { makeDeps, tokenFor } from './deps.js';
import { startServer, stopServer, TestClient, delay, type RunningServer } from './harness.js';

const validQoe = {
  v: 1,
  type: 'qoe',
  samples: 30,
  avgRttMs: 120,
  maxRttMs: 400,
  avgJitterMs: 15,
  maxJitterMs: 60,
  avgLoss: 0.02,
  maxLoss: 0.1,
  secGood: 50,
  secDegraded: 8,
  secPoor: 2,
  worst: 'degraded'
};

describe('qoe frame handling', () => {
  let running: RunningServer | undefined;

  afterEach(async () => {
    if (running) await stopServer(running);
    running = undefined;
  });

  it('accepts a valid qoe frame and emits a qoe event for the sender', async () => {
    const deps = makeDeps();
    running = await startServer(deps);
    const a = new TestClient(running.url);
    await a.join(tokenFor('room1', 'A'));

    a.send(validQoe);
    await delay(50);

    const qoe = deps.events.find((e) => e.type === 'qoe');
    expect(qoe).toBeDefined();
    expect(qoe!.participantId).toBe('A');
    expect(qoe!.data).toMatchObject({ samples: 30, worst: 'degraded', maxRttMs: 400 });
    a.close();
  });

  it('drops a malformed qoe frame without emitting a qoe event', async () => {
    const deps = makeDeps();
    running = await startServer(deps);
    const a = new TestClient(running.url);
    await a.join(tokenFor('room1', 'A'));

    a.send({ ...validQoe, avgLoss: 5 });
    await delay(50);

    expect(deps.events.some((e) => e.type === 'qoe')).toBe(false);
    expect(
      deps.events.some(
        (e) => e.type === 'relay.frame_dropped' && e.data?.reason === 'qoe_invalid'
      )
    ).toBe(true);
    a.close();
  });

  it('rate-limits qoe to one accepted frame per window', async () => {
    const deps = makeDeps();
    running = await startServer(deps);
    const a = new TestClient(running.url);
    await a.join(tokenFor('room1', 'A'));

    a.send(validQoe);
    a.send(validQoe);
    await delay(50);

    expect(deps.events.filter((e) => e.type === 'qoe')).toHaveLength(1);
    expect(
      deps.events.some(
        (e) => e.type === 'relay.frame_dropped' && e.data?.reason === 'qoe_rate_limited'
      )
    ).toBe(true);
    a.close();
  });
});
