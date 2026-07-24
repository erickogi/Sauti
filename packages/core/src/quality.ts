import type { Quality, StatsSample } from './types.js';

const HYSTERESIS = 2;

export function classify(sample: StatsSample): Quality {
  if (sample.loss > 0.1 || sample.rttMs > 400 || sample.jitterMs > 100) {
    return 'poor';
  }
  if (sample.loss > 0.03 || sample.rttMs > 200 || sample.jitterMs > 50) {
    return 'degraded';
  }
  return 'good';
}

export class QualityTracker {
  private label: Quality = 'good';
  private pending: Quality = 'good';
  private pendingCount = 0;
  private consecutivePoor = 0;

  observe(sample: StatsSample): { label: Quality; fallback: boolean } {
    const raw = classify(sample);

    if (raw === 'poor') this.consecutivePoor += 1;
    else this.consecutivePoor = 0;

    if (raw === this.label) {
      this.pending = raw;
      this.pendingCount = 0;
    } else if (raw === this.pending) {
      this.pendingCount += 1;
      if (this.pendingCount >= HYSTERESIS) {
        this.label = raw;
        this.pendingCount = 0;
      }
    } else {
      this.pending = raw;
      this.pendingCount = 1;
    }

    return { label: this.label, fallback: this.consecutivePoor >= 2 };
  }
}
