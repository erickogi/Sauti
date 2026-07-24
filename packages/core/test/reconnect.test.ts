import { describe, it, expect, vi } from 'vitest';
import { connect, makeCall, readyFrame, URL, TOKEN } from './harness.js';
import { flush, participant } from './fakes.js';

describe('reconnect and recovery', () => {
  it('reconnects presenting the same token [RC-01]', async () => {
    vi.useFakeTimers();
    try {
      const conn = await connect(readyFrame({ selfId: 'A' }), { reconnectBaseMs: 10 });
      conn.socket.drop();
      await vi.advanceTimersByTimeAsync(50);
      const resumed = conn.h.lastSocket();
      resumed.open();
      expect(resumed.frames()[0]).toEqual({ v: 1, type: 'join', token: TOKEN });
    } finally {
      vi.useRealTimers();
    }
  });

  it('tries ICE restart before rebuilding a live PC [RC-02]', async () => {
    const conn = await connect(readyFrame({ selfId: 'A', peers: [participant('B')] }));
    const pc = conn.h.pcs[0]!;
    const before = conn.h.pcs.length;
    pc.setIceState('failed');
    expect(pc.restartCount).toBe(1);
    expect(conn.h.pcs).toHaveLength(before);
    expect(pc.closed).toBe(false);
  });

  it('rebuilds only after ICE restart fails, not when it succeeds [RC-03]', async () => {
    const conn = await connect(readyFrame({ selfId: 'A', peers: [participant('B')] }));
    const pc = conn.h.pcs[0]!;
    pc.setIceState('failed');
    pc.setIceState('failed');
    await flush();
    expect(pc.closed).toBe(true);
    expect(conn.h.pcs).toHaveLength(2);

    const rebuilt = conn.h.pcs[1]!;
    rebuilt.setIceState('failed');
    rebuilt.setIceState('connected');
    rebuilt.setIceState('failed');
    expect(rebuilt.restartCount).toBe(2);
    expect(conn.h.pcs).toHaveLength(2);
  });

  it('a network change proactively restarts ICE on live PCs [RC-04]', async () => {
    const conn = await connect(readyFrame({ selfId: 'A', peers: [participant('B')] }));
    const pc = conn.h.pcs[0]!;
    const before = conn.h.pcs.length;
    conn.h.fire('online');
    expect(pc.restartCount).toBe(1);
    expect(conn.h.pcs).toHaveLength(before);
  });

  it('retains the PC through an unreachable window and recovers it [RC-05]', async () => {
    const conn = await connect(readyFrame({ selfId: 'A', peers: [participant('B')] }));
    const pc = conn.h.pcs[0]!;
    conn.socket.deliver({ v: 1, type: 'participant-unreachable', participantId: 'B' });
    await flush();
    expect(conn.call.getSnapshot().participants.find((p) => p.participantId === 'B')!.connectionState).toBe(
      'reconnecting'
    );
    pc.setIceState('connected');
    await flush();
    expect(conn.h.pcs[0]).toBe(pc);
    expect(conn.h.pcs).toHaveLength(1);
    expect(conn.call.getSnapshot().participants.find((p) => p.participantId === 'B')!.connectionState).toBe(
      'connected'
    );
  });

  it('a resumed ready does not re-announce existing peers or recreate PCs [RC-07]', async () => {
    const conn = await connect(
      readyFrame({ selfId: 'A', peers: [participant('B'), participant('C')] })
    );
    const pcRefs = [...conn.h.pcs];
    const joined: string[] = [];
    conn.call.on('joined', (e) => joined.push(e.participantId));
    conn.socket.deliver(
      readyFrame({
        selfId: 'A',
        peers: [participant('B'), participant('C')],
        resumed: true
      })
    );
    await flush();
    expect(joined).toHaveLength(0);
    expect(conn.h.pcs).toHaveLength(2);
    expect(conn.h.pcs[0]).toBe(pcRefs[0]);
    expect(conn.h.pcs[1]).toBe(pcRefs[1]);
  });

  it('gives up after the attempt budget with a typed terminal error [RC-08]', async () => {
    vi.useFakeTimers();
    try {
      const conn = await connect(readyFrame({ selfId: 'A' }), {
        reconnectBaseMs: 10,
        reconnectMaxMs: 100,
        maxReconnectAttempts: 2
      });
      const errors: string[] = [];
      conn.call.on('error', (e) => errors.push(e.code));

      conn.socket.drop();
      await vi.advanceTimersByTimeAsync(200);
      conn.h.lastSocket().drop();
      await vi.advanceTimersByTimeAsync(200);
      conn.h.lastSocket().drop();
      const socketsAfterTerminal = conn.h.sockets.length;
      await vi.advanceTimersByTimeAsync(1000);

      expect(errors).toContain('disconnected');
      expect(conn.h.sockets).toHaveLength(socketsAfterTerminal);
    } finally {
      vi.useRealTimers();
    }
  });
});
