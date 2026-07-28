import type {
  AudioSinkLike,
  DeviceInfo,
  MediaConstraints,
  PeerLike,
  RtcConfig,
  SautiRuntime,
  SocketLike,
  StatsSample,
  StreamLike
} from './types.js';

type RawStats = Record<string, unknown> & { type: string };

interface StatsReportLike {
  forEach(callback: (stats: RawStats) => void): void;
}

function numeric(value: unknown): number | undefined {
  return typeof value === 'number' ? value : undefined;
}

function readRttSeconds(stats: RawStats): number | undefined {
  let best: number | undefined;
  for (const [key, value] of Object.entries(stats)) {
    const lower = key.toLowerCase();
    const seconds = numeric(value);
    if (seconds === undefined) continue;
    if (lower.includes('round') && lower.endsWith('time')) {
      best = seconds;
      if (lower.startsWith('current')) return seconds;
    }
  }
  return best;
}

export function adaptStats(report: StatsReportLike): StatsSample {
  const sample: StatsSample = { rttMs: 0, loss: 0, jitterMs: 0 };
  report.forEach((stats) => {
    if (stats.type === 'inbound-rtp') {
      const jitter = numeric(stats.jitter);
      if (jitter !== undefined) sample.jitterMs = jitter * 1000;
      const lost = numeric(stats.packetsLost) ?? 0;
      const received = numeric(stats.packetsReceived) ?? 0;
      const total = lost + received;
      if (total > 0) sample.loss = lost / total;
      const bufferDelay = numeric(stats.jitterBufferDelay);
      const bufferCount = numeric(stats.jitterBufferEmittedCount);
      if (bufferDelay !== undefined && bufferCount !== undefined && bufferCount > 0) {
        sample.jitterBufferMs = (bufferDelay / bufferCount) * 1000;
      }
    } else if (stats.type === 'remote-inbound-rtp' || stats.type === 'candidate-pair') {
      const rtt = readRttSeconds(stats);
      if (rtt !== undefined) sample.rttMs = rtt * 1000;
      if (stats.type === 'remote-inbound-rtp') {
        const fractionLost = numeric(stats.fractionLost);
        if (fractionLost !== undefined) sample.fractionLost = fractionLost;
      }
    }
  });
  return sample;
}

function globalRef<T>(name: string): T {
  const value = (globalThis as unknown as Record<string, unknown>)[name];
  if (!value) throw new Error(`${name} is not available in this environment`);
  return value as T;
}

function mediaDevices(): {
  getUserMedia(c: unknown): Promise<StreamLike>;
  enumerateDevices(): Promise<DeviceInfo[]>;
  addEventListener(type: string, handler: () => void): void;
  removeEventListener(type: string, handler: () => void): void;
} {
  const navigatorRef = globalRef<{ mediaDevices: never }>('navigator');
  return navigatorRef.mediaDevices;
}

export function browserRuntime(): SautiRuntime {
  return {
    createWebSocket(url: string): SocketLike {
      const Ctor = globalRef<new (url: string) => SocketLike>('WebSocket');
      return new Ctor(url);
    },
    createPeerConnection(config: RtcConfig): PeerLike {
      const Ctor = globalRef<new (config: RtcConfig) => PeerLike>('RTCPeerConnection');
      const pc = new Ctor(config);
      const original = pc.getStats.bind(pc);
      pc.getStats = async () =>
        adaptStats((await original()) as unknown as StatsReportLike);
      return pc;
    },
    getUserMedia(constraints: MediaConstraints): Promise<StreamLike> {
      return mediaDevices().getUserMedia(constraints);
    },
    enumerateDevices(): Promise<DeviceInfo[]> {
      return mediaDevices().enumerateDevices();
    },
    createAudioSink(): AudioSinkLike {
      const doc = globalRef<{
        createElement(tag: string): {
          autoplay: boolean;
          hidden: boolean;
          srcObject: unknown;
          play(): Promise<void> | undefined;
          remove(): void;
        };
        body: { appendChild(node: unknown): void };
      }>('document');
      const el = doc.createElement('audio');
      el.autoplay = true;
      el.hidden = true;
      doc.body.appendChild(el);
      return {
        get srcObject(): StreamLike | null {
          return el.srcObject as StreamLike | null;
        },
        set srcObject(value: StreamLike | null) {
          el.srcObject = value;
        },
        play(): Promise<void> {
          try {
            return el.play() ?? Promise.resolve();
          } catch {
            return Promise.resolve();
          }
        },
        remove(): void {
          el.remove();
        }
      };
    },
    isSecureContext(): boolean {
      return (globalThis as unknown as { isSecureContext?: boolean }).isSecureContext === true;
    },
    now(): number {
      return Date.now();
    },
    addEventListener(type: string, handler: () => void): void {
      if (type === 'devicechange') mediaDevices().addEventListener(type, handler);
      else globalThis.addEventListener(type, handler);
    },
    removeEventListener(type: string, handler: () => void): void {
      if (type === 'devicechange') mediaDevices().removeEventListener(type, handler);
      else globalThis.removeEventListener(type, handler);
    }
  };
}

export function resolveRuntime(overrides?: Partial<SautiRuntime>): SautiRuntime {
  if (!overrides) return browserRuntime();
  return { ...browserRuntime(), ...overrides };
}
