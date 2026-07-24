import { describe, it, expect, vi } from 'vitest';
import { connect, makeCall, readyFrame, URL, TOKEN } from './harness.js';
import { flush, makeRuntime } from './fakes.js';

describe('duration', () => {
  it('captures clockOffset once and does not recompute it [DUR-01]', async () => {
    const h = makeRuntime();
    h.now.value = 1_000_000;
    const conn = await connect(
      readyFrame({ selfId: 'A', serverNow: 1_000_500, startedAt: 1_000_000 }),
      {},
      h
    );
    expect(conn.call.getSnapshot().durationMs).toBe(500);

    conn.socket.deliver(
      readyFrame({
        selfId: 'A',
        serverNow: 5_000_000,
        startedAt: 1_000_000,
        resumed: true
      })
    );
    await flush();
    h.now.value = 1_001_000;
    conn.call.getSnapshot();
    conn.socket.deliver({ v: 1, type: 'room-started', startedAt: 1_000_000 });
    await flush();
    expect(conn.call.getSnapshot().durationMs).toBe(1500);
  });

  it('is 0 while startedAt is null then tracks elapsed time [DUR-02]', async () => {
    vi.useFakeTimers();
    try {
      const h = makeRuntime();
      h.now.value = 2_000_000;
      const conn = await connect(
        readyFrame({ selfId: 'A', serverNow: 2_000_000, startedAt: null }),
        {},
        h
      );
      expect(conn.call.getSnapshot().durationMs).toBe(0);
      conn.socket.deliver({ v: 1, type: 'room-started', startedAt: 2_000_000 });
      await flush();
      h.now.value = 2_005_000;
      await vi.advanceTimersByTimeAsync(5000);
      expect(conn.call.getSnapshot().durationMs).toBe(5000);
    } finally {
      vi.useRealTimers();
    }
  });

  it('is server-anchored and continues across a reconnect [DUR-03]', async () => {
    vi.useFakeTimers();
    try {
      const h = makeRuntime();
      h.now.value = 3_000_000;
      const conn = await connect(
        readyFrame({ selfId: 'A', serverNow: 3_000_000, startedAt: 3_000_000 }),
        { reconnectBaseMs: 10 },
        h
      );
      h.now.value = 3_010_000;
      await vi.advanceTimersByTimeAsync(1000);
      expect(conn.call.getSnapshot().durationMs).toBe(10_000);

      conn.socket.drop();
      await vi.advanceTimersByTimeAsync(50);
      const resumed = h.lastSocket();
      resumed.open();
      h.now.value = 3_012_000;
      resumed.deliver(
        readyFrame({
          selfId: 'A',
          serverNow: 9_000_000,
          startedAt: 3_000_000,
          resumed: true
        })
      );
      await flush();
      await vi.advanceTimersByTimeAsync(1000);
      expect(conn.call.getSnapshot().durationMs).toBe(12_000);
    } finally {
      vi.useRealTimers();
    }
  });

  it('ticks about once per second and stops after leave [DUR-04]', async () => {
    vi.useFakeTimers();
    try {
      const { h, call } = makeCall();
      h.now.value = 4_000_000;
      const p = call.join({ url: URL, token: TOKEN });
      await flush();
      h.lastSocket().open();
      h.lastSocket().deliver(
        readyFrame({ selfId: 'A', serverNow: 4_000_000, startedAt: 4_000_000 })
      );
      await p;

      h.now.value = 4_001_000;
      await vi.advanceTimersByTimeAsync(1000);
      expect(call.getSnapshot().durationMs).toBe(1000);

      call.leave();
      h.now.value = 4_005_000;
      await vi.advanceTimersByTimeAsync(3000);
      expect(call.getSnapshot().durationMs).toBe(0);
    } finally {
      vi.useRealTimers();
    }
  });
});
