import { describe, it, expect } from 'vitest';
import { connect, makeCall, readyFrame, URL, TOKEN } from './harness.js';
import { flush, participant, FakeStream } from './fakes.js';

function livePcs(pcs: { closed: boolean }[]): number {
  return pcs.filter((pc) => !pc.closed).length;
}

describe('mesh', () => {
  it('keeps one PC per remote peer across joins and leaves [MESH-01]', async () => {
    const conn = await connect(readyFrame({ selfId: 'A' }));
    conn.socket.deliver({ v: 1, type: 'participant-joined', participant: participant('B') });
    conn.socket.deliver({ v: 1, type: 'participant-joined', participant: participant('C') });
    await flush();
    expect(livePcs(conn.h.pcs)).toBe(conn.call.getSnapshot().participants.length - 1);

    conn.socket.deliver({ v: 1, type: 'participant-left', participantId: 'B' });
    await flush();
    expect(livePcs(conn.h.pcs)).toBe(conn.call.getSnapshot().participants.length - 1);
    expect(livePcs(conn.h.pcs)).toBe(1);
  });

  it('adds the local mic track to every peer connection [MESH-02]', async () => {
    const conn = await connect(
      readyFrame({ selfId: 'A', peers: [participant('B'), participant('C')] })
    );
    expect(conn.h.pcs).toHaveLength(2);
    for (const pc of conn.h.pcs) {
      expect(pc.senders).toHaveLength(1);
      expect(pc.senders[0]!.track!.kind).toBe('audio');
    }
    expect(conn.h.pcs[0]!.senders[0]!.track).toBe(conn.h.pcs[1]!.senders[0]!.track);
  });

  it('routes every remote track to its own audible sink [MESH-03]', async () => {
    const conn = await connect(
      readyFrame({ selfId: 'S', peers: [participant('A'), participant('B')] })
    );
    const streamA = new FakeStream();
    const streamB = new FakeStream();
    conn.h.pcs[0]!.emitTrack(streamA);
    conn.h.pcs[1]!.emitTrack(streamB);
    await flush();
    expect(conn.h.sinks).toHaveLength(2);
    expect(conn.h.sinks[0]!.srcObject).toBe(streamA);
    expect(conn.h.sinks[1]!.srcObject).toBe(streamB);
    expect(conn.h.sinks[0]!.playCount).toBeGreaterThan(0);
    expect(conn.h.sinks[1]!.playCount).toBeGreaterThan(0);
  });

  it('join fast-fails with a client-side RoomFull error [MESH-04]', async () => {
    const { h, call } = makeCall();
    const errors: string[] = [];
    call.on('error', (e) => errors.push(e.code));
    const promise = call.join({ url: URL, token: TOKEN });
    await flush();
    const socket = h.lastSocket();
    socket.open();
    socket.deliver(
      readyFrame({
        selfId: 'A',
        peers: [participant('B'), participant('C')],
        maxParticipants: 2
      })
    );
    await expect(promise).rejects.toMatchObject({ code: 'room-full' });
    expect(errors).toContain('room-full');
    expect(socket.frames().some((f) => f.type === 'error')).toBe(false);
  });
});
