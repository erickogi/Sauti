import { describe, it, expect, afterEach, vi } from 'vitest';
import { adaptStats, browserRuntime, resolveRuntime } from '../src/runtime.js';

const g = globalThis as unknown as Record<string, unknown>;

const saved: Record<string, unknown> = {};
function stash(...names: string[]): void {
  for (const name of names) saved[name] = g[name];
}
function restore(name: string, value: unknown): void {
  Object.defineProperty(globalThis, name, { value, configurable: true, writable: true });
}
afterEach(() => {
  for (const [name, value] of Object.entries(saved)) restore(name, value);
});

describe('adaptStats', () => {
  it('reduces a stats report to rtt, loss, and jitter', () => {
    const report = {
      forEach(cb: (s: Record<string, unknown>) => void) {
        cb({ type: 'inbound-rtp', jitter: 0.05, packetsLost: 5, packetsReceived: 95 });
        cb({ type: 'remote-inbound-rtp', currentRoundTripTime: 0.2 });
      }
    };
    const sample = adaptStats(report);
    expect(sample.jitterMs).toBeCloseTo(50);
    expect(sample.loss).toBeCloseTo(0.05);
    expect(sample.rttMs).toBeCloseTo(200);
  });

  it('falls back to roundTripTime and tolerates missing counters', () => {
    const report = {
      forEach(cb: (s: Record<string, unknown>) => void) {
        cb({ type: 'candidate-pair', roundTripTime: 0.1 });
        cb({ type: 'inbound-rtp' });
        cb({ type: 'other' });
      }
    };
    const sample = adaptStats(report);
    expect(sample.rttMs).toBeCloseTo(100);
    expect(sample.loss).toBe(0);
    expect(sample.jitterMs).toBe(0);
  });
});

describe('browserRuntime', () => {
  it('constructs a WebSocket from the global constructor', () => {
    stash('WebSocket');
    class FakeWS {
      constructor(public url: string) {}
    }
    g.WebSocket = FakeWS;
    const socket = browserRuntime().createWebSocket('wss://x');
    expect(socket).toBeInstanceOf(FakeWS);
  });

  it('throws when a required global is missing', () => {
    stash('WebSocket');
    delete g.WebSocket;
    expect(() => browserRuntime().createWebSocket('wss://x')).toThrow();
  });

  it('wraps a peer connection and adapts getStats', async () => {
    stash('RTCPeerConnection');
    class FakePC {
      config: unknown;
      constructor(config: unknown) {
        this.config = config;
      }
      getStats() {
        return Promise.resolve({
          forEach(cb: (s: Record<string, unknown>) => void) {
            cb({ type: 'inbound-rtp', jitter: 0.02, packetsLost: 0, packetsReceived: 100 });
          }
        });
      }
    }
    g.RTCPeerConnection = FakePC;
    const pc = browserRuntime().createPeerConnection({ iceServers: [] });
    const sample = await pc.getStats();
    expect(sample.jitterMs).toBeCloseTo(20);
  });

  it('reads media devices and secure context and now from globals', async () => {
    stash('navigator', 'isSecureContext');
    const getUserMedia = vi.fn(async () => ({ getTracks: () => [], getAudioTracks: () => [] }));
    const enumerateDevices = vi.fn(async () => [
      { deviceId: 'x', kind: 'audioinput', label: 'X' }
    ]);
    Object.defineProperty(globalThis, 'navigator', {
      value: { mediaDevices: { getUserMedia, enumerateDevices } },
      configurable: true
    });
    g.isSecureContext = true;
    const rt = browserRuntime();
    await rt.getUserMedia({ audio: true });
    expect(getUserMedia).toHaveBeenCalled();
    expect(await rt.enumerateDevices()).toHaveLength(1);
    expect(rt.isSecureContext()).toBe(true);
    expect(typeof rt.now()).toBe('number');
  });

  it('creates a hidden audio sink and removes it', async () => {
    const rt = browserRuntime();
    const sink = rt.createAudioSink();
    sink.srcObject = { getTracks: () => [], getAudioTracks: () => [] };
    expect(sink.srcObject).not.toBeNull();
    await expect(sink.play()).resolves.toBeUndefined();
    expect(() => sink.remove()).not.toThrow();
  });

  it('routes event registration to the right target', () => {
    stash('navigator');
    const add = vi.fn();
    const remove = vi.fn();
    Object.defineProperty(globalThis, 'navigator', {
      value: { mediaDevices: { addEventListener: add, removeEventListener: remove } },
      configurable: true
    });
    const rt = browserRuntime();
    const handler = () => undefined;
    const onlineSpy = vi.spyOn(globalThis, 'addEventListener');
    const offlineSpy = vi.spyOn(globalThis, 'removeEventListener');
    rt.addEventListener('online', handler);
    rt.removeEventListener('online', handler);
    rt.addEventListener('devicechange', handler);
    rt.removeEventListener('devicechange', handler);
    expect(onlineSpy).toHaveBeenCalledWith('online', handler);
    expect(offlineSpy).toHaveBeenCalledWith('online', handler);
    expect(add).toHaveBeenCalledWith('devicechange', handler);
    expect(remove).toHaveBeenCalledWith('devicechange', handler);
  });

  it('resolves audio play when the element returns no promise', async () => {
    stash('document');
    const el = {
      autoplay: false,
      hidden: false,
      srcObject: null as unknown,
      play: () => undefined,
      remove: () => undefined
    };
    Object.defineProperty(globalThis, 'document', {
      value: { createElement: () => el, body: { appendChild: () => undefined } },
      configurable: true
    });
    const sink = browserRuntime().createAudioSink();
    await expect(sink.play()).resolves.toBeUndefined();
  });

  it('resolveRuntime returns the browser runtime and merges overrides', () => {
    const plain = resolveRuntime();
    expect(typeof plain.now).toBe('function');
    const merged = resolveRuntime({ now: () => 42 });
    expect(merged.now()).toBe(42);
  });
});
