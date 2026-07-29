import { describe, it, expect } from 'vitest';
import {
  WAKE_PUSH_KIND,
  WAKE_PUSH_VERSION,
  WAKE_PUSH_JOIN_HINT_KEY,
  encodeWakePushData,
  decodeWakePushData,
  isWakePushExpired
} from '../src/wakePush.js';
import type { WakePushPayload } from '../src/wakePush.js';

const base: WakePushPayload = {
  kind: WAKE_PUSH_KIND,
  v: WAKE_PUSH_VERSION,
  callId: 'call-1',
  roomId: 'room-1',
  issuedAt: 1000,
  ttlSeconds: 30
};

const full: WakePushPayload = {
  ...base,
  callerDisplayHint: 'label',
  joinHint: 'hint-token',
  metadata: { a: '1', b: '2' }
};

describe('reserved constants', () => {
  it('exposes the frozen wake-push identifiers', () => {
    expect(WAKE_PUSH_KIND).toBe('io.sauti.wake.call');
    expect(WAKE_PUSH_VERSION).toBe(1);
    expect(WAKE_PUSH_JOIN_HINT_KEY).toBe('sauti.joinHint');
  });
});

describe('round-trip', () => {
  it('recovers a full payload with every optional present', () => {
    const result = decodeWakePushData(encodeWakePushData(full));
    expect(result).toEqual({ ok: true, payload: full });
  });

  it('recovers a minimal payload and normalizes metadata to an empty map', () => {
    const result = decodeWakePushData(encodeWakePushData(base));
    expect(result).toEqual({ ok: true, payload: { ...base, metadata: {} } });
  });
});

describe('encode output shape', () => {
  it('is a flat string map with metadata under the meta prefix', () => {
    const data = encodeWakePushData(full);
    for (const key of Object.keys(data)) {
      expect(typeof data[key]).toBe('string');
    }
    expect(data['meta.a']).toBe('1');
    expect(data['meta.b']).toBe('2');
    expect(data['a']).toBeUndefined();
    expect(data['kind']).toBe(WAKE_PUSH_KIND);
    expect(data['v']).toBe('1');
    expect(data['issuedAt']).toBe('1000');
    expect(data['ttlSeconds']).toBe('30');
  });
});

describe('numeric version', () => {
  it('emits v as the numeric version after a round-trip', () => {
    const result = decodeWakePushData(encodeWakePushData(full));
    if (!result.ok) throw new Error('expected ok');
    expect(typeof result.payload.v).toBe('number');
    expect(result.payload.v).toBe(WAKE_PUSH_VERSION);
  });
});

describe('reason wrong-kind', () => {
  it('reports wrong-kind when kind is absent', () => {
    expect(decodeWakePushData({})).toEqual({ ok: false, reason: 'wrong-kind' });
  });

  it('reports wrong-kind when kind is present but different', () => {
    const data = encodeWakePushData(base);
    data['kind'] = 'io.other.kind';
    expect(decodeWakePushData(data)).toEqual({
      ok: false,
      reason: 'wrong-kind'
    });
  });
});

describe('reason unsupported-version', () => {
  it('reports unsupported-version when v is absent', () => {
    const data = encodeWakePushData(base);
    delete data['v'];
    expect(decodeWakePushData(data)).toEqual({
      ok: false,
      reason: 'unsupported-version'
    });
  });

  it('reports unsupported-version for a future version', () => {
    const data = encodeWakePushData(base);
    data['v'] = '2';
    expect(decodeWakePushData(data)).toEqual({
      ok: false,
      reason: 'unsupported-version'
    });
  });

  it('reports unsupported-version for a non-numeric version', () => {
    const data = encodeWakePushData(base);
    data['v'] = 'x';
    expect(decodeWakePushData(data)).toEqual({
      ok: false,
      reason: 'unsupported-version'
    });
  });
});

describe('reason malformed', () => {
  it('reports malformed when callId is missing', () => {
    const data = encodeWakePushData(base);
    delete data['callId'];
    expect(decodeWakePushData(data)).toEqual({ ok: false, reason: 'malformed' });
  });

  it('reports malformed when roomId is missing', () => {
    const data = encodeWakePushData(base);
    delete data['roomId'];
    expect(decodeWakePushData(data)).toEqual({ ok: false, reason: 'malformed' });
  });

  it('reports malformed when issuedAt is non-numeric', () => {
    const data = encodeWakePushData(base);
    data['issuedAt'] = 'soon';
    expect(decodeWakePushData(data)).toEqual({ ok: false, reason: 'malformed' });
  });

  it('reports malformed when ttlSeconds is non-numeric', () => {
    const data = encodeWakePushData(base);
    data['ttlSeconds'] = 'long';
    expect(decodeWakePushData(data)).toEqual({ ok: false, reason: 'malformed' });
  });

  it('reports malformed for an object carrying only kind and version', () => {
    const data: Record<string, string> = {
      kind: WAKE_PUSH_KIND,
      v: '1'
    };
    expect(decodeWakePushData(data)).toEqual({ ok: false, reason: 'malformed' });
  });
});

describe('tolerance', () => {
  it('ignores unknown top-level keys when the required set is present', () => {
    const data = encodeWakePushData(base);
    data['surprise'] = 'value';
    const result = decodeWakePushData(data);
    expect(result.ok).toBe(true);
    if (!result.ok) throw new Error('expected ok');
    expect('surprise' in result.payload.metadata!).toBe(false);
  });
});

describe('metadata edge cases', () => {
  it('round-trips an empty metadata map', () => {
    const p: WakePushPayload = { ...base, metadata: {} };
    const result = decodeWakePushData(encodeWakePushData(p));
    expect(result).toEqual({ ok: true, payload: p });
  });

  it('round-trips a key that itself starts with the meta prefix', () => {
    const p: WakePushPayload = { ...base, metadata: { 'meta.x': 'y' } };
    const data = encodeWakePushData(p);
    expect(data['meta.meta.x']).toBe('y');
    const result = decodeWakePushData(data);
    if (!result.ok) throw new Error('expected ok');
    expect(result.payload.metadata).toEqual({ 'meta.x': 'y' });
  });
});

describe('isWakePushExpired', () => {
  it('treats the exact boundary as not expired', () => {
    const at = base.issuedAt + base.ttlSeconds * 1000;
    expect(isWakePushExpired(base, at)).toBe(false);
  });

  it('treats one millisecond past the boundary as expired', () => {
    const at = base.issuedAt + base.ttlSeconds * 1000 + 1;
    expect(isWakePushExpired(base, at)).toBe(true);
  });

  it('expires a zero ttl for any moment after issue', () => {
    const p: WakePushPayload = { ...base, ttlSeconds: 0 };
    expect(isWakePushExpired(p, p.issuedAt + 1)).toBe(true);
    expect(isWakePushExpired(p, p.issuedAt)).toBe(false);
  });

  it('is not expired when the clock reads before issue', () => {
    expect(isWakePushExpired(base, base.issuedAt - 5000)).toBe(false);
  });
});
