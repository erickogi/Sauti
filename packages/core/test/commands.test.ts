import { describe, it, expect, vi } from 'vitest';
import { connect, makeCall, readyFrame, URL, TOKEN } from './harness.js';
import { flush, makeRuntime, participant, FakeStream, FakeTrack } from './fakes.js';

describe('commands', () => {
  it('join opens one socket and settles to connected on ready; a second join is a no-op [CMD-01]', async () => {
    const { h, call } = makeCall();
    const p = call.join({ url: URL, token: TOKEN });
    await flush();
    h.lastSocket().open();
    h.lastSocket().deliver(readyFrame({ selfId: 'A' }));
    await p;
    expect(call.getSnapshot().phase).toBe('connected');
    expect(h.sockets).toHaveLength(1);

    await call.join({ url: URL, token: TOKEN });
    expect(h.sockets).toHaveLength(1);
    expect(h.lastSocket().frames().filter((f) => f.type === 'join')).toHaveLength(1);
  });

  it('leave is a no-op before join and terminal after join [CMD-02]', async () => {
    const { call } = makeCall();
    expect(() => call.leave()).not.toThrow();

    const conn = await connect(readyFrame({ selfId: 'A', peers: [] }));
    conn.call.leave();
    expect(conn.call.getSnapshot().phase).toBe('left');
    expect(conn.call.getSnapshot().participants).toHaveLength(0);
    expect(conn.socket.frames().some((f) => f.type === 'leave')).toBe(true);
  });

  it('leave tears down a live mesh: closes every PC, stops the mic, empties the sink registry, halts duration [MESH-05]', async () => {
    vi.useFakeTimers();
    try {
      const h = makeRuntime();
      const micTrack = new FakeTrack();
      h.setGetUserMedia(async () => new FakeStream([micTrack]));
      h.now.value = 5_000_000;
      const conn = await connect(
        readyFrame({
          selfId: 'S',
          peers: [participant('A'), participant('B')],
          serverNow: 5_000_000,
          startedAt: 5_000_000
        }),
        {},
        h
      );
      conn.h.pcs[0]!.emitTrack(new FakeStream());
      conn.h.pcs[1]!.emitTrack(new FakeStream());
      await flush();
      expect(conn.h.pcs).toHaveLength(2);
      expect(conn.h.sinks).toHaveLength(2);

      h.now.value = 5_001_000;
      await vi.advanceTimersByTimeAsync(1000);
      expect(conn.call.getSnapshot().durationMs).toBe(1000);

      conn.call.leave();

      expect(conn.h.pcs[0]!.closed).toBe(true);
      expect(conn.h.pcs[1]!.closed).toBe(true);
      expect(micTrack.stopped).toBe(true);
      expect(conn.h.sinks.every((s) => s.removed)).toBe(true);
      expect(conn.call.getSnapshot().participants).toHaveLength(0);

      h.now.value = 5_010_000;
      await vi.advanceTimersByTimeAsync(5000);
      expect(conn.call.getSnapshot().durationMs).toBe(0);
    } finally {
      vi.useRealTimers();
    }
  });

  it('setMuted toggles localMuted, the track, and the state frame [CMD-03]', async () => {
    const h = makeRuntime();
    const micTrack = new FakeTrack();
    h.setGetUserMedia(async () => new FakeStream([micTrack]));
    const conn = await connect(readyFrame({ selfId: 'A' }), {}, h);

    conn.call.setMuted(true);
    expect(conn.call.getSnapshot().localMuted).toBe(true);
    expect(micTrack.enabled).toBe(false);
    expect(conn.socket.frames().at(-1)).toEqual({ v: 1, type: 'state', state: { muted: true } });

    conn.call.setMuted(false);
    expect(conn.call.getSnapshot().localMuted).toBe(false);
    expect(micTrack.enabled).toBe(true);
    expect(conn.socket.frames().at(-1)).toEqual({ v: 1, type: 'state', state: { muted: false } });
  });

  it('setHold toggles localOnHold, the mic sender audio, and the state frame [CMD-04]', async () => {
    const h = makeRuntime();
    const micTrack = new FakeTrack();
    h.setGetUserMedia(async () => new FakeStream([micTrack]));
    const conn = await connect(readyFrame({ selfId: 'A' }), {}, h);

    conn.call.setHold(true);
    expect(conn.call.getSnapshot().localOnHold).toBe(true);
    expect(micTrack.enabled).toBe(false);
    expect(conn.socket.frames().at(-1)).toEqual({ v: 1, type: 'state', state: { onHold: true } });
  });
});
