import { describe, it, expect, afterEach } from 'vitest';
import { createHmac } from 'node:crypto';
import { mintIceServers, opaqueRef } from '../src/turn.js';
import { makeDeps, tokenFor, turn } from './deps.js';
import { startServer, stopServer, TestClient, type RunningServer } from './harness.js';

let running: RunningServer | undefined;

afterEach(async () => {
  if (running) await stopServer(running);
  running = undefined;
});

describe('coturn REST credential minting', () => {
  it('builds username as exp:opaqueRef and credential as base64 hmac-sha1 of the username', () => {
    const now = 1_700_000_000_000;
    const [server] = mintIceServers(turn, 'alice', now);
    const [expStr, ref] = server!.username.split(':');
    expect(Number(expStr)).toBe(Math.floor(now / 1000) + turn.ttlSeconds);
    expect(ref).toBe(opaqueRef('alice'));
    expect(ref).not.toBe('alice');
    const expected = createHmac('sha1', turn.secret)
      .update(server!.username)
      .digest('base64');
    expect(server!.credential).toBe(expected);
    expect(server!.urls).toEqual(turn.urls);
    expect(server!.realm).toBe('sauti');
  });

  it('derives an opaque ref that is not the raw participant identity', () => {
    expect(opaqueRef('participant-secret')).toMatch(/^[0-9a-f]{16}$/);
    expect(opaqueRef('a')).not.toBe(opaqueRef('b'));
  });

  it('omits realm when it is not configured', () => {
    const [server] = mintIceServers(
      { urls: ['turn:x'], secret: 's', ttlSeconds: 60 },
      'a',
      1000
    );
    expect(server!.realm).toBeUndefined();
  });

  it('never exposes the static-auth-secret in a ready frame', async () => {
    running = await startServer(makeDeps());
    const a = new TestClient(running.url);
    const ready = await a.join(tokenFor('room1', 'alice'));
    const serialized = JSON.stringify(ready);
    expect(serialized.includes(turn.secret)).toBe(false);
    const iceServers = ready.iceServers as Array<Record<string, unknown>>;
    expect(iceServers[0]?.username).toContain(':');
    expect(JSON.stringify(iceServers)).not.toContain(turn.secret);
    a.close();
  });
});
