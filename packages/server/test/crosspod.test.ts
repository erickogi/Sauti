import { describe, it, expect } from 'vitest';
import { FakeRedisPort } from './fakeRedis.js';
import { makeDeps, tokenFor } from './deps.js';
import { startServer, stopServer, TestClient, delay } from './harness.js';

describe('cross-pod relay and presence', () => {
  it('exchanges offers and presence between peers on different instances via pub/sub', async () => {
    const redis = new FakeRedisPort();
    const pod1 = await startServer(makeDeps({ redis }));
    const pod2 = await startServer(makeDeps({ redis }));

    const a = new TestClient(pod1.url);
    await a.join(tokenFor('room1', 'A'));
    const b = new TestClient(pod2.url);
    const readyB = await b.join(tokenFor('room1', 'B'));

    const peersB = readyB.peers as Array<{ participantId: string }>;
    expect(peersB.map((p) => p.participantId)).toEqual(['A']);

    const joinedOnPod1 = await a.waitForType('participant-joined');
    expect((joinedOnPod1.participant as { participantId: string }).participantId).toBe(
      'B'
    );

    a.send({ v: 1, type: 'offer', to: 'B', sdp: 'CROSSPOD' });
    const offer = await b.waitForType('offer');
    expect(offer.from).toBe('A');
    expect(offer.sdp).toBe('CROSSPOD');

    expect(redis.subs.has('sauti:room:room1')).toBe(true);

    a.close();
    b.close();
    await stopServer(pod1);
    await stopServer(pod2);
  });

  it('subscribes to and unsubscribes from the namespaced room channel with no leak', async () => {
    const redis = new FakeRedisPort();
    const pod = await startServer(makeDeps({ redis }));
    const a = new TestClient(pod.url);
    await a.join(tokenFor('roomZ', 'A'));
    expect(redis.subs.get('sauti:room:roomZ')?.size).toBe(1);
    a.send({ v: 1, type: 'leave' });
    await delay(30);
    expect(redis.subs.has('sauti:room:roomZ')).toBe(false);
    await stopServer(pod);
  });

  it('delivers a cross-pod participant-left when a remote peer leaves', async () => {
    const redis = new FakeRedisPort();
    const pod1 = await startServer(makeDeps({ redis }));
    const pod2 = await startServer(makeDeps({ redis }));
    const a = new TestClient(pod1.url);
    await a.join(tokenFor('room1', 'A'));
    const b = new TestClient(pod2.url);
    await b.join(tokenFor('room1', 'B'));
    await a.waitForType('participant-joined');
    b.send({ v: 1, type: 'leave' });
    const left = await a.waitForType('participant-left');
    expect(left.participantId).toBe('B');
    a.close();
    await stopServer(pod1);
    await stopServer(pod2);
  });

  it('keeps the surviving pod working when the other pod closes mid-call', async () => {
    const redis = new FakeRedisPort();
    const pod1 = await startServer(makeDeps({ redis }));
    const pod2 = await startServer(makeDeps({ redis }));
    const a = new TestClient(pod1.url);
    await a.join(tokenFor('room1', 'A'));
    const b = new TestClient(pod2.url);
    await b.join(tokenFor('room1', 'B'));
    const c = new TestClient(pod2.url);
    await c.join(tokenFor('room1', 'C'));
    await b.waitForType('participant-joined');

    await stopServer(pod1);

    b.send({ v: 1, type: 'offer', to: 'C', sdp: 'STILL-UP' });
    const offer = await c.waitForType('offer');
    expect(offer.from).toBe('B');
    const slot = await redis.hget('sauti:room:room1', 'A');
    expect(slot).not.toBeNull();
    b.close();
    c.close();
    await stopServer(pod2);
  });
});
