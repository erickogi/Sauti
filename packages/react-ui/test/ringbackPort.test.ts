import { describe, it, expect, afterEach, beforeEach, vi } from 'vitest';
import {
  createRingbackPort,
  DEFAULT_RINGBACK_TONE,
  type RingbackPort
} from '../src/audio/ringbackPort.js';

type AudioScope = {
  AudioContext?: unknown;
  webkitAudioContext?: unknown;
};

const scope = globalThis as AudioScope;

function withoutAudioContext<T>(run: () => T): T {
  const savedStandard = scope.AudioContext;
  const savedWebkit = scope.webkitAudioContext;
  delete scope.AudioContext;
  delete scope.webkitAudioContext;
  try {
    return run();
  } finally {
    if (savedStandard !== undefined) scope.AudioContext = savedStandard;
    if (savedWebkit !== undefined) scope.webkitAudioContext = savedWebkit;
  }
}

afterEach(() => {
  delete scope.AudioContext;
  delete scope.webkitAudioContext;
});

describe('DEFAULT_RINGBACK_TONE', () => {
  it('carries the 440/480 pair and a 2s-on 4s-off cadence', () => {
    expect(DEFAULT_RINGBACK_TONE.frequencies).toEqual([440, 480]);
    expect(DEFAULT_RINGBACK_TONE.cadence).toEqual({ onMs: 2000, offMs: 4000 });
    expect(DEFAULT_RINGBACK_TONE.gain).toBeGreaterThan(0);
    expect(DEFAULT_RINGBACK_TONE.gain).toBeLessThan(1);
  });
});

describe('createRingbackPort without an AudioContext', () => {
  it('returns a port whose start, stop and close never throw', () => {
    withoutAudioContext(() => {
      const port = createRingbackPort();
      expect(() => port.start()).not.toThrow();
      expect(() => port.stop()).not.toThrow();
      expect(() => port.close()).not.toThrow();
    });
  });

  it('is idempotent and safe under repeated and interleaved calls', () => {
    withoutAudioContext(() => {
      const port = createRingbackPort();
      expect(() => {
        port.start();
        port.start();
        port.stop();
        port.stop();
        port.start();
        port.close();
        port.close();
        port.start();
        port.stop();
      }).not.toThrow();
    });
  });

  it('ignores tone overrides on the silent path and still returns a usable port', () => {
    withoutAudioContext(() => {
      const port: RingbackPort = createRingbackPort({
        frequencies: [660],
        cadence: { onMs: 500, offMs: 500 },
        gain: 0.5
      });
      expect(() => {
        port.start();
        port.stop();
        port.close();
      }).not.toThrow();
    });
  });
});

interface FakeGainNode {
  gain: { value: number };
  connect: ReturnType<typeof vi.fn>;
}

interface FakeOscillatorNode {
  frequency: { value: number };
  connect: ReturnType<typeof vi.fn>;
  start: ReturnType<typeof vi.fn>;
  stop: ReturnType<typeof vi.fn>;
}

interface FakeAudioContext {
  destination: object;
  state: string;
  resume: ReturnType<typeof vi.fn>;
  close: ReturnType<typeof vi.fn>;
  createGain: ReturnType<typeof vi.fn>;
  createOscillator: ReturnType<typeof vi.fn>;
  gains: FakeGainNode[];
  oscillators: FakeOscillatorNode[];
}

function installAudioContext(initialState = 'running'): FakeAudioContext[] {
  const contexts: FakeAudioContext[] = [];
  class Context implements FakeAudioContext {
    destination = {};
    state = initialState;
    gains: FakeGainNode[] = [];
    oscillators: FakeOscillatorNode[] = [];
    resume = vi.fn(async () => {
      this.state = 'running';
    });
    close = vi.fn(async () => {
      this.state = 'closed';
    });
    createGain = vi.fn((): FakeGainNode => {
      const node: FakeGainNode = { gain: { value: 0 }, connect: vi.fn() };
      this.gains.push(node);
      return node;
    });
    createOscillator = vi.fn((): FakeOscillatorNode => {
      const node: FakeOscillatorNode = {
        frequency: { value: 0 },
        connect: vi.fn(),
        start: vi.fn(),
        stop: vi.fn()
      };
      this.oscillators.push(node);
      return node;
    });
    constructor() {
      contexts.push(this);
    }
  }
  scope.AudioContext = Context;
  return contexts;
}

function onlyContext(contexts: FakeAudioContext[]): FakeAudioContext {
  expect(contexts).toHaveLength(1);
  const context = contexts[0];
  if (context == null) throw new Error('no audio context was constructed');
  return context;
}

describe('createRingbackPort with a real WebAudio port', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('builds one context with one started oscillator per default frequency', () => {
    const contexts = installAudioContext();
    const port = createRingbackPort();

    port.start();

    const context = onlyContext(contexts);
    expect(context.createGain).toHaveBeenCalledTimes(1);
    expect(context.oscillators).toHaveLength(DEFAULT_RINGBACK_TONE.frequencies.length);
    const gain = context.gains[0];
    if (gain == null) throw new Error('no gain node was constructed');
    expect(gain.connect).toHaveBeenCalledWith(context.destination);
    expect(gain.gain.value).toBe(DEFAULT_RINGBACK_TONE.gain);
    context.oscillators.forEach((oscillator, index) => {
      expect(oscillator.frequency.value).toBe(DEFAULT_RINGBACK_TONE.frequencies[index]);
      expect(oscillator.connect).toHaveBeenCalledWith(gain);
      expect(oscillator.start).toHaveBeenCalledTimes(1);
    });

    port.close();
  });

  it('resumes a suspended context on start', () => {
    const contexts = installAudioContext('suspended');
    const port = createRingbackPort();

    port.start();

    const context = onlyContext(contexts);
    expect(context.resume).toHaveBeenCalledTimes(1);
    expect(context.state).toBe('running');

    port.close();
  });

  it('leaves a running context alone on start', () => {
    const contexts = installAudioContext('running');
    const port = createRingbackPort();

    port.start();

    expect(onlyContext(contexts).resume).not.toHaveBeenCalled();

    port.close();
  });

  it('alternates the cadence and always keeps one timer pending while ringing', () => {
    const contexts = installAudioContext();
    const port = createRingbackPort();

    port.start();
    const context = onlyContext(contexts);
    const gain = context.gains[0];
    if (gain == null) throw new Error('no gain node was constructed');

    expect(gain.gain.value).toBe(DEFAULT_RINGBACK_TONE.gain);
    expect(vi.getTimerCount()).toBe(1);

    vi.advanceTimersByTime(DEFAULT_RINGBACK_TONE.cadence.onMs);
    expect(gain.gain.value).toBe(0);
    expect(vi.getTimerCount()).toBe(1);

    vi.advanceTimersByTime(DEFAULT_RINGBACK_TONE.cadence.offMs);
    expect(gain.gain.value).toBe(DEFAULT_RINGBACK_TONE.gain);
    expect(vi.getTimerCount()).toBe(1);

    port.close();
  });

  it('stops by clearing the pending cadence timer and silencing the gain', () => {
    const contexts = installAudioContext();
    const port = createRingbackPort();

    port.start();
    const context = onlyContext(contexts);
    const gain = context.gains[0];
    if (gain == null) throw new Error('no gain node was constructed');

    vi.advanceTimersByTime(DEFAULT_RINGBACK_TONE.cadence.onMs);
    expect(gain.gain.value).toBe(0);
    expect(vi.getTimerCount()).toBe(1);

    port.stop();
    expect(gain.gain.value).toBe(0);
    expect(vi.getTimerCount()).toBe(0);

    vi.advanceTimersByTime(DEFAULT_RINGBACK_TONE.cadence.offMs * 4);
    expect(gain.gain.value).toBe(0);

    expect(() => port.stop()).not.toThrow();
    expect(vi.getTimerCount()).toBe(0);

    port.close();
  });

  it('wires the context only once across repeated starts', () => {
    const contexts = installAudioContext();
    const port = createRingbackPort();

    port.start();
    const context = onlyContext(contexts);
    expect(context.createGain).toHaveBeenCalledTimes(1);
    expect(context.createOscillator).toHaveBeenCalledTimes(
      DEFAULT_RINGBACK_TONE.frequencies.length
    );

    port.start();
    expect(contexts).toHaveLength(1);
    expect(context.createGain).toHaveBeenCalledTimes(1);
    expect(context.createOscillator).toHaveBeenCalledTimes(
      DEFAULT_RINGBACK_TONE.frequencies.length
    );

    port.close();
  });

  it('closes by stopping every oscillator and shutting the context, then ignores start', () => {
    const contexts = installAudioContext();
    const port = createRingbackPort();

    port.start();
    const context = onlyContext(contexts);

    port.close();
    context.oscillators.forEach((oscillator) => {
      expect(oscillator.stop).toHaveBeenCalledTimes(1);
    });
    expect(context.close).toHaveBeenCalledTimes(1);

    port.start();
    expect(contexts).toHaveLength(1);
    expect(context.createGain).toHaveBeenCalledTimes(1);

    port.close();
    expect(context.close).toHaveBeenCalledTimes(1);
  });

  it('honours custom frequencies, cadence and gain', () => {
    const contexts = installAudioContext();
    const port = createRingbackPort({
      frequencies: [660],
      cadence: { onMs: 100, offMs: 200 },
      gain: 0.5
    });

    port.start();
    const context = onlyContext(contexts);
    expect(context.oscillators).toHaveLength(1);
    const oscillator = context.oscillators[0];
    const gain = context.gains[0];
    if (oscillator == null || gain == null) throw new Error('audio graph was not constructed');
    expect(oscillator.frequency.value).toBe(660);
    expect(gain.gain.value).toBe(0.5);

    vi.advanceTimersByTime(100);
    expect(gain.gain.value).toBe(0);

    vi.advanceTimersByTime(200);
    expect(gain.gain.value).toBe(0.5);

    port.close();
  });
});
