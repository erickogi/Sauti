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

describe('participant-joined ordering on rejoin', () => {
  it('delivers participant-joined before any relayed offer when a left peer rejoins', async () => {
    running = await startServer(makeDeps());

    const a = new TestClient(running.url);
    const order: string[] = [];
    a.ws.on('message', (data) => {
      order.push((JSON.parse(data.toString()) as { type: string }).type);
    });
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
    await delay(30);

    const offerIndex = order.indexOf('offer');
    const lastJoinedIndex = order.lastIndexOf('participant-joined');
    expect(offerIndex).toBeGreaterThan(-1);
    expect(order.filter((t) => t === 'participant-joined')).toHaveLength(2);
    expect(lastJoinedIndex).toBeLessThan(offerIndex);

    a.close();
    b2.close();
  });
});
