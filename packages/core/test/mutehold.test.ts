import { describe, it, expect } from 'vitest';
import { connect, readyFrame } from './harness.js';
import { flush, makeRuntime, participant, FakeStream, FakeTrack } from './fakes.js';

describe('mute and hold', () => {
  it('a remote muted state comes only from participant-state, never inferred [MH-01]', async () => {
    const conn = await connect(
      readyFrame({ selfId: 'A', peers: [participant('B', { muted: false })] })
    );
    conn.h.pcs[0]!.emitTrack(new FakeStream());
    await flush();
    let b = conn.call.getSnapshot().participants.find((p) => p.participantId === 'B')!;
    expect(b.muted).toBe(false);

    conn.socket.deliver({
      v: 1,
      type: 'participant-state',
      participantId: 'B',
      state: { muted: true }
    });
    await flush();
    b = conn.call.getSnapshot().participants.find((p) => p.participantId === 'B')!;
    expect(b.muted).toBe(true);
  });

  it('local mute disables the track while remote awareness rides the state frame [MH-02]', async () => {
    const h = makeRuntime();
    const micTrack = new FakeTrack();
    h.setGetUserMedia(async () => new FakeStream([micTrack]));
    const conn = await connect(
      readyFrame({ selfId: 'A', peers: [participant('B', { muted: false })] }),
      {},
      h
    );

    conn.call.setMuted(true);
    expect(micTrack.enabled).toBe(false);
    const b = conn.call.getSnapshot().participants.find((p) => p.participantId === 'B')!;
    expect(b.muted).toBe(false);
    expect(conn.socket.frames().some((f) => f.type === 'state')).toBe(true);

    conn.socket.deliver({
      v: 1,
      type: 'participant-state',
      participantId: 'B',
      state: { muted: true }
    });
    await flush();
    const bAfter = conn.call.getSnapshot().participants.find((p) => p.participantId === 'B')!;
    expect(bAfter.muted).toBe(true);
  });

  it('mute and hold survive resume through the ready snapshot [MH-03]', async () => {
    const conn = await connect(readyFrame({ selfId: 'A', peers: [participant('B')] }));
    conn.socket.deliver(
      readyFrame({
        selfId: 'A',
        peers: [participant('B', { muted: true, onHold: true })],
        resumed: true
      })
    );
    await flush();
    const b = conn.call.getSnapshot().participants.find((p) => p.participantId === 'B')!;
    expect(b.muted).toBe(true);
    expect(b.onHold).toBe(true);
  });
});
