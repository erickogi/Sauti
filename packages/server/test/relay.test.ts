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

async function threeParty(): Promise<{
  a: TestClient;
  b: TestClient;
  c: TestClient;
}> {
  const a = new TestClient(running!.url);
  await a.join(tokenFor('room1', 'A'));
  const b = new TestClient(running!.url);
  await b.join(tokenFor('room1', 'B'));
  const c = new TestClient(running!.url);
  await c.join(tokenFor('room1', 'C'));
  await a.waitForType('participant-joined');
  await a.waitForType('participant-joined');
  await b.waitForType('participant-joined');
  return { a, b, c };
}

describe('addressed relay routing', () => {
  it('delivers an offer only to the named peer, stamps from, and removes to', async () => {
    running = await startServer(makeDeps());
    const { a, b, c } = await threeParty();
    a.send({ v: 1, type: 'offer', to: 'B', sdp: 'SDP-A' });
    const received = await b.waitForType('offer');
    expect(received.from).toBe('A');
    expect(received.sdp).toBe('SDP-A');
    expect('to' in received).toBe(false);
    await delay(30);
    expect(c.received('offer')).toBe(false);
    a.close();
    b.close();
    c.close();
  });

  it('drops an inbound frame carrying a client-supplied from rather than trusting it', async () => {
    const deps = makeDeps();
    running = await startServer(deps);
    const { a, b, c } = await threeParty();
    a.send({ v: 1, type: 'offer', to: 'B', sdp: 'SDP', from: 'someoneElse' });
    await delay(30);
    expect(b.received('offer')).toBe(false);
    expect(
      deps.events.some(
        (e) =>
          e.type === 'relay.frame_dropped' &&
          e.data?.reason === 'unexpected_field'
      )
    ).toBe(true);
    a.close();
    b.close();
    c.close();
  });

  it('delivers with the server-stamped from after the spoofed frame is rejected', async () => {
    running = await startServer(makeDeps());
    const { a, b, c } = await threeParty();
    a.send({ v: 1, type: 'offer', to: 'B', sdp: 'clean' });
    const received = await b.waitForType('offer');
    expect(received.from).toBe('A');
    expect('to' in received).toBe(false);
    a.close();
    b.close();
    c.close();
  });

  it('relays answer and ice with the same routing rules', async () => {
    running = await startServer(makeDeps());
    const { a, b, c } = await threeParty();
    b.send({ v: 1, type: 'answer', to: 'A', sdp: 'ANS' });
    const answer = await a.waitForType('answer');
    expect(answer.from).toBe('B');
    expect(answer.sdp).toBe('ANS');
    a.send({
      v: 1,
      type: 'ice',
      to: 'C',
      candidate: { candidate: 'cand', sdpMid: 'audio', sdpMLineIndex: 0 }
    });
    const ice = await c.waitForType('ice');
    expect(ice.from).toBe('A');
    expect((ice.candidate as { candidate: string }).candidate).toBe('cand');
    a.close();
    b.close();
    c.close();
  });
});

describe('duplicate and out-of-order relay', () => {
  it('forwards duplicate offers in order without dropping or reordering them', async () => {
    running = await startServer(makeDeps());
    const { a, b, c } = await threeParty();
    a.send({ v: 1, type: 'offer', to: 'B', sdp: 'one' });
    a.send({ v: 1, type: 'offer', to: 'B', sdp: 'one' });
    a.send({ v: 1, type: 'ice', to: 'B', candidate: { candidate: 'two', sdpMid: null, sdpMLineIndex: null } });
    const first = await b.waitForType('offer');
    const second = await b.waitForType('offer');
    const third = await b.waitForType('ice');
    expect(first.sdp).toBe('one');
    expect(second.sdp).toBe('one');
    expect((third.candidate as { candidate: string }).candidate).toBe('two');
    a.close();
    b.close();
    c.close();
  });
});

describe('relay dropping', () => {
  it('drops an unknown frame type without touching room state', async () => {
    const deps = makeDeps();
    running = await startServer(deps);
    const { a, b, c } = await threeParty();
    a.send({ v: 1, type: 'whisper', to: 'B', sdp: 'x' });
    await delay(30);
    expect(b.received('whisper')).toBe(false);
    const all = await deps.redis.hgetall('sauti:room:room1');
    expect(Object.keys(all).sort()).toEqual(['A', 'B', 'C']);
    a.close();
    b.close();
    c.close();
  });

  it('drops a relay frame carrying an injected extra field', async () => {
    const deps = makeDeps();
    running = await startServer(deps);
    const { a, b, c } = await threeParty();
    a.send({ v: 1, type: 'offer', to: 'B', sdp: 'x', evil: true });
    await delay(30);
    expect(b.received('offer')).toBe(false);
    expect(
      deps.events.some(
        (e) =>
          e.type === 'relay.frame_dropped' &&
          e.data?.reason === 'unexpected_field'
      )
    ).toBe(true);
    a.close();
    b.close();
    c.close();
  });

  it('drops an offer whose to is not a current member', async () => {
    running = await startServer(makeDeps());
    const { a, b, c } = await threeParty();
    a.send({ v: 1, type: 'offer', to: 'ghost', sdp: 'x' });
    await delay(30);
    expect(b.received('offer')).toBe(false);
    expect(c.received('offer')).toBe(false);
    a.close();
    b.close();
    c.close();
  });

  it('drops an ice frame with a malformed candidate', async () => {
    running = await startServer(makeDeps());
    const { a, b, c } = await threeParty();
    a.send({ v: 1, type: 'ice', to: 'B', candidate: { candidate: 'x' } });
    await delay(30);
    expect(b.received('ice')).toBe(false);
    a.close();
    b.close();
    c.close();
  });

  it('drops a relay frame whose to is not a string', async () => {
    running = await startServer(makeDeps());
    const { a, b, c } = await threeParty();
    a.send({ v: 1, type: 'offer', to: 123, sdp: 'x' });
    await delay(30);
    expect(b.received('offer')).toBe(false);
    a.close();
    b.close();
    c.close();
  });

  it('drops an offer with a non-string sdp', async () => {
    running = await startServer(makeDeps());
    const { a, b, c } = await threeParty();
    a.send({ v: 1, type: 'offer', to: 'B', sdp: 42 });
    await delay(30);
    expect(b.received('offer')).toBe(false);
    a.close();
    b.close();
    c.close();
  });

  it('drops a mid-call frame with a mismatched version', async () => {
    const deps = makeDeps();
    running = await startServer(deps);
    const { a, b, c } = await threeParty();
    a.send({ v: 2, type: 'offer', to: 'B', sdp: 'x' });
    await delay(30);
    expect(b.received('offer')).toBe(false);
    expect(
      deps.events.some((e) => e.data?.reason === 'version_mismatch')
    ).toBe(true);
    a.close();
    b.close();
    c.close();
  });

  it('drops a mid-call frame that omits the version entirely', async () => {
    const deps = makeDeps();
    running = await startServer(deps);
    const { a, b, c } = await threeParty();
    a.send({ type: 'offer', to: 'B', sdp: 'x' });
    await delay(30);
    expect(b.received('offer')).toBe(false);
    expect(
      deps.events.some((e) => e.data?.reason === 'version_mismatch')
    ).toBe(true);
    a.close();
    b.close();
    c.close();
  });
});
