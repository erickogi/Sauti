import { describe, it, expect, vi } from 'vitest';
import { makeCall, readyFrame, URL, TOKEN } from './harness.js';
import { flush, participant, type RuntimeHarness } from './fakes.js';
import type { SautiCall } from '../src/call.js';
import type { JoinOptions, SautiOptions } from '../src/types.js';
import type { FakeWebSocket } from './fakes.js';

interface Joined {
  h: RuntimeHarness;
  call: SautiCall;
  socket: FakeWebSocket;
}

async function join(
  ready: ReturnType<typeof readyFrame>,
  joinOptions: Partial<JoinOptions> = {},
  options: SautiOptions = {}
): Promise<Joined> {
  const { h, call } = makeCall(options);
  const promise = call.join({ url: URL, token: TOKEN, ...joinOptions });
  await flush();
  const socket = h.lastSocket();
  socket.open();
  socket.deliver(ready);
  await promise;
  return { h, call, socket };
}

function leaveFrames(socket: FakeWebSocket): Record<string, unknown>[] {
  return socket.frames().filter((f) => f.type === 'leave');
}

describe('endWhenLastPeerLeaves', () => {
  it('defaults off: a peer joining then leaving never auto-leaves', async () => {
    const conn = await join(readyFrame({ selfId: 'A', peers: [participant('B')] }));
    conn.socket.deliver({ v: 1, type: 'participant-left', participantId: 'B' });
    await flush();
    expect(leaveFrames(conn.socket)).toHaveLength(0);
    expect(conn.call.getSnapshot().phase).toBe('connected');
    expect(conn.call.getSnapshot().participants.map((p) => p.participantId)).toContain('A');
  });

  it('enabled: the last remote leaving triggers leave() and phase left', async () => {
    const conn = await join(
      readyFrame({ selfId: 'A', peers: [participant('B')] }),
      { endWhenLastPeerLeaves: true }
    );
    conn.socket.deliver({ v: 1, type: 'participant-left', participantId: 'B' });
    await flush();
    expect(leaveFrames(conn.socket)).toHaveLength(1);
    expect(conn.socket.closed).toBe(true);
    expect(conn.call.getSnapshot().phase).toBe('left');
  });

  it('enabled: self alone from the start never auto-leaves', async () => {
    const conn = await join(readyFrame({ selfId: 'A' }), { endWhenLastPeerLeaves: true });
    await flush();
    expect(leaveFrames(conn.socket)).toHaveLength(0);
    expect(conn.call.getSnapshot().phase).toBe('connected');
  });

  it('enabled: with two remotes, the call ends only when the last one leaves', async () => {
    const conn = await join(
      readyFrame({ selfId: 'A', peers: [participant('B'), participant('C')] }),
      { endWhenLastPeerLeaves: true }
    );

    conn.socket.deliver({ v: 1, type: 'participant-left', participantId: 'B' });
    await flush();
    expect(leaveFrames(conn.socket)).toHaveLength(0);
    expect(conn.call.getSnapshot().phase).toBe('connected');

    conn.socket.deliver({ v: 1, type: 'participant-left', participantId: 'C' });
    await flush();
    expect(leaveFrames(conn.socket)).toHaveLength(1);
    expect(conn.call.getSnapshot().phase).toBe('left');
  });

  it('enabled: an unreachable peer never ends the call', async () => {
    const conn = await join(
      readyFrame({ selfId: 'A', peers: [participant('B')] }),
      { endWhenLastPeerLeaves: true }
    );
    conn.socket.deliver({ v: 1, type: 'participant-unreachable', participantId: 'B' });
    await flush();
    expect(leaveFrames(conn.socket)).toHaveLength(0);
    expect(conn.call.getSnapshot().phase).toBe('connected');
    const b = conn.call.getSnapshot().participants.find((p) => p.participantId === 'B')!;
    expect(b.connectionState).toBe('reconnecting');
  });

  it('enabled: a peer that vanishes on a resumed ready ends the call', async () => {
    vi.useFakeTimers();
    try {
      const conn = await join(
        readyFrame({ selfId: 'A', peers: [participant('B')] }),
        { endWhenLastPeerLeaves: true },
        { reconnectBaseMs: 10 }
      );
      conn.socket.drop();
      await vi.advanceTimersByTimeAsync(50);
      const resumed = conn.h.lastSocket();
      resumed.open();
      resumed.deliver(readyFrame({ selfId: 'A', peers: [], resumed: true }));
      await flush();
      expect(leaveFrames(resumed)).toHaveLength(1);
      expect(conn.call.getSnapshot().phase).toBe('left');
    } finally {
      vi.useRealTimers();
    }
  });

  it('enabled: a mid-call join then leave ends the call', async () => {
    const conn = await join(readyFrame({ selfId: 'A' }), { endWhenLastPeerLeaves: true });

    conn.socket.deliver({ v: 1, type: 'participant-joined', participant: participant('C') });
    await flush();
    expect(leaveFrames(conn.socket)).toHaveLength(0);
    expect(conn.call.getSnapshot().phase).toBe('connected');

    conn.socket.deliver({ v: 1, type: 'participant-left', participantId: 'C' });
    await flush();
    expect(leaveFrames(conn.socket)).toHaveLength(1);
    expect(conn.call.getSnapshot().phase).toBe('left');
  });
});
