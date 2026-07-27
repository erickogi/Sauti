import { describe, it, expect, vi } from 'vitest';
import { connect, readyFrame } from './harness.js';
import { flush, participant } from './fakes.js';

describe('rejoin after the server dropped us', () => {
  it('rebuilds stale peers when a reconnect returns a fresh ready [RC-09]', async () => {
    vi.useFakeTimers();
    try {
      const conn = await connect(
        readyFrame({ selfId: 'B', peers: [participant('A')] }),
        { reconnectBaseMs: 10 }
      );
      const stalePc = conn.h.pcs[0]!;
      expect(conn.h.pcs).toHaveLength(1);

      conn.socket.drop();
      await vi.advanceTimersByTimeAsync(50);
      const resumed = conn.h.lastSocket();
      resumed.open();
      resumed.deliver(
        readyFrame({ selfId: 'B', peers: [participant('A')], resumed: false })
      );
      await flush();

      expect(stalePc.closed).toBe(true);
      expect(conn.h.pcs).toHaveLength(2);
      expect(conn.h.pcs[1]!.closed).toBe(false);
    } finally {
      vi.useRealTimers();
    }
  });

  it('drops peers that vanished while we were gone on a fresh ready [RC-10]', async () => {
    vi.useFakeTimers();
    try {
      const conn = await connect(
        readyFrame({ selfId: 'B', peers: [participant('A'), participant('C')] }),
        { reconnectBaseMs: 10 }
      );
      expect(conn.h.pcs).toHaveLength(2);
      const staleA = conn.h.pcs[0]!;
      const staleC = conn.h.pcs[1]!;

      conn.socket.drop();
      await vi.advanceTimersByTimeAsync(50);
      const resumed = conn.h.lastSocket();
      resumed.open();
      resumed.deliver(
        readyFrame({ selfId: 'B', peers: [participant('A')], resumed: false })
      );
      await flush();

      expect(staleA.closed).toBe(true);
      expect(staleC.closed).toBe(true);
      const present = conn.call
        .getSnapshot()
        .participants.map((p) => p.participantId)
        .sort();
      expect(present).toEqual(['A', 'B']);
    } finally {
      vi.useRealTimers();
    }
  });
});
