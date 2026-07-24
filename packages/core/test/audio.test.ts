import { describe, it, expect } from 'vitest';
import { connect, readyFrame } from './harness.js';
import { flush, makeRuntime, participant, FakeStream, FakeAudioSink } from './fakes.js';

describe('audio sinks', () => {
  it('creates one sink per peer on first track and removes it on leave [AUD-01]', async () => {
    const conn = await connect(readyFrame({ selfId: 'A', peers: [participant('B')] }));
    conn.h.pcs[0]!.emitTrack(new FakeStream());
    await flush();
    expect(conn.h.sinks).toHaveLength(1);
    conn.socket.deliver({ v: 1, type: 'participant-left', participantId: 'B' });
    await flush();
    expect(conn.h.sinks[0]!.removed).toBe(true);
  });

  it('unlockAudio plays every managed sink [AUD-02]', async () => {
    const conn = await connect(
      readyFrame({ selfId: 'S', peers: [participant('A'), participant('B')] })
    );
    conn.h.pcs[0]!.emitTrack(new FakeStream());
    conn.h.pcs[1]!.emitTrack(new FakeStream());
    await flush();
    const before = conn.h.sinks.map((s) => s.playCount);
    await conn.call.unlockAudio();
    for (let i = 0; i < conn.h.sinks.length; i += 1) {
      expect(conn.h.sinks[i]!.playCount).toBeGreaterThan(before[i]!);
    }
  });

  it('surfaces an autoplay rejection as a needs-unlock flag [AUD-03]', async () => {
    const h = makeRuntime({
      createAudioSink: () => {
        const sink = new FakeAudioSink();
        sink.rejectPlay = true;
        return sink;
      }
    });
    const conn = await connect(readyFrame({ selfId: 'A', peers: [participant('B')] }), {}, h);
    conn.h.pcs[0]!.emitTrack(new FakeStream());
    await flush();
    expect(conn.call.getSnapshot().audioBlocked).toBe(true);
  });

  it('creates independent sinks for three remote peers [AUD-04]', async () => {
    const conn = await connect(
      readyFrame({
        selfId: 'S',
        peers: [participant('A'), participant('B'), participant('C')],
        maxParticipants: 4
      })
    );
    const streams = [new FakeStream(), new FakeStream(), new FakeStream()];
    conn.h.pcs[0]!.emitTrack(streams[0]!);
    conn.h.pcs[1]!.emitTrack(streams[1]!);
    conn.h.pcs[2]!.emitTrack(streams[2]!);
    await flush();
    expect(conn.h.sinks).toHaveLength(3);
    expect(conn.h.sinks[0]!.srcObject).toBe(streams[0]);
    expect(conn.h.sinks[1]!.srcObject).toBe(streams[1]);
    expect(conn.h.sinks[2]!.srcObject).toBe(streams[2]);
  });
});
