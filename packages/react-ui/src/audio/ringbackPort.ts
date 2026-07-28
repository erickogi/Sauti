export interface RingbackPort {
  start(): void;
  stop(): void;
  close(): void;
}

export interface RingbackToneOptions {
  frequencies?: number[];
  cadence?: { onMs: number; offMs: number };
  gain?: number;
}

export const DEFAULT_RINGBACK_TONE: Required<RingbackToneOptions> = {
  frequencies: [440, 480],
  cadence: { onMs: 2000, offMs: 4000 },
  gain: 0.08
};

const noopRingbackPort: RingbackPort = Object.freeze({
  start(): void {},
  stop(): void {},
  close(): void {}
});

type AudioContextConstructor = new () => AudioContext;

function resolveAudioContextConstructor(): AudioContextConstructor | undefined {
  const scope = globalThis as {
    AudioContext?: AudioContextConstructor;
    webkitAudioContext?: AudioContextConstructor;
  };
  return scope.AudioContext ?? scope.webkitAudioContext;
}

class WebAudioRingbackPort implements RingbackPort {
  private readonly constructAudioContext: AudioContextConstructor;
  private readonly frequencies: number[];
  private readonly onMs: number;
  private readonly offMs: number;
  private readonly audibleGain: number;
  private context: AudioContext | null = null;
  private gainNode: GainNode | null = null;
  private oscillators: OscillatorNode[] = [];
  private cadenceTimer: ReturnType<typeof setTimeout> | null = null;
  private ringing = false;
  private closed = false;

  constructor(constructAudioContext: AudioContextConstructor, tone?: RingbackToneOptions) {
    this.constructAudioContext = constructAudioContext;
    this.frequencies = tone?.frequencies ?? DEFAULT_RINGBACK_TONE.frequencies;
    this.onMs = tone?.cadence?.onMs ?? DEFAULT_RINGBACK_TONE.cadence.onMs;
    this.offMs = tone?.cadence?.offMs ?? DEFAULT_RINGBACK_TONE.cadence.offMs;
    this.audibleGain = tone?.gain ?? DEFAULT_RINGBACK_TONE.gain;
  }

  start(): void {
    if (this.closed || this.ringing) return;
    this.ringing = true;
    const context = this.ensureContext();
    if (context.state === 'suspended') void context.resume();
    this.audibleOn();
  }

  stop(): void {
    if (this.closed || !this.ringing) return;
    this.ringing = false;
    this.clearCadence();
    this.setGain(0);
  }

  close(): void {
    if (this.closed) return;
    this.closed = true;
    this.ringing = false;
    this.clearCadence();
    for (const oscillator of this.oscillators) oscillator.stop();
    this.oscillators = [];
    this.gainNode = null;
    if (this.context != null) {
      void this.context.close();
      this.context = null;
    }
  }

  private ensureContext(): AudioContext {
    if (this.context == null) {
      const context = new this.constructAudioContext();
      const gainNode = context.createGain();
      gainNode.gain.value = 0;
      gainNode.connect(context.destination);
      for (const frequency of this.frequencies) {
        const oscillator = context.createOscillator();
        oscillator.frequency.value = frequency;
        oscillator.connect(gainNode);
        oscillator.start();
        this.oscillators.push(oscillator);
      }
      this.gainNode = gainNode;
      this.context = context;
    }
    return this.context;
  }

  private audibleOn(): void {
    this.setGain(this.audibleGain);
    this.cadenceTimer = setTimeout(() => this.audibleOff(), this.onMs);
  }

  private audibleOff(): void {
    this.setGain(0);
    this.cadenceTimer = setTimeout(() => this.audibleOn(), this.offMs);
  }

  private setGain(value: number): void {
    if (this.gainNode != null) this.gainNode.gain.value = value;
  }

  private clearCadence(): void {
    if (this.cadenceTimer != null) {
      clearTimeout(this.cadenceTimer);
      this.cadenceTimer = null;
    }
  }
}

export function createRingbackPort(tone?: RingbackToneOptions): RingbackPort {
  const constructAudioContext = resolveAudioContextConstructor();
  if (constructAudioContext == null) return noopRingbackPort;
  return new WebAudioRingbackPort(constructAudioContext, tone);
}
