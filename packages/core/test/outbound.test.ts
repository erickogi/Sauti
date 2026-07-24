import { describe, it, expect } from 'vitest';
import { parseClientFrame } from '@sauti/protocol';
import { connect, readyFrame, URL, TOKEN } from './harness.js';
import { makeCall } from './harness.js';
import { flush, participant } from './fakes.js';

function mediaFrames(frames: Record<string, unknown>[]): Record<string, unknown>[] {
  return frames.filter((f) => f.type === 'offer' || f.type === 'answer' || f.type === 'ice');
}

describe('outbound frames', () => {
  it('sends the join frame as the first frame only after open [OUT-01]', async () => {
    const { h, call } = makeCall();
    const promise = call.join({ url: URL, token: TOKEN });
    await flush();
    const socket = h.lastSocket();
    expect(socket.sent).toHaveLength(0);
    socket.open();
    expect(socket.sent[0]).toEqual({ v: 1, type: 'join', token: TOKEN });
    socket.deliver(readyFrame({ selfId: 'A' }));
    await promise;
  });

  it('leave sends exactly one leave frame then closes the socket [OUT-02]', async () => {
    const { socket, call } = await connect(readyFrame({ selfId: 'A' }));
    call.leave();
    const leaves = socket.frames().filter((f) => f.type === 'leave');
    expect(leaves).toHaveLength(1);
    expect(socket.closed).toBe(true);
    const lastIndex = socket.sent.findIndex((f) => (f as { type: string }).type === 'leave');
    expect(lastIndex).toBe(socket.sent.length - 1);
  });

  it('setMuted and setHold emit only the changed key [OUT-03]', async () => {
    const { socket, call } = await connect(readyFrame({ selfId: 'A' }));
    call.setMuted(true);
    const muteFrame = socket.frames().find((f) => f.type === 'state');
    expect(muteFrame!.state).toEqual({ muted: true });
    expect((muteFrame!.state as Record<string, unknown>).onHold).toBeUndefined();

    socket.sent.length = 0;
    call.setHold(true);
    const holdFrame = socket.frames().find((f) => f.type === 'state');
    expect(holdFrame!.state).toEqual({ onHold: true });
    expect((holdFrame!.state as Record<string, unknown>).muted).toBeUndefined();
  });

  it('media frames carry to, never from, and target a member [OUT-04/OUT-05]', async () => {
    const s1 = await connect(readyFrame({ selfId: 'B', peers: [participant('A')] }));
    await flush();
    s1.h.lastPc().emitIce({ candidate: 'a', sdpMid: '0', sdpMLineIndex: 0 });

    const s2 = await connect(readyFrame({ selfId: 'A', peers: [participant('B')] }));
    await flush();
    s2.socket.deliver({ v: 1, type: 'offer', from: 'B', sdp: 'remote-offer' });
    await flush();
    s2.h.lastPc().emitIce({ candidate: 'b', sdpMid: '0', sdpMLineIndex: 0 });

    for (const scenario of [s1, s2]) {
      const members = new Set(
        scenario.call.getSnapshot().participants.map((p) => p.participantId)
      );
      for (const frame of mediaFrames(scenario.socket.frames())) {
        expect(typeof frame.to).toBe('string');
        expect('from' in frame).toBe(false);
        expect(members.has(frame.to as string)).toBe(true);
      }
      for (const frame of scenario.socket.frames()) {
        expect(parseClientFrame(frame).success).toBe(true);
      }
    }

    const types1 = s1.socket.frames().map((f) => f.type);
    const types2 = s2.socket.frames().map((f) => f.type);
    expect(types1).toContain('offer');
    expect(types1).toContain('ice');
    expect(types2).toContain('answer');
    expect(types2).toContain('ice');
  });
});
