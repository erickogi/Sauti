import { describe, it, expect, vi } from 'vitest';
import { classify, QualityTracker } from '../src/quality.js';
import type { Quality, StatsSample } from '../src/types.js';
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

describe('opus dtx negotiation wiring [DTX]', () => {
  it('leaves the created offer untouched when dtx is unset [DTX-01]', async () => {
    const h = opusRuntime();
    const conn = await connect(readyFrame({ selfId: 'B', peers: [participant('A')] }), {}, h);
    await flush();
    const pc = h.pcs[0]!;
    const offer = conn.socket.frames().find((f) => f.type === 'offer');
    expect(offer!.sdp).toBe(OPUS_OFFER);
    expect(offer!.sdp).not.toContain('usedtx');
    const local = pc.localDescriptions.find((d) => d?.type === 'offer');
    expect(local!.sdp).toBe(OPUS_OFFER);
  });

  it('munges the created offer with usedtx when dtx is enabled [DTX-02]', async () => {
    const h = opusRuntime();
    const conn = await connect(
      readyFrame({ selfId: 'B', peers: [participant('A')] }),
      { opus: { dtx: true } },
      h
    );
    await flush();
    const pc = h.pcs[0]!;
    const offer = conn.socket.frames().find((f) => f.type === 'offer');
    expect(offer!.sdp).toContain('a=fmtp:111 minptime=10;usedtx=1');
    const local = pc.localDescriptions.find((d) => d?.type === 'offer');
    expect(local!.sdp).toBe(offer!.sdp);
  });

  it('munges the created answer with usedtx and never transforms the remote offer [DTX-03]', async () => {
    const h = opusRuntime();
    const conn = await connect(
      readyFrame({ selfId: 'A', peers: [participant('B')] }),
      { opus: { dtx: true } },
      h
    );
    conn.socket.deliver({ v: 1, type: 'offer', from: 'B', sdp: OPUS_OFFER });
    await flush();
    const pc = h.pcs[0]!;
    const answer = conn.socket.frames().find((f) => f.type === 'answer');
    expect(answer!.sdp).toContain('a=fmtp:111 minptime=10;stereo=0;usedtx=1');
    const localAnswer = pc.localDescriptions.find((d) => d?.type === 'answer');
    expect(localAnswer!.sdp).toBe(answer!.sdp);
    const storedRemote = pc.remoteDescriptions.find((d) => d.type === 'offer');
    expect(storedRemote!.sdp).toBe(OPUS_OFFER);
    expect(storedRemote!.sdp).not.toContain('usedtx');
  });

  it('carries both usedtx and useinbandfec on the created offer fmtp line [DTX-04]', async () => {
    const h = opusRuntime();
    const conn = await connect(
      readyFrame({ selfId: 'B', peers: [participant('A')] }),
      { opus: { dtx: true, fec: true } },
      h
    );
    await flush();
    const offer = conn.socket.frames().find((f) => f.type === 'offer');
    expect(offer!.sdp).toContain('a=fmtp:111 minptime=10;useinbandfec=1;usedtx=1');
  });

  it('carries both usedtx and useinbandfec on the created answer fmtp line [DTX-05]', async () => {
    const h = opusRuntime();
    const conn = await connect(
      readyFrame({ selfId: 'A', peers: [participant('B')] }),
      { opus: { dtx: true, fec: true } },
      h
    );
    conn.socket.deliver({ v: 1, type: 'offer', from: 'B', sdp: OPUS_OFFER });
    await flush();
    const answer = conn.socket.frames().find((f) => f.type === 'answer');
    expect(answer!.sdp).toContain('a=fmtp:111 minptime=10;stereo=0;useinbandfec=1;usedtx=1');
  });
});

const SILENCE: StatsSample[] = [
  { rttMs: 30, loss: 0, jitterMs: 10 },
  { rttMs: 30, loss: 0.02, jitterMs: 10 },
  { rttMs: 30, loss: 0.05, jitterMs: 10 },
  { rttMs: 30, loss: 0.08, jitterMs: 10 },
  { rttMs: 30, loss: 0.12, jitterMs: 10 },
  { rttMs: 30, loss: 0.15, jitterMs: 10 },
  { rttMs: 30, loss: 0.2, jitterMs: 10 }
];

describe('dtx silence loss-math characterization [DTX-LOSS]', () => {
  it('classifies each silence sample by its cumulative loss alone [DTX-LOSS-01]', () => {
    const raw = SILENCE.map(classify);
    expect(raw).toEqual([
      'good',
      'good',
      'degraded',
      'degraded',
      'poor',
      'poor',
      'poor'
    ] satisfies Quality[]);
  });

  it('tracks rising silence loss into degraded then poor with fallback after the thresholds [DTX-LOSS-02]', () => {
    const tracker = new QualityTracker();
    const observed = SILENCE.map((sample) => tracker.observe(sample));
    expect(observed).toEqual([
      { label: 'good', fallback: false },
      { label: 'good', fallback: false },
      { label: 'good', fallback: false },
      { label: 'degraded', fallback: false },
      { label: 'degraded', fallback: false },
      { label: 'poor', fallback: true },
      { label: 'poor', fallback: true }
    ]);
  });
});

describe('dtx silence does not trip dead-peer [DTX-DEAD]', () => {
  it('updates quality from a high-loss stats sample without marking the peer left or unreachable [DTX-DEAD-01]', async () => {
    vi.useFakeTimers();
    try {
      const conn = await connect(
        readyFrame({ selfId: 'S', peers: [participant('A')] }),
        { statsIntervalMs: 2000 }
      );
      const events: string[] = [];
      conn.call.on('left', () => events.push('left'));
      conn.call.on('unreachable', () => events.push('unreachable'));
      conn.h.pcs[0]!.nextStats = { rttMs: 30, loss: 0.2, jitterMs: 10 };

      await vi.advanceTimersByTimeAsync(2000);
      await vi.advanceTimersByTimeAsync(2000);
      await vi.advanceTimersByTimeAsync(2000);

      const snap = conn.call.getSnapshot();
      const a = snap.participants.find((p) => p.participantId === 'A')!;
      expect(a.quality).toBe('poor');
      expect(a.connectionState).not.toBe('reconnecting');
      expect(snap.fallback).toBe(true);
      expect(snap.phase).toBe('connected');
      expect(events).toEqual([]);
    } finally {
      vi.useRealTimers();
    }
  });
});
