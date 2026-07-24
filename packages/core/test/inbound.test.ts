import { describe, it, expect, vi } from 'vitest';
import { connect, readyFrame } from './harness.js';
import { flush, participant } from './fakes.js';

describe('inbound frames', () => {
  it('a fresh ready initializes state and one PC per peer [IN-01]', async () => {
    const { call, h } = await connect(
      readyFrame({ selfId: 'A', peers: [participant('B'), participant('C')] })
    );
    expect(call.getSnapshot().participants).toHaveLength(3);
    expect(h.pcs).toHaveLength(2);
  });

  it('a resumed ready keeps live PCs and drops absent peers [IN-02]', async () => {
    const conn = await connect(
      readyFrame({ selfId: 'A', peers: [participant('B'), participant('C')] })
    );
    const pcForB = conn.h.pcs[0]!;
    const pcCountBefore = conn.h.pcs.length;

    conn.socket.deliver(
      readyFrame({ selfId: 'A', peers: [participant('B')], resumed: true })
    );
    await flush();

    expect(pcForB.closed).toBe(false);
    expect(conn.h.pcs).toHaveLength(pcCountBefore);
    const ids = conn.call.getSnapshot().participants.map((p) => p.participantId);
    expect(ids).toContain('B');
    expect(ids).not.toContain('C');
  });

  it('participant-joined adds a participant with one PC carrying the mic track [IN-03]', async () => {
    const conn = await connect(readyFrame({ selfId: 'A' }));
    const before = conn.h.pcs.length;
    conn.socket.deliver({ v: 1, type: 'participant-joined', participant: participant('B') });
    await flush();
    expect(conn.h.pcs).toHaveLength(before + 1);
    const newPc = conn.h.lastPc();
    expect(newPc.senders).toHaveLength(1);
    expect(newPc.senders[0]!.track!.kind).toBe('audio');
    expect(conn.call.getSnapshot().participants.map((p) => p.participantId)).toContain('B');
  });

  it('participant-state merges only the sent key [IN-04]', async () => {
    const conn = await connect(
      readyFrame({ selfId: 'A', peers: [participant('B', { muted: true, onHold: true })] })
    );
    conn.socket.deliver({
      v: 1,
      type: 'participant-state',
      participantId: 'B',
      state: { muted: false }
    });
    await flush();
    const b = conn.call.getSnapshot().participants.find((p) => p.participantId === 'B')!;
    expect(b.muted).toBe(false);
    expect(b.onHold).toBe(true);
  });

  it('participant-unreachable marks reconnecting and keeps the PC [IN-05]', async () => {
    const conn = await connect(readyFrame({ selfId: 'A', peers: [participant('B')] }));
    const pc = conn.h.pcs[0]!;
    conn.socket.deliver({ v: 1, type: 'participant-unreachable', participantId: 'B' });
    await flush();
    const b = conn.call.getSnapshot().participants.find((p) => p.participantId === 'B')!;
    expect(b.connectionState).toBe('reconnecting');
    expect(pc.closed).toBe(false);
  });

  it('participant-left closes the PC, removes the sink and the participant [IN-06]', async () => {
    const conn = await connect(readyFrame({ selfId: 'A', peers: [participant('B')] }));
    const pc = conn.h.pcs[0]!;
    const stream = new (await import('./fakes.js')).FakeStream();
    pc.emitTrack(stream);
    expect(conn.h.sinks).toHaveLength(1);

    conn.socket.deliver({ v: 1, type: 'participant-left', participantId: 'B' });
    await flush();
    expect(pc.closed).toBe(true);
    expect(conn.h.sinks[0]!.removed).toBe(true);
    expect(
      conn.call.getSnapshot().participants.find((p) => p.participantId === 'B')
    ).toBeUndefined();
  });

  it('room-started sets startedAt and starts the clock [IN-07]', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(1_000_000);
    try {
      const conn = await connect(
        readyFrame({ selfId: 'A', serverNow: 1_000_000, startedAt: null })
      );
      conn.h.now.value = 1_000_000;
      expect(conn.call.getSnapshot().durationMs).toBe(0);
      conn.socket.deliver({ v: 1, type: 'room-started', startedAt: 1_000_000 });
      await flush();
      conn.h.now.value = 1_002_000;
      await vi.advanceTimersByTimeAsync(1000);
      expect(conn.call.getSnapshot().durationMs).toBeGreaterThan(0);
    } finally {
      vi.useRealTimers();
    }
  });

  it('an error frame surfaces a typed error and fatal codes close the socket [IN-08]', async () => {
    const conn = await connect(readyFrame({ selfId: 'A' }));
    const errors: { code: string; message: string }[] = [];
    conn.call.on('error', (e) => errors.push({ code: e.code, message: e.message }));

    conn.socket.deliver({ v: 1, type: 'error', code: 'rate-limited', message: 'slow down' });
    await flush();
    expect(errors[0]!.code).toBe('server-error');
    expect(errors[0]!.message).toBe('slow down');
    expect(conn.socket.closed).toBe(false);

    conn.socket.deliver({ v: 1, type: 'error', code: 'unauthorized', message: 'nope' });
    await flush();
    expect(conn.socket.closed).toBe(true);
  });

  it('offer and ice from a peer route to the negotiation handler [IN-09]', async () => {
    const conn = await connect(readyFrame({ selfId: 'A', peers: [participant('B')] }));
    const pc = conn.h.pcs[0]!;
    conn.socket.deliver({ v: 1, type: 'offer', from: 'B', sdp: 'remote-offer' });
    await flush();
    expect(pc.remoteDescriptions.some((d) => d.sdp === 'remote-offer')).toBe(true);

    conn.socket.deliver({
      v: 1,
      type: 'ice',
      from: 'B',
      candidate: { candidate: 'x', sdpMid: '0', sdpMLineIndex: 0 }
    });
    await flush();
    expect(pc.addedCandidates).toHaveLength(1);
  });

  it('a malformed frame is ignored without throwing [IN-10]', async () => {
    const conn = await connect(readyFrame({ selfId: 'A', peers: [participant('B')] }));
    const before = conn.call.getSnapshot();
    expect(() => conn.socket.deliver({ v: 1, type: 'participant-joined' })).not.toThrow();
    expect(() => conn.socket.deliverRaw('not-json{')).not.toThrow();
    await flush();
    expect(conn.call.getSnapshot().participants).toHaveLength(before.participants.length);
  });

  it('a version mismatch raises a typed error and applies no state [IN-11]', async () => {
    const conn = await connect(readyFrame({ selfId: 'A' }));
    const errors: string[] = [];
    conn.call.on('error', (e) => errors.push(e.code));
    conn.socket.deliver({ v: 2, type: 'ready', self: participant('A'), peers: [] });
    await flush();
    expect(errors).toContain('version-mismatch');
    expect(conn.call.getSnapshot().participants).toHaveLength(1);
  });
});
