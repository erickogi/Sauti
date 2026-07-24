import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { FakeRedisPort } from './fakeRedis.js';
import type { RedisPort } from '../src/types.js';

const REDISPORT_METHODS: Array<keyof RedisPort> = [
  'hsetnx',
  'hget',
  'hgetall',
  'hdel',
  'pexpire',
  'eval',
  'publish',
  'subscribe',
  'unsubscribe'
];

describe('RedisPort surface', () => {
  it('is exactly the nine contract methods, each callable', () => {
    const port = new FakeRedisPort();
    for (const name of REDISPORT_METHODS) {
      expect(typeof port[name], `RedisPort.${String(name)} must exist`).toBe(
        'function'
      );
    }
    expect(REDISPORT_METHODS).toHaveLength(9);
  });

  it('exposes no key-space traversal method, so KEYS and SCAN cannot leak in', () => {
    const port = new FakeRedisPort() as unknown as Record<string, unknown>;
    expect(port.keys).toBeUndefined();
    expect(port.scan).toBeUndefined();
  });
});

describe('room enumeration avoids key-space traversal', () => {
  const serverSrc = readFileSync(
    join(process.cwd(), 'src', 'server.ts'),
    'utf8'
  );

  it('issues no KEYS or SCAN redis call anywhere in the server', () => {
    expect(/\bKEYS\b/.test(serverSrc)).toBe(false);
    expect(/redis\.(keys|scan)\s*\(/.test(serverSrc)).toBe(false);
  });

  it('enumerates active rooms through the maintained registry hash', () => {
    const start = serverSrc.indexOf('async function listActiveRooms');
    expect(start).toBeGreaterThan(-1);
    const end = serverSrc.indexOf('async function close', start);
    const body = serverSrc.slice(start, end);
    expect(body).toContain('redis.hgetall(registryKey)');
  });
});
