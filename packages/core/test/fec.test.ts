import { describe, it, expect } from 'vitest';
import { connect, readyFrame } from './harness.js';
import { FakePeerConnection, flush, makeRuntime, participant } from './fakes.js';

const OPUS_OFFER = [
  'v=0',
  'm=audio 9 UDP/TLS/RTP/SAVPF 111',
  'a=rtpmap:111 opus/48000/2',
  'a=fmtp:111 minptime=10'
].join('\r\n');

const OPUS_ANSWER = [
  'v=0',
  'm=audio 9 UDP/TLS/RTP/SAVPF 111',
  'a=rtpmap:111 opus/48000/2',
  'a=fmtp:111 minptime=10;stereo=0'
].join('\r\n');

function opusRuntime() {
  return makeRuntime({
    createPeerConnection: (config) => {
      const pc = new FakePeerConnection(config);
      pc.createOffer = async () => ({ type: 'offer', sdp: OPUS_OFFER });
      pc.createAnswer = async () => ({ type: 'answer', sdp: OPUS_ANSWER });
      return pc;
    }
  });
}

describe('opus fec negotiation wiring [FEC]', () => {
  it('produces a byte-identical created offer when no opus config is set [FEC-01]', async () => {
    const conn = await connect(readyFrame({ selfId: 'B', peers: [participant('A')] }));
    await flush();
    const pc = conn.h.pcs[0]!;
    const offer = conn.socket.frames().find((f) => f.type === 'offer');
    expect(offer!.sdp).toBe('offer-sdp');
    expect(pc.localDescriptions.some((d) => d?.type === 'offer' && d.sdp === 'offer-sdp')).toBe(
      true
    );
  });

  it('produces a byte-identical created answer and leaves the remote untouched by default [FEC-02]', async () => {
    const conn = await connect(readyFrame({ selfId: 'A', peers: [participant('B')] }));
    conn.socket.deliver({ v: 1, type: 'offer', from: 'B', sdp: 'remote-offer' });
    await flush();
    const pc = conn.h.pcs[0]!;
    const answer = conn.socket.frames().find((f) => f.type === 'answer');
    expect(answer!.sdp).toBe('answer-sdp');
    expect(pc.remoteDescriptions.some((d) => d.type === 'offer' && d.sdp === 'remote-offer')).toBe(
      true
    );
  });

  it('munges the created offer symmetrically when fec is enabled [FEC-03]', async () => {
    const h = opusRuntime();
    const conn = await connect(
      readyFrame({ selfId: 'B', peers: [participant('A')] }),
      { opus: { fec: true } },
      h
    );
    await flush();
    const pc = h.pcs[0]!;
    const offer = conn.socket.frames().find((f) => f.type === 'offer');
    expect(offer!.sdp).toContain('a=fmtp:111 minptime=10;useinbandfec=1');
    const local = pc.localDescriptions.find((d) => d?.type === 'offer');
    expect(local!.sdp).toBe(offer!.sdp);
  });

  it('munges the created answer symmetrically and never transforms the remote offer [FEC-04]', async () => {
    const h = opusRuntime();
    const conn = await connect(
      readyFrame({ selfId: 'A', peers: [participant('B')] }),
      { opus: { fec: true } },
      h
    );
    conn.socket.deliver({ v: 1, type: 'offer', from: 'B', sdp: OPUS_OFFER });
    await flush();
    const pc = h.pcs[0]!;
    const answer = conn.socket.frames().find((f) => f.type === 'answer');
    expect(answer!.sdp).toContain('a=fmtp:111 minptime=10;stereo=0;useinbandfec=1');
    const localAnswer = pc.localDescriptions.find((d) => d?.type === 'answer');
    expect(localAnswer!.sdp).toBe(answer!.sdp);
    const storedRemote = pc.remoteDescriptions.find((d) => d.type === 'offer');
    expect(storedRemote!.sdp).toBe(OPUS_OFFER);
    expect(storedRemote!.sdp).not.toContain('useinbandfec');
  });

  it('does not honor dtx in 2B wiring even when it is set [FEC-05]', async () => {
    const h = opusRuntime();
    const conn = await connect(
      readyFrame({ selfId: 'B', peers: [participant('A')] }),
      { opus: { dtx: true } },
      h
    );
    await flush();
    const offer = conn.socket.frames().find((f) => f.type === 'offer');
    expect(offer!.sdp).toBe(OPUS_OFFER);
    expect(offer!.sdp).not.toContain('usedtx');
  });
});
