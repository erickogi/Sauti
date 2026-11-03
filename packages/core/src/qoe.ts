import type { Quality } from './types.js';

export interface QoeSummary {
  samples: number;
  avgRttMs: number;
  maxRttMs: number;
  avgJitterMs: number;
  maxJitterMs: number;
  avgLoss: number;
  maxLoss: number;
  secGood: number;
  secDegraded: number;
  secPoor: number;
  worst: Quality;
}

const RANK: Record<Quality, number> = { good: 0, degraded: 1, poor: 2 };

function round(value: number, places: number): number {
  const f = 10 ** places;
  return Math.round(value * f) / f;
}

function clamp(value: number, lo: number, hi: number): number {
  if (!Number.isFinite(value)) return lo;
  return Math.min(hi, Math.max(lo, value));
}

export class QoeAggregator {
  private samples = 0;
  private rttSum = 0;
  private rttCount = 0;
  private rttMax = 0;
  private jitterSum = 0;
  private jitterMax = 0;
  private lossSum = 0;
  private lossMax = 0;
  private secGood = 0;
  private secDegraded = 0;
  private secPoor = 0;
  private worst: Quality = 'good';

  constructor(private readonly intervalSec: number) {}

  observe(band: Quality, rttMs: number, fractionLost: number, jitterMs: number): void {
    this.samples += 1;

    if (Number.isFinite(rttMs) && rttMs > 0) {
      this.rttSum += rttMs;
      this.rttCount += 1;
      if (rttMs > this.rttMax) this.rttMax = rttMs;
    }

    if (Number.isFinite(jitterMs) && jitterMs >= 0) {
      this.jitterSum += jitterMs;
      if (jitterMs > this.jitterMax) this.jitterMax = jitterMs;
    }

    const loss = Number.isFinite(fractionLost) ? fractionLost : 0;
    this.lossSum += loss;
    if (loss > this.lossMax) this.lossMax = loss;

    if (band === 'poor') this.secPoor += this.intervalSec;
    else if (band === 'degraded') this.secDegraded += this.intervalSec;
    else this.secGood += this.intervalSec;

    if (RANK[band] > RANK[this.worst]) this.worst = band;
  }

  hasSamples(): boolean {
    return this.samples > 0;
  }

  summary(): QoeSummary | null {
    if (this.samples === 0) return null;
    return {
      samples: this.samples,
      avgRttMs: round(clamp(this.rttCount ? this.rttSum / this.rttCount : 0, 0, 5000), 1),
      maxRttMs: round(clamp(this.rttMax, 0, 5000), 1),
      avgJitterMs: round(clamp(this.jitterSum / this.samples, 0, 2000), 1),
      maxJitterMs: round(clamp(this.jitterMax, 0, 2000), 1),
      avgLoss: round(clamp(this.lossSum / this.samples, 0, 1), 4),
      maxLoss: round(clamp(this.lossMax, 0, 1), 4),
      secGood: round(clamp(this.secGood, 0, 86400), 1),
      secDegraded: round(clamp(this.secDegraded, 0, 86400), 1),
      secPoor: round(clamp(this.secPoor, 0, 86400), 1),
      worst: this.worst
    };
  }

  reset(): void {
    this.samples = 0;
    this.rttSum = 0;
    this.rttCount = 0;
    this.rttMax = 0;
    this.jitterSum = 0;
    this.jitterMax = 0;
    this.lossSum = 0;
    this.lossMax = 0;
    this.secGood = 0;
    this.secDegraded = 0;
    this.secPoor = 0;
    this.worst = 'good';
  }
}
