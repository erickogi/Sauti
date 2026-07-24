import { describe, it, expect } from 'vitest';
import { computeBackoff } from '../src/backoff.js';
import { Emitter } from '../src/emitter.js';
import { CallStore } from '../src/store.js';
import { Mesh } from '../src/mesh.js';
import { AudioSinkManager } from '../src/audio.js';
import { connect, readyFrame } from './harness.js';
import { flush, makeRuntime, participant, FakeStream, FakeTrack, FakePeerConnection } from './fakes.js';

describe('computeBackoff [RC-06]', () => {
  it('increases then caps, with bounded jitter', () => {
    const base = 100;
    const max = 1000;
    const low = Array.from({ length: 7 }, (_, a) => computeBackoff(a, base, max, () => 0));
    for (let i = 1; i < low.length; i += 1) {
      expect(low[i]!).toBeGreaterThanOrEqual(low[i - 1]!);
    }
    for (const value of low) expect(value).toBeLessThanOrEqual(max);
    expect(low.at(-1)!).toBeGreaterThanOrEqual(0.75 * max);

    for (let a = 0; a < 7; a += 1) {
      const lo = computeBackoff(a, base, max, () => 0);
      const hi = computeBackoff(a, base, max, () => 1);
      const cap = Math.min(base * 2 ** a, max);
      expect(hi).toBeGreaterThanOrEqual(lo);
      expect(hi).toBeLessThanOrEqual(cap);
      expect(lo).toBeGreaterThanOrEqual(Math.round(cap * 0.75) - 1);
    }
  });
});

describe('Emitter', () => {
  it('subscribes, emits, and unsubscribes', () => {
    const emitter = new Emitter<{ ping: number }>();
    const seen: number[] = [];
    const off = emitter.on('ping', (n) => seen.push(n));
    emitter.emit('ping', 1);
    off();
    emitter.emit('ping', 2);
    emitter.emit('ping', 3);
    expect(seen).toEqual([1]);
  });

  it('is a no-op for a type with no listeners and supports off()', () => {
    const emitter = new Emitter<{ ping: number }>();
    expect(() => emitter.emit('ping', 1)).not.toThrow();
    const handler = (): void => undefined;
    emitter.on('ping', handler);
    emitter.off('ping', handler);
    emitter.emit('ping', 1);
  });
});

describe('CallStore edge cases', () => {
  it('handles mutations with no matching participant and no self', () => {
    const store = new CallStore(() => 0);
    store.setLocalMuted(true);
    expect(store.getSnapshot().localMuted).toBe(true);
    store.setLocalOnHold(true);
    expect(store.getSnapshot().localOnHold).toBe(true);
    store.mergeState('ghost', { muted: true });
    store.setConnectionState('ghost', 'left');
    store.setQuality('ghost', 'poor');
    store.removeParticipant('ghost');
    expect(store.getParticipant('ghost')).toBeUndefined();
  });

  it('ignores redundant flag writes and clamps negative duration', () => {
    let clock = 0;
    const store = new CallStore(() => clock);
    let notifications = 0;
    store.subscribe(() => (notifications += 1));
    store.setAudioBlocked(false);
    store.setFallback(false);
    expect(notifications).toBe(0);
    store.setAudioBlocked(true);
    expect(notifications).toBe(1);
    store.setClockOffset(0);
    store.setStartedAt(1000);
    clock = 500;
    expect(store.getSnapshot().durationMs).toBe(0);
  });

  it('resets to an idle-left state', () => {
    const store = new CallStore(() => 0);
    store.setSelf('A');
    store.upsertParticipant({
      participantId: 'A',
      metadata: {},
      muted: false,
      onHold: false,
      connectionState: 'connected'
    });
    store.reset();
    expect(store.getSnapshot().participants).toHaveLength(0);
    expect(store.getSnapshot().phase).toBe('left');
    expect(store.getSelfId()).toBeNull();
  });
});

describe('Mesh direct branches', () => {
  it('throws if a peer is created before local media is set', () => {
    const h = makeRuntime();
    const sinks = new AudioSinkManager(
      h.runtime,
      () => undefined,
      () => undefined
    );
    const mesh = new Mesh(h.runtime, sinks, 'self', {
      send: () => undefined,
      onConnected: () => undefined,
      onError: () => undefined
    });
    expect(() => mesh.ensurePeer('peer')).toThrow();
  });

  it('ignores answer and ice for an unknown peer', async () => {
    const h = makeRuntime();
    const sinks = new AudioSinkManager(
      h.runtime,
      () => undefined,
      () => undefined
    );
    const mesh = new Mesh(h.runtime, sinks, 'self', {
      send: () => undefined,
      onConnected: () => undefined,
      onError: () => undefined
    });
    mesh.setLocalMedia(new FakeStream([new FakeTrack()]), new FakeTrack());
    await expect(mesh.routeAnswer('ghost', 'sdp')).resolves.toBeUndefined();
    await expect(
      mesh.routeIce('ghost', { candidate: 'c', sdpMid: '0', sdpMLineIndex: 0 })
    ).resolves.toBeUndefined();
  });
});

describe('call level branches', () => {
  it('applies an inbound answer to the offering peer', async () => {
    const conn = await connect(readyFrame({ selfId: 'B', peers: [participant('A')] }));
    await flush();
    const pc = conn.h.pcs[0]!;
    conn.socket.deliver({ v: 1, type: 'answer', from: 'A', sdp: 'the-answer' });
    await flush();
    expect(pc.remoteDescriptions.some((d) => d.type === 'answer' && d.sdp === 'the-answer')).toBe(
      true
    );
  });

  it('surfaces an offer-creation failure as a typed error [ERR-04]', async () => {
    const h = makeRuntime({
      createPeerConnection: (config) => {
        const pc = new FakePeerConnection(config);
        pc.createOffer = async () => {
          throw new Error('offer failed');
        };
        return pc;
      }
    });
    const conn = await connect(readyFrame({ selfId: 'B', peers: [participant('A')] }), {}, h);
    const errors: string[] = [];
    conn.call.on('error', (e) => errors.push(e.message));
    conn.h.pcs[0]!.emitNegotiationNeeded();
    await flush();
    expect(errors).toContain('offer failed');
  });

  it('injects the transport through the runtime seam [PKG-08]', async () => {
    const conn = await connect(readyFrame({ selfId: 'A' }));
    expect(conn.h.sockets).toHaveLength(1);
    expect(conn.socket.url).toBe('wss://sauti.test/ws');
  });

  it('supports off() and reports listener counts', async () => {
    const conn = await connect(readyFrame({ selfId: 'A' }));
    const seen: string[] = [];
    const handler = (e: { participantId: string }): void => {
      seen.push(e.participantId);
    };
    conn.call.on('joined', handler);
    conn.call.off('joined', handler);
    conn.socket.deliver({ v: 1, type: 'participant-joined', participant: participant('B') });
    await flush();
    expect(seen).toHaveLength(0);

    expect(conn.call.listenerCount()).toBe(0);
    const unsub = conn.call.subscribe(() => undefined);
    expect(conn.call.listenerCount()).toBe(1);
    unsub();
    expect(conn.call.listenerCount()).toBe(0);
  });

  it('does not emit when a routed offer rejects with a non-error [call surface2]', async () => {
    const h = makeRuntime({
      createPeerConnection: (config) => {
        const pc = new FakePeerConnection(config);
        pc.setRemoteDescription = async () => {
          throw 'plain-string-rejection';
        };
        return pc;
      }
    });
    const conn = await connect(readyFrame({ selfId: 'A', peers: [participant('B')] }), {}, h);
    const errors: string[] = [];
    conn.call.on('error', (e) => errors.push(e.code));
    conn.socket.deliver({ v: 1, type: 'offer', from: 'B', sdp: 'x' });
    await flush();
    expect(errors).toHaveLength(0);
  });
});
