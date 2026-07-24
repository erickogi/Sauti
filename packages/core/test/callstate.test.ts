import { describe, it, expect, vi } from 'vitest';
import { connect, makeCall, readyFrame, URL, TOKEN } from './harness.js';
import { flush, participant } from './fakes.js';

describe('call state', () => {
  it('replaces the snapshot object on change and never mutates in place [CS-01]', async () => {
    const conn = await connect(readyFrame({ selfId: 'A' }));
    const first = conn.call.getSnapshot();
    conn.socket.deliver({ v: 1, type: 'participant-joined', participant: participant('B') });
    await flush();
    const second = conn.call.getSnapshot();
    expect(second).not.toBe(first);
    expect(first.participants).toHaveLength(1);
    expect(second.participants).toHaveLength(2);
  });

  it('carries every documented field with the right primitive type [CS-02]', async () => {
    const conn = await connect(
      readyFrame({ selfId: 'A', peers: [participant('B', { muted: true })] })
    );
    const snap = conn.call.getSnapshot();
    expect(typeof snap.localMuted).toBe('boolean');
    expect(typeof snap.localOnHold).toBe('boolean');
    expect(typeof snap.durationMs).toBe('number');
    expect(typeof snap.quality).toBe('string');
    expect(typeof snap.reconnecting).toBe('boolean');
    expect(typeof snap.audioBlocked).toBe('boolean');
    for (const p of snap.participants) {
      expect(typeof p.participantId).toBe('string');
      expect(typeof p.muted).toBe('boolean');
      expect(typeof p.onHold).toBe('boolean');
      expect(typeof p.connectionState).toBe('string');
      expect(typeof p.quality).toBe('string');
    }
  });

  it('returns a referentially stable snapshot between changes [CS-03]', async () => {
    const conn = await connect(readyFrame({ selfId: 'A' }));
    expect(conn.call.getSnapshot()).toBe(conn.call.getSnapshot());
  });

  it('reflects a local socket drop in reconnecting and clears it on resume [CS-04a]', async () => {
    vi.useFakeTimers();
    try {
      const { h, call } = makeCall({ reconnectBaseMs: 10, maxReconnectAttempts: 5 });
      const promise = call.join({ url: URL, token: TOKEN });
      await flush();
      const socket = h.lastSocket();
      socket.open();
      socket.deliver(readyFrame({ selfId: 'A' }));
      await promise;
      expect(call.getSnapshot().reconnecting).toBe(false);

      socket.drop();
      expect(call.getSnapshot().reconnecting).toBe(true);

      await vi.advanceTimersByTimeAsync(50);
      const resumed = h.lastSocket();
      resumed.open();
      resumed.deliver(readyFrame({ selfId: 'A', resumed: true }));
      await flush();
      expect(call.getSnapshot().reconnecting).toBe(false);
    } finally {
      vi.useRealTimers();
    }
  });

  it('reflects a peer becoming unreachable and clears it on resume [CS-04b]', async () => {
    const conn = await connect(readyFrame({ selfId: 'A', peers: [participant('B')] }));
    expect(conn.call.getSnapshot().reconnecting).toBe(false);
    conn.socket.deliver({ v: 1, type: 'participant-unreachable', participantId: 'B' });
    await flush();
    expect(conn.call.getSnapshot().reconnecting).toBe(true);
    conn.socket.deliver(
      readyFrame({ selfId: 'A', peers: [participant('B')], resumed: true })
    );
    await flush();
    expect(conn.call.getSnapshot().reconnecting).toBe(false);
  });
});
