import { PROTOCOL_VERSION, type ReadyFrame, type ServerFrame } from '@sauti/protocol';
import { AudioSinkManager } from './audio.js';
import { Emitter } from './emitter.js';
import {
  DisconnectedError,
  InsecureTransportError,
  RoomFullError,
  ServerFrameError,
  VersionMismatchError,
  isFatalServerCode,
  mapMicError,
  type SautiError
} from './errors.js';
import { Mesh } from './mesh.js';
import { QualityTracker } from './quality.js';
import { SignalingClient } from './signaling.js';
import { CallStore } from './store.js';
import type {
  CallSnapshot,
  DeviceInfo,
  JoinOptions,
  SautiEvents,
  SautiOptions,
  SautiRuntime,
  StreamLike,
  TrackLike
} from './types.js';

const NETWORK_EVENTS = ['online', 'visibilitychange', 'pageshow'];

export interface SautiCall {
  join(options: JoinOptions): Promise<void>;
  leave(): void;
  setMuted(muted: boolean): void;
  setHold(onHold: boolean): void;
  acquireMic(deviceId?: string): Promise<void>;
  unlockAudio(): Promise<void>;
  enumerateDevices(): Promise<DeviceInfo[]>;
  selectInputDevice(deviceId: string): Promise<void>;
  getSnapshot(): CallSnapshot;
  subscribe(listener: () => void): () => void;
  listenerCount(): number;
  on<K extends keyof SautiEvents>(
    type: K,
    handler: (payload: SautiEvents[K]) => void
  ): () => void;
  off<K extends keyof SautiEvents>(
    type: K,
    handler: (payload: SautiEvents[K]) => void
  ): void;
}

export class Call implements SautiCall {
  private readonly runtime: SautiRuntime;
  private readonly store: CallStore;
  private readonly emitter = new Emitter<SautiEvents>();
  private readonly sinks: AudioSinkManager;
  private readonly trackers = new Map<string, QualityTracker>();

  private readonly graceMs: number;
  private readonly maxReconnectAttempts: number;
  private readonly reconnectBaseMs: number;
  private readonly reconnectMaxMs: number;
  private readonly statsIntervalMs: number;

  private signaling: SignalingClient | null = null;
  private mesh: Mesh | null = null;
  private localStream: StreamLike | null = null;
  private localTrack: TrackLike | null = null;

  private active = false;
  private clockCaptured = false;
  private localMuted = false;
  private localOnHold = false;

  private durationTimer: ReturnType<typeof setInterval> | null = null;
  private qualityTimer: ReturnType<typeof setInterval> | null = null;

  private joinResolve: (() => void) | null = null;
  private joinReject: ((error: SautiError) => void) | null = null;

  private readonly onNetworkChange = () => this.mesh?.iceRestartAll();
  private readonly onDeviceChange = () => {
    void this.refreshDevices();
  };

  constructor(runtime: SautiRuntime, options: SautiOptions = {}) {
    this.runtime = runtime;
    this.graceMs = options.graceMs ?? 30000;
    this.maxReconnectAttempts = options.maxReconnectAttempts ?? 8;
    this.reconnectBaseMs = options.reconnectBaseMs ?? 500;
    this.reconnectMaxMs = options.reconnectMaxMs ?? this.graceMs;
    this.statsIntervalMs = options.statsIntervalMs ?? 2000;
    this.store = new CallStore(() => this.runtime.now());
    this.sinks = new AudioSinkManager(
      this.runtime,
      () => this.store.setAudioBlocked(true),
      () => this.store.setAudioBlocked(false)
    );
  }

  getSnapshot(): CallSnapshot {
    return this.store.getSnapshot();
  }

  subscribe(listener: () => void): () => void {
    return this.store.subscribe(listener);
  }

  listenerCount(): number {
    return this.store.listenerCount();
  }

  on<K extends keyof SautiEvents>(
    type: K,
    handler: (payload: SautiEvents[K]) => void
  ): () => void {
    return this.emitter.on(type, handler);
  }

  off<K extends keyof SautiEvents>(
    type: K,
    handler: (payload: SautiEvents[K]) => void
  ): void {
    this.emitter.off(type, handler);
  }

  async acquireMic(deviceId?: string): Promise<void> {
    if (this.localTrack) return;
    let stream: StreamLike;
    try {
      stream = await this.runtime.getUserMedia({
        audio: deviceId ? { deviceId } : true
      });
    } catch (reason) {
      throw mapMicError(reason);
    }
    const track = stream.getAudioTracks()[0];
    if (!track) throw mapMicError({ name: 'NotFoundError' });
    this.localStream = stream;
    this.localTrack = track;
    this.applyLocalAudio();
  }

  private applyLocalAudio(): void {
    if (this.localTrack) {
      this.localTrack.enabled = !(this.localMuted || this.localOnHold);
    }
  }

  async enumerateDevices(): Promise<DeviceInfo[]> {
    if (!this.localTrack) return [];
    return this.runtime.enumerateDevices();
  }

  private async refreshDevices(): Promise<void> {
    if (!this.localTrack) return;
    const devices = await this.runtime.enumerateDevices();
    this.emitter.emit('devices-changed', { devices });
  }

  async selectInputDevice(deviceId: string): Promise<void> {
    const stream = await this.runtime.getUserMedia({ audio: { deviceId } });
    const track = stream.getAudioTracks()[0];
    if (!track) throw mapMicError({ name: 'NotFoundError' });
    track.enabled = !(this.localMuted || this.localOnHold);
    this.localTrack?.stop();
    this.localStream = stream;
    this.localTrack = track;
    await this.mesh?.replaceTrackAll(track);
  }

  async join(options: JoinOptions): Promise<void> {
    if (this.active) return;
    if (!this.runtime.isSecureContext()) {
      throw new InsecureTransportError('join requires a secure browsing context');
    }
    if (!options.url.startsWith('wss://')) {
      throw new InsecureTransportError('signaling requires a wss:// endpoint');
    }
    await this.acquireMic();
    this.active = true;
    this.clockCaptured = false;
    this.store.setPhase('connecting');
    this.registerListeners();

    const promise = new Promise<void>((resolve, reject) => {
      this.joinResolve = resolve;
      this.joinReject = reject;
    });

    this.signaling = new SignalingClient(
      this.runtime,
      {
        maxReconnectAttempts: this.maxReconnectAttempts,
        reconnectBaseMs: this.reconnectBaseMs,
        reconnectMaxMs: this.reconnectMaxMs,
        random: Math.random
      },
      {
        onFrame: (frame) => this.onFrame(frame),
        onVersionMismatch: (raw) => this.surface(new VersionMismatchError(raw)),
        onReconnecting: () => {
          this.store.setSocketReconnecting(true);
          this.emitter.emit('reconnecting', {});
        },
        onTerminal: (error) => this.onTerminal(error)
      }
    );
    this.signaling.connect(options.url, options.token);
    return promise;
  }

  private registerListeners(): void {
    for (const type of NETWORK_EVENTS) {
      this.runtime.addEventListener(type, this.onNetworkChange);
    }
    this.runtime.addEventListener('devicechange', this.onDeviceChange);
  }

  private unregisterListeners(): void {
    for (const type of NETWORK_EVENTS) {
      this.runtime.removeEventListener(type, this.onNetworkChange);
    }
    this.runtime.removeEventListener('devicechange', this.onDeviceChange);
  }

  private onFrame(frame: ServerFrame): void {
    switch (frame.type) {
      case 'ready':
        this.onReady(frame);
        break;
      case 'participant-joined':
        this.onParticipantJoined(frame.participant);
        break;
      case 'participant-state':
        this.store.mergeState(frame.participantId, frame.state);
        this.emitStateChanged(frame.participantId);
        break;
      case 'participant-unreachable':
        this.store.setConnectionState(frame.participantId, 'reconnecting');
        this.emitter.emit('unreachable', { participantId: frame.participantId });
        break;
      case 'participant-left':
        this.mesh?.removePeer(frame.participantId);
        this.store.removeParticipant(frame.participantId);
        this.trackers.delete(frame.participantId);
        this.emitter.emit('left', { participantId: frame.participantId });
        break;
      case 'room-started':
        this.store.setStartedAt(frame.startedAt);
        this.startDuration();
        break;
      case 'error':
        this.onServerError(frame.code, frame.message);
        break;
      case 'offer':
        void this.mesh?.routeOffer(frame.from, frame.sdp).catch((e) => this.surface2(e));
        break;
      case 'answer':
        void this.mesh?.routeAnswer(frame.from, frame.sdp).catch((e) => this.surface2(e));
        break;
      case 'ice':
        void this.mesh?.routeIce(frame.from, frame.candidate).catch((e) => this.surface2(e));
        break;
    }
  }

  private onReady(frame: ReadyFrame): void {
    if (!this.clockCaptured) {
      this.store.setClockOffset(frame.serverNow - this.runtime.now());
      this.clockCaptured = true;
    }

    if (!frame.resumed && frame.peers.length >= frame.room.maxParticipants) {
      const error = new RoomFullError('the room is already at capacity');
      this.surface(error);
      this.failJoin(error);
      return;
    }

    const selfId = frame.self.participantId;
    this.store.setSelf(selfId);

    if (!this.mesh) {
      this.mesh = new Mesh(this.runtime, this.sinks, selfId, {
        send: (f) => this.signaling?.send(f),
        onConnected: (peerId) => this.store.setConnectionState(peerId, 'connected'),
        onError: (e) => this.surface2(e)
      });
    }
    this.mesh.setIceServers(frame.iceServers);
    if (this.localStream && this.localTrack) {
      this.mesh.setLocalMedia(this.localStream, this.localTrack);
    }

    this.store.upsertParticipant({
      participantId: selfId,
      metadata: frame.self.metadata,
      muted: this.localMuted,
      onHold: this.localOnHold,
      connectionState: 'connected'
    });

    if (frame.resumed) {
      this.reconcile(frame);
    } else {
      for (const peer of frame.peers) {
        this.store.upsertParticipant({
          participantId: peer.participantId,
          metadata: peer.metadata,
          muted: peer.state.muted,
          onHold: peer.state.onHold,
          connectionState: 'connecting'
        });
        this.mesh.addPeer(peer.participantId);
      }
    }

    this.store.setStartedAt(frame.room.startedAt);
    if (frame.room.startedAt !== null) this.startDuration();
    this.startQuality();

    this.store.setSocketReconnecting(false);
    this.store.setPhase('connected');
    if (frame.resumed) this.emitter.emit('reconnected', {});
    this.finishJoin();
  }

  private reconcile(frame: ReadyFrame): void {
    const present = new Set(frame.peers.map((p) => p.participantId));
    for (const id of this.mesh!.ids()) {
      if (!present.has(id)) {
        this.mesh!.removePeer(id);
        this.store.removeParticipant(id);
        this.trackers.delete(id);
      }
    }
    for (const peer of frame.peers) {
      this.store.upsertParticipant({
        participantId: peer.participantId,
        metadata: peer.metadata,
        muted: peer.state.muted,
        onHold: peer.state.onHold,
        connectionState: this.mesh!.has(peer.participantId) ? 'connected' : 'connecting'
      });
      if (!this.mesh!.has(peer.participantId)) this.mesh!.addPeer(peer.participantId);
    }
  }

  private onParticipantJoined(participant: ReadyFrame['peers'][number]): void {
    this.store.upsertParticipant({
      participantId: participant.participantId,
      metadata: participant.metadata,
      muted: participant.state.muted,
      onHold: participant.state.onHold,
      connectionState: 'connecting'
    });
    this.mesh?.addPeer(participant.participantId);
    this.emitter.emit('joined', { participantId: participant.participantId });
  }

  private onServerError(code: string, message: string): void {
    this.surface(new ServerFrameError(code, message));
    if (isFatalServerCode(code)) {
      this.failJoin(new ServerFrameError(code, message));
    }
  }

  private onTerminal(error: DisconnectedError): void {
    this.surface(error);
    this.failJoin(error);
  }

  private failJoin(error: SautiError): void {
    this.signaling?.close();
    this.teardown();
    this.finishJoin(error);
  }

  private startDuration(): void {
    if (this.durationTimer) return;
    this.durationTimer = setInterval(() => this.store.tick(), 1000);
  }

  private startQuality(): void {
    if (this.qualityTimer) return;
    this.qualityTimer = setInterval(() => {
      void this.pollQuality();
    }, this.statsIntervalMs);
  }

  private async pollQuality(): Promise<void> {
    if (!this.mesh) return;
    let anyFallback = false;
    for (const peerId of this.mesh.ids()) {
      const peer = this.mesh.ensurePeer(peerId);
      let sample;
      try {
        sample = await peer.pc.getStats();
      } catch {
        continue;
      }
      let tracker = this.trackers.get(peerId);
      if (!tracker) {
        tracker = new QualityTracker();
        this.trackers.set(peerId, tracker);
      }
      const result = tracker.observe(sample);
      const before = this.store.getParticipant(peerId)?.quality;
      this.store.setQuality(peerId, result.label);
      if (before !== undefined && before !== result.label) {
        this.emitter.emit('quality-changed', {
          participantId: peerId,
          quality: result.label
        });
      }
      if (result.fallback) anyFallback = true;
    }
    this.store.setFallback(anyFallback);
  }

  private emitStateChanged(id: string): void {
    const view = this.store.getParticipant(id);
    if (!view) return;
    this.emitter.emit('state-changed', {
      participantId: id,
      muted: view.muted,
      onHold: view.onHold
    });
  }

  private surface(error: SautiError): void {
    this.emitter.emit('error', error);
  }

  private surface2(error: unknown): void {
    if (error instanceof Error) {
      this.emitter.emit(
        'error',
        error as SautiError
      );
    }
  }

  setMuted(muted: boolean): void {
    this.localMuted = muted;
    this.applyLocalAudio();
    this.store.setLocalMuted(muted);
    this.signaling?.send({
      v: PROTOCOL_VERSION,
      type: 'state',
      state: { muted }
    });
  }

  setHold(onHold: boolean): void {
    this.localOnHold = onHold;
    this.applyLocalAudio();
    this.store.setLocalOnHold(onHold);
    this.signaling?.send({
      v: PROTOCOL_VERSION,
      type: 'state',
      state: { onHold }
    });
  }

  async unlockAudio(): Promise<void> {
    await this.sinks.unlock();
  }

  leave(): void {
    if (!this.active) return;
    this.signaling?.send({ v: PROTOCOL_VERSION, type: 'leave' });
    this.signaling?.close();
    this.teardown();
  }

  private teardown(): void {
    this.mesh?.teardown();
    this.mesh = null;
    for (const track of this.localStream?.getTracks() ?? []) track.stop();
    this.localStream = null;
    this.localTrack = null;
    if (this.durationTimer) {
      clearInterval(this.durationTimer);
      this.durationTimer = null;
    }
    if (this.qualityTimer) {
      clearInterval(this.qualityTimer);
      this.qualityTimer = null;
    }
    this.trackers.clear();
    this.unregisterListeners();
    this.signaling = null;
    this.active = false;
    this.localMuted = false;
    this.localOnHold = false;
    this.store.reset();
  }

  private finishJoin(error?: SautiError): void {
    if (error) {
      this.joinReject?.(error);
    } else {
      this.joinResolve?.();
    }
    this.joinResolve = null;
    this.joinReject = null;
  }
}
