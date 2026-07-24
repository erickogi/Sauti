import { describe, it, expect, vi } from 'vitest';
import { AudioSinkManager } from '../src/audio.js';
import { connect, readyFrame } from './harness.js';
import {
  flush,
  makeRuntime,
  participant,
  FakeStream,
  FakeAudioSink,
  FakePeerConnection
} from './fakes.js';

describe('coverage completions', () => {
  it('reuses an existing sink and toggles the blocked flag on unlock', async () => {
    const h = makeRuntime();
    const blocked: number[] = [];
    const unblocked: number[] = [];
    const mgr = new AudioSinkManager(
      h.runtime,
      () => blocked.push(1),
      () => unblocked.push(1)
    );
    const streamA = new FakeStream();
    const streamB = new FakeStream();
    mgr.attach('A', streamA);
    mgr.attach('A', streamB);
    await flush();
    expect(h.sinks).toHaveLength(1);
    expect(h.sinks[0]!.srcObject).toBe(streamB);
    await mgr.unlock();
    expect(unblocked.length).toBeGreaterThan(0);
  });

  it('reports blocked from unlock when a sink rejects play', async () => {
    const h = makeRuntime({
      createAudioSink: () => {
        const sink = new FakeAudioSink();
        sink.rejectPlay = true;
        return sink;
      }
    });
    let blocked = false;
    const mgr = new AudioSinkManager(
      h.runtime,
      () => (blocked = true),
      () => undefined
    );
    mgr.attach('A', new FakeStream());
    await mgr.unlock();
    expect(blocked).toBe(true);
  });

  it('a resumed ready adds a genuinely new peer while keeping the existing one [IN-02/RC]', async () => {
    const conn = await connect(readyFrame({ selfId: 'A', peers: [participant('B')] }));
    const pcForB = conn.h.pcs[0]!;
    conn.socket.deliver(
      readyFrame({
        selfId: 'A',
        peers: [participant('B'), participant('D')],
        resumed: true
      })
    );
    await flush();
    expect(conn.h.pcs).toHaveLength(2);
    expect(conn.h.pcs[0]).toBe(pcForB);
    const ids = conn.call.getSnapshot().participants.map((p) => p.participantId);
    expect(ids).toContain('D');
    const d = conn.call.getSnapshot().participants.find((p) => p.participantId === 'D')!;
    const b = conn.call.getSnapshot().participants.find((p) => p.participantId === 'B')!;
    expect(d.connectionState).toBe('connecting');
    expect(b.connectionState).toBe('connected');
  });

  it('ignores a participant-state for an unknown participant', async () => {
    const conn = await connect(readyFrame({ selfId: 'A' }));
    const events: string[] = [];
    conn.call.on('state-changed', (e) => events.push(e.participantId));
    conn.socket.deliver({
      v: 1,
      type: 'participant-state',
      participantId: 'ghost',
      state: { muted: true }
    });
    await flush();
    expect(events).toHaveLength(0);
  });

  it('leave clears a pending reconnect timer and swallows a send on a closed socket', async () => {
    vi.useFakeTimers();
    try {
      const conn = await connect(readyFrame({ selfId: 'A' }), { reconnectBaseMs: 10000 });
      conn.socket.drop();
      const socketCount = conn.h.sockets.length;
      conn.call.leave();
      await vi.advanceTimersByTimeAsync(30000);
      expect(conn.h.sockets).toHaveLength(socketCount);
    } finally {
      vi.useRealTimers();
    }
  });

  it('tolerates session descriptions without an sdp string', async () => {
    const h = makeRuntime({
      createPeerConnection: (config) => {
        const pc = new FakePeerConnection(config);
        pc.createOffer = async () => ({ type: 'offer' });
        pc.createAnswer = async () => ({ type: 'answer' });
        return pc;
      }
    });
    const conn = await connect(readyFrame({ selfId: 'B', peers: [participant('A')] }), {}, h);
    await flush();
    conn.socket.deliver({ v: 1, type: 'offer', from: 'A', sdp: 'x' });
    await flush();
    const offer = conn.socket.frames().find((f) => f.type === 'offer');
    const answer = conn.socket.frames().find((f) => f.type === 'answer');
    expect(offer!.sdp).toBe('');
    expect(answer!.sdp).toBe('');
  });
});
