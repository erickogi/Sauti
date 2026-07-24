import { describe, it, expect } from 'vitest';
import { isPolite } from '@sauti/protocol';
import { connect, readyFrame } from './harness.js';
import { flush, participant } from './fakes.js';

describe('perfect negotiation', () => {
  it('makes exactly one side of each pair the offerer by id order [NEG-01]', async () => {
    const conn = await connect(
      readyFrame({ selfId: 'B', peers: [participant('A'), participant('C')] })
    );
    await flush();
    const pcForA = conn.h.pcs[0]!;
    const pcForC = conn.h.pcs[1]!;
    expect(isPolite('B', 'A')).toBe(false);
    expect(isPolite('B', 'C')).toBe(true);
    expect(pcForA.offersCreated.length).toBe(1);
    expect(pcForC.offersCreated.length).toBe(0);
    const offers = conn.socket.frames().filter((f) => f.type === 'offer');
    expect(offers).toEqual([{ v: 1, type: 'offer', to: 'A', sdp: 'offer-sdp' }]);
  });

  it('the polite peer rolls back on collision, the impolite ignores [NEG-02]', async () => {
    const polite = await connect(readyFrame({ selfId: 'A', peers: [participant('B')] }));
    const politePc = polite.h.pcs[0]!;
    politePc.emitNegotiationNeeded();
    polite.socket.deliver({ v: 1, type: 'offer', from: 'B', sdp: 'glare' });
    await flush();
    expect(politePc.localDescriptions.some((d) => d?.type === 'rollback')).toBe(true);
    expect(politePc.remoteDescriptions.some((d) => d.sdp === 'glare')).toBe(true);

    const impolite = await connect(readyFrame({ selfId: 'B', peers: [participant('A')] }));
    const impolitePc = impolite.h.pcs[0]!;
    impolitePc.emitNegotiationNeeded();
    impolite.socket.deliver({ v: 1, type: 'offer', from: 'A', sdp: 'glare2' });
    await flush();
    expect(impolitePc.remoteDescriptions.some((d) => d.sdp === 'glare2')).toBe(false);
  });

  it('negotiationneeded creates one offer at a time guarded by makingOffer [NEG-03]', async () => {
    const conn = await connect(readyFrame({ selfId: 'A', peers: [participant('B')] }));
    const pc = conn.h.pcs[0]!;
    pc.emitNegotiationNeeded();
    pc.emitNegotiationNeeded();
    await flush();
    expect(pc.offersCreated).toHaveLength(1);
    pc.emitNegotiationNeeded();
    await flush();
    expect(pc.offersCreated).toHaveLength(2);
  });

  it('ice errors are swallowed only while an offer is ignored [NEG-04]', async () => {
    const ignored = await connect(readyFrame({ selfId: 'B', peers: [participant('A')] }));
    const ignoredPc = ignored.h.pcs[0]!;
    const ignoredErrors: string[] = [];
    ignored.call.on('error', (e) => ignoredErrors.push(e.code));
    ignoredPc.emitNegotiationNeeded();
    ignored.socket.deliver({ v: 1, type: 'offer', from: 'A', sdp: 'glare' });
    await flush();
    ignoredPc.rejectNextIce = true;
    ignored.socket.deliver({
      v: 1,
      type: 'ice',
      from: 'A',
      candidate: { candidate: 'bad', sdpMid: '0', sdpMLineIndex: 0 }
    });
    await flush();
    expect(ignoredErrors).toHaveLength(0);

    const open = await connect(readyFrame({ selfId: 'A', peers: [participant('B')] }));
    const openPc = open.h.pcs[0]!;
    const openErrors: string[] = [];
    open.call.on('error', () => openErrors.push('e'));
    openPc.rejectNextIce = true;
    open.socket.deliver({
      v: 1,
      type: 'ice',
      from: 'B',
      candidate: { candidate: 'bad', sdpMid: '0', sdpMLineIndex: 0 }
    });
    await flush();
    expect(openErrors).toHaveLength(1);
  });
});
