import type {
  AudioSinkLike,
  DeviceInfo,
  IceCandidateLike,
  MediaConstraints,
  PeerLike,
  RtcConfig,
  SautiRuntime,
  SenderLike,
  SessionDescriptionLike,
  SocketLike,
  StatsSample,
  StreamLike,
  TrackLike
} from '../src/types.js';

let trackCounter = 0;

export class FakeTrack implements TrackLike {
  readonly kind = 'audio';
  enabled = true;
  stopped = false;
  readonly id: string;

  constructor(label = 'mic') {
    this.id = `${label}-${(trackCounter += 1)}`;
  }

  stop(): void {
    this.stopped = true;
  }
}

export class FakeStream implements StreamLike {
  constructor(public tracks: TrackLike[] = [new FakeTrack()]) {}

  getTracks(): TrackLike[] {
    return this.tracks;
  }

  getAudioTracks(): TrackLike[] {
    return this.tracks.filter((t) => t.kind === 'audio');
  }
}

export class FakeSender implements SenderLike {
  replaced: (TrackLike | null)[] = [];

  constructor(public track: TrackLike | null) {}

  async replaceTrack(track: TrackLike | null): Promise<void> {
    this.track = track;
    this.replaced.push(track);
  }
}

export class FakeWebSocket implements SocketLike {
  static instances: FakeWebSocket[] = [];

  onopen: (() => void) | null = null;
  onclose: ((ev: { code: number }) => void) | null = null;
  onerror: ((ev: unknown) => void) | null = null;
  onmessage: ((ev: { data: string }) => void) | null = null;

  sent: unknown[] = [];
  closed = false;
  opened = false;

  constructor(public url: string) {
    FakeWebSocket.instances.push(this);
  }

  send(data: string): void {
    this.sent.push(JSON.parse(data));
  }

  close(): void {
    this.closed = true;
  }

  open(): void {
    this.opened = true;
    this.onopen?.();
  }

  deliver(frame: unknown): void {
    this.onmessage?.({ data: JSON.stringify(frame) });
  }

  deliverRaw(data: string): void {
    this.onmessage?.({ data });
  }

  drop(code = 1006): void {
    this.onclose?.({ code });
  }

  frames(): Record<string, unknown>[] {
    return this.sent as Record<string, unknown>[];
  }
}

export class FakePeerConnection implements PeerLike {
  static instances: FakePeerConnection[] = [];

  connectionState = 'new';
  iceConnectionState = 'new';
  signalingState = 'stable';
  localDescription: SessionDescriptionLike | null = null;

  onicecandidate: ((ev: { candidate: IceCandidateLike | null }) => void) | null = null;
  ontrack: ((ev: { streams: StreamLike[]; track: TrackLike }) => void) | null = null;
  onnegotiationneeded: (() => void) | null = null;
  oniceconnectionstatechange: (() => void) | null = null;
  onconnectionstatechange: (() => void) | null = null;

  senders: FakeSender[] = [];
  remoteDescriptions: SessionDescriptionLike[] = [];
  localDescriptions: (SessionDescriptionLike | undefined)[] = [];
  addedCandidates: IceCandidateLike[] = [];
  offersCreated: ({ iceRestart?: boolean } | undefined)[] = [];
  restartCount = 0;
  closed = false;
  rejectNextIce = false;
  nextStats: StatsSample = { rttMs: 10, loss: 0, jitterMs: 5 };

  constructor(public config: RtcConfig) {
    FakePeerConnection.instances.push(this);
  }

  async createOffer(options?: { iceRestart?: boolean }): Promise<SessionDescriptionLike> {
    this.offersCreated.push(options);
    return { type: 'offer', sdp: 'offer-sdp' };
  }

  async createAnswer(): Promise<SessionDescriptionLike> {
    return { type: 'answer', sdp: 'answer-sdp' };
  }

  async setLocalDescription(description?: SessionDescriptionLike): Promise<void> {
    this.localDescriptions.push(description);
    if (description?.type === 'rollback') this.signalingState = 'stable';
    else this.localDescription = description ?? { type: 'offer', sdp: 'offer-sdp' };
  }

  async setRemoteDescription(description: SessionDescriptionLike): Promise<void> {
    this.remoteDescriptions.push(description);
  }

  async addIceCandidate(candidate: IceCandidateLike): Promise<void> {
    if (this.rejectNextIce) {
      this.rejectNextIce = false;
      throw new Error('bad candidate');
    }
    this.addedCandidates.push(candidate);
  }

  addTrack(track: TrackLike, _stream: StreamLike): SenderLike {
    const sender = new FakeSender(track);
    this.senders.push(sender);
    return sender;
  }

  getSenders(): SenderLike[] {
    return this.senders;
  }

  restartIce(): void {
    this.restartCount += 1;
  }

  async getStats(): Promise<StatsSample> {
    return this.nextStats;
  }

  close(): void {
    this.closed = true;
  }

  emitIce(candidate: IceCandidateLike | null): void {
    this.onicecandidate?.({ candidate });
  }

  emitTrack(stream: StreamLike): void {
    this.ontrack?.({ streams: [stream], track: stream.getAudioTracks()[0]! });
  }

  emitNegotiationNeeded(): void {
    this.onnegotiationneeded?.();
  }

  setIceState(state: string): void {
    this.iceConnectionState = state;
    this.oniceconnectionstatechange?.();
  }
}

export class FakeAudioSink implements AudioSinkLike {
  static instances: FakeAudioSink[] = [];

  srcObject: StreamLike | null = null;
  removed = false;
  playCount = 0;
  rejectPlay = false;

  constructor() {
    FakeAudioSink.instances.push(this);
  }

  async play(): Promise<void> {
    this.playCount += 1;
    if (this.rejectPlay) throw new Error('NotAllowedError');
  }

  remove(): void {
    this.removed = true;
  }
}

export interface RuntimeHarness {
  runtime: SautiRuntime;
  sockets: FakeWebSocket[];
  pcs: FakePeerConnection[];
  sinks: FakeAudioSink[];
  listeners: Map<string, Set<() => void>>;
  getUserMediaCalls: MediaConstraints[];
  setGetUserMedia(fn: (c: MediaConstraints) => Promise<StreamLike>): void;
  setDevices(devices: DeviceInfo[]): void;
  fire(type: string): void;
  now: { value: number };
  lastSocket(): FakeWebSocket;
  lastPc(): FakePeerConnection;
}

export function makeRuntime(overrides: Partial<SautiRuntime> = {}): RuntimeHarness {
  FakeWebSocket.instances = [];
  FakePeerConnection.instances = [];
  FakeAudioSink.instances = [];

  const listeners = new Map<string, Set<() => void>>();
  const getUserMediaCalls: MediaConstraints[] = [];
  const now = { value: 1_000_000 };
  let devices: DeviceInfo[] = [
    { deviceId: 'mic-1', kind: 'audioinput', label: 'Mic One' },
    { deviceId: 'mic-2', kind: 'audioinput', label: 'Mic Two' }
  ];
  let getUserMedia = async (_c: MediaConstraints): Promise<StreamLike> =>
    new FakeStream([new FakeTrack()]);

  const runtime: SautiRuntime = {
    createWebSocket: (url) => new FakeWebSocket(url),
    createPeerConnection: (config) => new FakePeerConnection(config),
    getUserMedia: (c) => {
      getUserMediaCalls.push(c);
      return getUserMedia(c);
    },
    enumerateDevices: async () => devices,
    createAudioSink: () => new FakeAudioSink(),
    isSecureContext: () => true,
    now: () => now.value,
    addEventListener: (type, handler) => {
      let set = listeners.get(type);
      if (!set) {
        set = new Set();
        listeners.set(type, set);
      }
      set.add(handler);
    },
    removeEventListener: (type, handler) => {
      listeners.get(type)?.delete(handler);
    },
    ...overrides
  };

  return {
    runtime,
    sockets: FakeWebSocket.instances,
    pcs: FakePeerConnection.instances,
    sinks: FakeAudioSink.instances,
    listeners,
    getUserMediaCalls,
    now,
    setGetUserMedia: (fn) => {
      getUserMedia = fn;
    },
    setDevices: (d) => {
      devices = d;
    },
    fire: (type) => {
      for (const handler of [...(listeners.get(type) ?? [])]) handler();
    },
    lastSocket: () => FakeWebSocket.instances[FakeWebSocket.instances.length - 1]!,
    lastPc: () => FakePeerConnection.instances[FakePeerConnection.instances.length - 1]!
  };
}

let pid = 0;

export function participant(
  participantId: string,
  overrides: Partial<{ muted: boolean; onHold: boolean }> = {}
) {
  return {
    participantId,
    joinedAt: (pid += 1),
    metadata: {},
    connectionState: 'connected' as const,
    state: { muted: overrides.muted ?? false, onHold: overrides.onHold ?? false }
  };
}

export function readyFrame(options: {
  selfId: string;
  peers?: ReturnType<typeof participant>[];
  startedAt?: number | null;
  serverNow?: number;
  resumed?: boolean;
  maxParticipants?: number;
  roomId?: string;
}) {
  return {
    v: 1 as const,
    type: 'ready' as const,
    self: participant(options.selfId),
    peers: options.peers ?? [],
    room: {
      roomId: options.roomId ?? 'room-1',
      startedAt: options.startedAt ?? null,
      maxParticipants: options.maxParticipants ?? 3
    },
    iceServers: [{ urls: 'turn:example' }],
    serverNow: options.serverNow ?? 1_000_000,
    resumed: options.resumed ?? false
  };
}

export async function flush(): Promise<void> {
  await Promise.resolve();
  await Promise.resolve();
  await Promise.resolve();
}
