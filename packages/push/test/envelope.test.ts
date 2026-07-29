import { describe, expect, it } from 'vitest';
import {
  WAKE_PUSH_KIND,
  WAKE_PUSH_VERSION,
  decodeWakePushData,
  type WakePushPayload
} from '@sauti/protocol';
import { buildWakePushEnvelope } from '../src/index.js';

function basePayload(overrides: Partial<WakePushPayload> = {}): WakePushPayload {
  return {
    kind: WAKE_PUSH_KIND,
    v: WAKE_PUSH_VERSION,
    callId: 'call-1',
    roomId: 'room-1',
    issuedAt: 1000,
    ttlSeconds: 60,
    ...overrides
  };
}

describe('buildWakePushEnvelope', () => {
  it('maps every payload field to the expected data key', () => {
    const payload = basePayload({
      callerDisplayHint: 'hint',
      joinHint: 'join'
    });
    const env = buildWakePushEnvelope(payload);
    expect(env.data['kind']).toBe(WAKE_PUSH_KIND);
    expect(env.data['v']).toBe(String(WAKE_PUSH_VERSION));
    expect(env.data['callId']).toBe('call-1');
    expect(env.data['roomId']).toBe('room-1');
    expect(env.data['issuedAt']).toBe('1000');
    expect(env.data['ttlSeconds']).toBe('60');
    expect(env.data['callerDisplayHint']).toBe('hint');
    expect(env.data['joinHint']).toBe('join');
  });

  it('is always data-only and high priority', () => {
    const env = buildWakePushEnvelope(basePayload());
    expect(env.dataOnly).toBe(true);
    expect(env.priority).toBe('high');
  });

  it('falls back to the payload ttl when no option is given', () => {
    const env = buildWakePushEnvelope(basePayload({ ttlSeconds: 45 }));
    expect(env.ttlSeconds).toBe(45);
  });

  it('lets the option override the payload ttl', () => {
    const env = buildWakePushEnvelope(basePayload({ ttlSeconds: 45 }), {
      ttlSeconds: 5
    });
    expect(env.ttlSeconds).toBe(5);
  });

  it('accepts a zero ttl override rather than falling back', () => {
    const env = buildWakePushEnvelope(basePayload({ ttlSeconds: 45 }), {
      ttlSeconds: 0
    });
    expect(env.ttlSeconds).toBe(0);
  });

  it('defaults the collapse key to the call id', () => {
    const env = buildWakePushEnvelope(basePayload({ callId: 'abc' }));
    expect(env.collapseKey).toBe('abc');
  });

  it('lets the option override the collapse key', () => {
    const env = buildWakePushEnvelope(basePayload({ callId: 'abc' }), {
      collapseKey: 'grouped'
    });
    expect(env.collapseKey).toBe('grouped');
  });

  it('produces only string data values', () => {
    const env = buildWakePushEnvelope(
      basePayload({ metadata: { region: 'nbo' } })
    );
    for (const value of Object.values(env.data)) {
      expect(typeof value).toBe('string');
    }
  });

  it('namespaces metadata so it cannot shadow a top-level key', () => {
    const env = buildWakePushEnvelope(
      basePayload({
        callId: 'real-call',
        metadata: { callId: 'spoofed', from: 'spoofed' }
      })
    );
    expect(env.data['callId']).toBe('real-call');
    expect(env.data['meta.callId']).toBe('spoofed');
    expect(env.data['meta.from']).toBe('spoofed');
  });

  it('decodes back to a minimal payload', () => {
    const payload = basePayload();
    const env = buildWakePushEnvelope(payload);
    const decoded = decodeWakePushData(env.data);
    expect(decoded.ok).toBe(true);
    if (decoded.ok) {
      expect(decoded.payload).toEqual({ ...payload, metadata: {} });
    }
  });

  it('decodes back to a fully populated payload', () => {
    const payload = basePayload({
      callerDisplayHint: 'hint',
      joinHint: 'join',
      metadata: { region: 'nbo' }
    });
    const env = buildWakePushEnvelope(payload);
    const decoded = decodeWakePushData(env.data);
    expect(decoded.ok).toBe(true);
    if (decoded.ok) {
      expect(decoded.payload).toEqual(payload);
    }
  });
});
