import { describe, it, expect, vi } from 'vitest';
import { classify, QualityTracker } from '../src/quality.js';
import { connect, readyFrame } from './harness.js';
import { flush, participant } from './fakes.js';

const GOOD = { rttMs: 20, loss: 0, jitterMs: 5 };
const DEGRADED = { rttMs: 250, loss: 0.04, jitterMs: 60 };
const POOR = { rttMs: 500, loss: 0.2, jitterMs: 150 };

describe('quality classifier', () => {
  it('maps stats to GOOD/DEGRADED/POOR by threshold', () => {
    expect(classify(GOOD)).toBe('good');
    expect(classify(DEGRADED)).toBe('degraded');
    expect(classify(POOR)).toBe('poor');
  });

  it('does not flap on a single noisy sample (hysteresis)', () => {
    const t = new QualityTracker();
    expect(t.observe(GOOD).label).toBe('good');
    expect(t.observe(POOR).label).toBe('good');
    expect(t.observe(GOOD).label).toBe('good');
    expect(t.observe(POOR).label).toBe('good');
    expect(t.observe(POOR).label).toBe('poor');
  });

  it('raises the fallback flag on two consecutive POOR', () => {
    const t = new QualityTracker();
    expect(t.observe(POOR).fallback).toBe(false);
    expect(t.observe(POOR).fallback).toBe(true);
    expect(t.observe(GOOD).fallback).toBe(false);
  });
});

describe('quality poll [Q-01/CS-05]', () => {
  it('classifies per peer on the stats cadence and exposes the worst leg', async () => {
    vi.useFakeTimers();
    try {
      const conn = await connect(
        { ...readyFrame({ selfId: 'S', peers: [participant('A'), participant('B')] }) },
        { statsIntervalMs: 2000 }
      );
      conn.h.pcs[0]!.nextStats = GOOD;
      conn.h.pcs[1]!.nextStats = DEGRADED;
      await vi.advanceTimersByTimeAsync(2000);
      await vi.advanceTimersByTimeAsync(2000);
      const snap = conn.call.getSnapshot();
      const a = snap.participants.find((p) => p.participantId === 'A')!;
      const b = snap.participants.find((p) => p.participantId === 'B')!;
      expect(a.quality).toBe('good');
      expect(b.quality).toBe('degraded');
      expect(snap.quality).toBe('degraded');
    } finally {
      vi.useRealTimers();
    }
  });

  it('does not flap the aggregate label on a single poor poll [CS-05]', async () => {
    vi.useFakeTimers();
    try {
      const conn = await connect(
        readyFrame({ selfId: 'S', peers: [participant('A')] }),
        { statsIntervalMs: 2000 }
      );
      const pc = conn.h.pcs[0]!;
      const feed = async (sample: typeof GOOD) => {
        pc.nextStats = sample;
        await vi.advanceTimersByTimeAsync(2000);
        return conn.call.getSnapshot().quality;
      };

      expect(await feed(GOOD)).toBe('good');
      expect(await feed(POOR)).toBe('good');
      expect(await feed(GOOD)).toBe('good');
      expect(await feed(POOR)).toBe('good');
      expect(await feed(POOR)).toBe('poor');
    } finally {
      vi.useRealTimers();
    }
  });

  it('skips a peer whose getStats rejects without throwing', async () => {
    vi.useFakeTimers();
    try {
      const conn = await connect(
        readyFrame({ selfId: 'S', peers: [participant('A')] }),
        { statsIntervalMs: 2000 }
      );
      conn.h.pcs[0]!.getStats = async () => {
        throw new Error('stats unavailable');
      };
      await vi.advanceTimersByTimeAsync(2000);
      expect(conn.call.getSnapshot().participants.find((p) => p.participantId === 'A')!.quality).toBe(
        'good'
      );
    } finally {
      vi.useRealTimers();
    }
  });

  it('sets the fallback flag after two consecutive POOR polls', async () => {
    vi.useFakeTimers();
    try {
      const conn = await connect(
        readyFrame({ selfId: 'S', peers: [participant('A')] }),
        { statsIntervalMs: 2000 }
      );
      conn.h.pcs[0]!.nextStats = POOR;
      await vi.advanceTimersByTimeAsync(2000);
      expect(conn.call.getSnapshot().fallback).toBe(false);
      await vi.advanceTimersByTimeAsync(2000);
      expect(conn.call.getSnapshot().fallback).toBe(true);
    } finally {
      vi.useRealTimers();
    }
  });
});
