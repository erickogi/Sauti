import { describe, it, expect, vi } from 'vitest';
import { Emitter } from '../src/emitter.js';
import type { QoeSample, SautiEvents } from '../src/types.js';
import { connect, readyFrame } from './harness.js';
import { participant } from './fakes.js';

const GOOD = { rttMs: 20, loss: 0, jitterMs: 5 };
const DEGRADED = { rttMs: 250, loss: 0.04, jitterMs: 60 };

describe('emitter has', () => {
  it('reports whether a type has at least one listener', () => {
    const emitter = new Emitter<SautiEvents>();
    expect(emitter.has('qoe-sample')).toBe(false);
    const off = emitter.on('qoe-sample', () => undefined);
    expect(emitter.has('qoe-sample')).toBe(true);
    off();
    expect(emitter.has('qoe-sample')).toBe(false);
  });
});

describe('qoe side channel', () => {
  it('emits one qoe-sample per peer per poll with the classifier numbers and sampledAt', async () => {
    vi.useFakeTimers();
    try {
      const conn = await connect(
        readyFrame({ selfId: 'S', peers: [participant('A'), participant('B')] }),
        { statsIntervalMs: 2000 }
      );
      const samples: QoeSample[] = [];
      conn.call.on('qoe-sample', (s) => samples.push(s));
      conn.h.pcs[0]!.nextStats = GOOD;
      conn.h.pcs[1]!.nextStats = DEGRADED;
      conn.h.now.value = 5_000_000;
      await vi.advanceTimersByTimeAsync(2000);

      expect(samples).toHaveLength(2);
      const a = samples.find((s) => s.participantId === 'A')!;
      const b = samples.find((s) => s.participantId === 'B')!;
      expect(a.rttMs).toBe(GOOD.rttMs);
      expect(a.loss).toBe(GOOD.loss);
      expect(a.jitterMs).toBe(GOOD.jitterMs);
      expect(a.sampledAt).toBe(5_000_000);
      expect(b.rttMs).toBe(DEGRADED.rttMs);
      expect(b.loss).toBe(DEGRADED.loss);
      expect(b.jitterMs).toBe(DEGRADED.jitterMs);
    } finally {
      vi.useRealTimers();
    }
  });

  it('carries the nullable extras through when present', async () => {
    vi.useFakeTimers();
    try {
      const conn = await connect(
        readyFrame({ selfId: 'S', peers: [participant('A')] }),
        { statsIntervalMs: 2000 }
      );
      const samples: QoeSample[] = [];
      conn.call.on('qoe-sample', (s) => samples.push(s));
      conn.h.pcs[0]!.nextStats = {
        rttMs: 30,
        loss: 0.01,
        jitterMs: 8,
        jitterBufferMs: 55,
        fractionLost: 0.02
      };
      await vi.advanceTimersByTimeAsync(2000);

      expect(samples[0]!.jitterBufferMs).toBe(55);
      expect(samples[0]!.fractionLost).toBe(0.02);
    } finally {
      vi.useRealTimers();
    }
  });

  it('leaves absent extras undefined without fabricating them', async () => {
    vi.useFakeTimers();
    try {
      const conn = await connect(
        readyFrame({ selfId: 'S', peers: [participant('A')] }),
        { statsIntervalMs: 2000 }
      );
      const samples: QoeSample[] = [];
      conn.call.on('qoe-sample', (s) => samples.push(s));
      conn.h.pcs[0]!.nextStats = GOOD;
      await vi.advanceTimersByTimeAsync(2000);

      expect(samples[0]!.jitterBufferMs).toBeUndefined();
      expect(samples[0]!.fractionLost).toBeUndefined();
    } finally {
      vi.useRealTimers();
    }
  });

  it('emits nothing and does not change quality when no one subscribes', async () => {
    vi.useFakeTimers();
    try {
      const conn = await connect(
        readyFrame({ selfId: 'S', peers: [participant('A')] }),
        { statsIntervalMs: 2000 }
      );
      const changed: unknown[] = [];
      conn.call.on('quality-changed', (e) => changed.push(e));
      conn.h.pcs[0]!.nextStats = DEGRADED;
      await vi.advanceTimersByTimeAsync(2000);
      await vi.advanceTimersByTimeAsync(2000);

      expect(conn.call.getSnapshot().participants.find((p) => p.participantId === 'A')!.quality).toBe(
        'degraded'
      );
      expect(changed).toHaveLength(1);
    } finally {
      vi.useRealTimers();
    }
  });

  it('skips a peer whose getStats rejects while still emitting for the others', async () => {
    vi.useFakeTimers();
    try {
      const conn = await connect(
        readyFrame({ selfId: 'S', peers: [participant('A'), participant('B')] }),
        { statsIntervalMs: 2000 }
      );
      const samples: QoeSample[] = [];
      conn.call.on('qoe-sample', (s) => samples.push(s));
      conn.h.pcs[0]!.getStats = async () => {
        throw new Error('stats unavailable');
      };
      conn.h.pcs[1]!.nextStats = GOOD;
      await vi.advanceTimersByTimeAsync(2000);

      expect(samples).toHaveLength(1);
      expect(samples[0]!.participantId).toBe('B');
    } finally {
      vi.useRealTimers();
    }
  });

  it('does not emit for a peer that has left', async () => {
    vi.useFakeTimers();
    try {
      const conn = await connect(
        readyFrame({ selfId: 'S', peers: [participant('A'), participant('B')] }),
        { statsIntervalMs: 2000 }
      );
      const samples: QoeSample[] = [];
      conn.call.on('qoe-sample', (s) => samples.push(s));
      conn.socket.deliver({ v: 1, type: 'participant-left', participantId: 'A' });
      conn.h.pcs[1]!.nextStats = GOOD;
      await vi.advanceTimersByTimeAsync(2000);

      expect(samples.every((s) => s.participantId !== 'A')).toBe(true);
      expect(samples).toHaveLength(1);
      expect(samples[0]!.participantId).toBe('B');
    } finally {
      vi.useRealTimers();
    }
  });
});
