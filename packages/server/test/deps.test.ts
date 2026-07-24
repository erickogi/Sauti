import { describe, it, expect, afterEach } from 'vitest';
import { createSautiServer } from '../src/server.js';
import type { RateLimiter } from '../src/types.js';
import { makeDeps, tokenFor } from './deps.js';
import { startServer, stopServer, TestClient, delay, type RunningServer } from './harness.js';

let running: RunningServer | undefined;

afterEach(async () => {
  if (running) await stopServer(running);
  running = undefined;
});

describe('namespace', () => {
  it('throws at construction when namespace is missing or empty', () => {
    expect(() =>
      createSautiServer(makeDeps({ namespace: '' }))
    ).toThrow(/namespace/);
  });

  it('prefixes every redis key and pub/sub channel with the namespace', async () => {
    const deps = makeDeps({ namespace: 'x' });
    running = await startServer(deps);
    const a = new TestClient(running.url);
    await a.join(tokenFor('room1', 'A'));
    const b = new TestClient(running.url);
    await b.join(tokenFor('room1', 'B'));
    await delay(20);
    for (const key of deps.redis.hashes.keys()) {
      expect(key.startsWith('x:')).toBe(true);
    }
    for (const channel of deps.redis.subs.keys()) {
      expect(channel.startsWith('x:')).toBe(true);
    }
    a.close();
    b.close();
  });
});

describe('authorize delegation', () => {
  it('admits a participant with the roomId, participantId, and metadata authorize returns', async () => {
    const deps = makeDeps({
      authorize: async () => ({
        roomId: 'roomZ',
        participantId: 'zed',
        metadata: { seat: 'front' }
      })
    });
    running = await startServer(deps);
    const a = new TestClient(running.url);
    const ready = await a.join('anything');
    expect((ready.self as { participantId: string }).participantId).toBe('zed');
    expect((ready.self as { metadata: Record<string, unknown> }).metadata).toEqual({
      seat: 'front'
    });
    const stored = await deps.redis.hgetall('sauti:room:roomZ');
    expect(Object.keys(stored)).toEqual(['zed']);
    a.close();
  });

  it('rejects and closes when authorize returns null', async () => {
    running = await startServer(makeDeps());
    const a = new TestClient(running.url);
    const res = await a.join('deny');
    expect(res.type).toBe('error');
    expect(res.code).toBe('unauthorized');
    await a.waitClosed();
    expect(a.closed).toBe(true);
  });
});

describe('origin policy', () => {
  it('rejects an origin not in allowedOrigins and admits one that is', async () => {
    running = await startServer(makeDeps({ allowedOrigins: ['https://a'] }));
    const evil = new TestClient(running.url, { origin: 'https://evil' });
    await expect(evil.connect()).rejects.toThrow();
    const good = new TestClient(running.url, { origin: 'https://a' });
    const ready = await good.join(tokenFor('room1', 'A'));
    expect(ready.type).toBe('ready');
    good.close();
  });

  it('defaults requireOrigin to true and rejects a missing Origin', async () => {
    running = await startServer(makeDeps());
    const noOrigin = new TestClient(running.url, {});
    await expect(noOrigin.connect()).rejects.toThrow();
  });

  it('admits a missing Origin only when requireOrigin is explicitly false', async () => {
    running = await startServer(makeDeps({ requireOrigin: false }));
    const noOrigin = new TestClient(running.url, {});
    const ready = await noOrigin.join(tokenFor('room1', 'A'));
    expect(ready.type).toBe('ready');
    noOrigin.close();
  });
});

describe('attach path option', () => {
  it('rejects an upgrade on a path other than the configured one', async () => {
    running = await startServer(makeDeps(), { path: '/ws' });
    const wrong = new TestClient(`${running.url}/nope`);
    await expect(wrong.connect()).rejects.toThrow();
    const right = new TestClient(`${running.url}/ws`);
    const ready = await right.join(tokenFor('room1', 'A'));
    expect(ready.type).toBe('ready');
    right.close();
  });
});

describe('rate limiting', () => {
  function limiter(overrides: Partial<RateLimiter>): RateLimiter {
    return {
      allowConnect: overrides.allowConnect ?? (async () => true),
      allowFrame: overrides.allowFrame ?? (async () => true)
    };
  }

  it('refuses an over-limit upgrade without allocating room state', async () => {
    let connects = 0;
    const deps = makeDeps({
      rateLimiter: limiter({
        allowConnect: async () => {
          connects += 1;
          return connects <= 1;
        }
      })
    });
    running = await startServer(deps);
    const first = new TestClient(running.url);
    await first.join(tokenFor('room1', 'A'));
    const before = await deps.redis.hgetall('sauti:room:room1');
    const second = new TestClient(running.url, { origin: 'http://localhost' });
    await expect(second.connect()).rejects.toThrow();
    await delay(20);
    const after = await deps.redis.hgetall('sauti:room:room1');
    expect(Object.keys(before)).toEqual(['A']);
    expect(after).toEqual(before);
    first.close();
  });

  it('drops a relay frame beyond the per-participant cap without tearing down the socket', async () => {
    let frames = 0;
    const deps = makeDeps({
      rateLimiter: limiter({
        allowFrame: async () => {
          frames += 1;
          return frames <= 1;
        }
      })
    });
    running = await startServer(deps);
    const a = new TestClient(running.url);
    await a.join(tokenFor('room1', 'A'));
    const b = new TestClient(running.url);
    await b.join(tokenFor('room1', 'B'));
    await a.waitForType('participant-joined');

    a.send({ v: 1, type: 'offer', to: 'B', sdp: 'first' });
    const first = await b.waitForType('offer');
    expect(first.sdp).toBe('first');
    a.send({ v: 1, type: 'offer', to: 'B', sdp: 'second' });
    await delay(30);
    expect(b.received('offer')).toBe(false);
    expect(a.closed).toBe(false);
    a.send({ v: 1, type: 'leave' });
    a.close();
    b.close();
  });

  it('drops an over-limit state frame without tearing down the socket', async () => {
    let frames = 0;
    const deps = makeDeps({
      rateLimiter: limiter({
        allowFrame: async () => {
          frames += 1;
          return false;
        }
      })
    });
    running = await startServer(deps);
    const a = new TestClient(running.url);
    await a.join(tokenFor('room1', 'A'));
    const b = new TestClient(running.url);
    await b.join(tokenFor('room1', 'B'));
    await a.waitForType('participant-joined');
    a.send({ v: 1, type: 'state', state: { muted: true } });
    await delay(30);
    expect(b.received('participant-state')).toBe(false);
    expect(frames).toBeGreaterThan(0);
    expect(a.closed).toBe(false);
    a.close();
    b.close();
  });

  it('does not gate when no rate limiter is injected', async () => {
    running = await startServer(makeDeps());
    const a = new TestClient(running.url);
    await a.join(tokenFor('room1', 'A'));
    const b = new TestClient(running.url);
    await b.join(tokenFor('room1', 'B'));
    await a.waitForType('participant-joined');
    for (let i = 0; i < 5; i += 1) {
      a.send({ v: 1, type: 'offer', to: 'B', sdp: `s${i}` });
    }
    await delay(30);
    a.close();
    b.close();
  });
});

describe('event sink', () => {
  it('emits SautiEvent-shaped lifecycle events with no host-domain payload', async () => {
    const deps = makeDeps();
    running = await startServer(deps);
    const marker = 'host-only-marker-9f3';
    const a = new TestClient(running.url);
    await a.join(tokenFor('room1', 'A', { metadata: { tag: marker } }));
    const b = new TestClient(running.url);
    await b.join(tokenFor('room1', 'B'));
    await a.waitForType('participant-joined');
    b.send({ v: 1, type: 'leave' });
    await delay(30);

    expect(deps.events.length).toBeGreaterThan(0);
    for (const e of deps.events) {
      expect(typeof e.type).toBe('string');
      expect(typeof e.roomId).toBe('string');
      expect(typeof e.at).toBe('number');
    }
    const joined = deps.events.find((e) => e.type === 'participant.joined');
    expect(joined?.participantId).toBe('A');
    expect(deps.events.some((e) => e.type === 'participant.left')).toBe(true);
    expect(JSON.stringify(deps.events)).not.toContain(marker);
    a.close();
  });
});
