import type { ConnectionState, Json } from '@sauti/protocol';
import type { SautiError } from './errors.js';

export type Quality = 'good' | 'degraded' | 'poor';

export interface TrackLike {
  readonly kind: string;
  enabled: boolean;
  stop(): void;
}

export interface StreamLike {
  getTracks(): TrackLike[];
  getAudioTracks(): TrackLike[];
}

export interface SenderLike {
  track: TrackLike | null;
  replaceTrack(track: TrackLike | null): Promise<void>;
}

export interface SessionDescriptionLike {
  type: string;
  sdp?: string;
}

export interface IceCandidateLike {
  candidate: string;
  sdpMid: string | null;
  sdpMLineIndex: number | null;
}

export interface StatsSample {
  rttMs: number;
  loss: number;
  jitterMs: number;
  jitterBufferMs?: number;
  fractionLost?: number;
}

export interface QoeSample {
  participantId: string;
  rttMs: number;
  loss: number;
  jitterMs: number;
  jitterBufferMs?: number;
  fractionLost?: number;
  sampledAt: number;
}

export interface PeerLike {
  connectionState: string;
  iceConnectionState: string;
  signalingState: string;
  localDescription: SessionDescriptionLike | null;
  onicecandidate: ((ev: { candidate: IceCandidateLike | null }) => void) | null;
  ontrack: ((ev: { streams: StreamLike[]; track: TrackLike }) => void) | null;
  onnegotiationneeded: (() => void) | null;
  oniceconnectionstatechange: (() => void) | null;
  onconnectionstatechange: (() => void) | null;
  createOffer(options?: { iceRestart?: boolean }): Promise<SessionDescriptionLike>;
  createAnswer(): Promise<SessionDescriptionLike>;
  setLocalDescription(description?: SessionDescriptionLike): Promise<void>;
  setRemoteDescription(description: SessionDescriptionLike): Promise<void>;
  addIceCandidate(candidate: IceCandidateLike): Promise<void>;
  addTrack(track: TrackLike, stream: StreamLike): SenderLike;
  getSenders(): SenderLike[];
  restartIce(): void;
  getStats(): Promise<StatsSample>;
  close(): void;
}

export interface SocketLike {
  onopen: (() => void) | null;
  onclose: ((ev: { code: number }) => void) | null;
  onerror: ((ev: unknown) => void) | null;
  onmessage: ((ev: { data: string }) => void) | null;
  send(data: string): void;
  close(code?: number): void;
}

export interface AudioSinkLike {
  srcObject: StreamLike | null;
  play(): Promise<void>;
  remove(): void;
}

export interface DeviceInfo {
  deviceId: string;
  kind: string;
  label: string;
}

export interface MediaConstraints {
  audio:
    | boolean
    | {
        deviceId?: string;
        echoCancellation?: boolean;
        noiseSuppression?: boolean;
        autoGainControl?: boolean;
      };
}

export interface IceServerConfig {
  urls: string | string[];
  username?: string;
  credential?: string;
}

export interface RtcConfig {
  iceServers: IceServerConfig[];
}

export interface SautiRuntime {
  createWebSocket(url: string): SocketLike;
  createPeerConnection(config: RtcConfig): PeerLike;
  getUserMedia(constraints: MediaConstraints): Promise<StreamLike>;
  enumerateDevices(): Promise<DeviceInfo[]>;
  createAudioSink(): AudioSinkLike;
  isSecureContext(): boolean;
  now(): number;
  addEventListener(type: string, handler: () => void): void;
  removeEventListener(type: string, handler: () => void): void;
}

export interface ParticipantView {
  participantId: string;
  metadata: Record<string, Json>;
  muted: boolean;
  onHold: boolean;
  connectionState: ConnectionState;
  quality: Quality;
}

export interface CallSnapshot {
  phase: 'idle' | 'connecting' | 'connected' | 'left';
  participants: ParticipantView[];
  localMuted: boolean;
  localOnHold: boolean;
  durationMs: number;
  quality: Quality;
  reconnecting: boolean;
  audioBlocked: boolean;
  fallback: boolean;
}

export interface JoinOptions {
  url: string;
  token: string;
  endWhenLastPeerLeaves?: boolean;
}

export interface SautiOptions {
  runtime?: Partial<SautiRuntime>;
  graceMs?: number;
  maxReconnectAttempts?: number;
  reconnectBaseMs?: number;
  reconnectMaxMs?: number;
  statsIntervalMs?: number;
  audioProcessing?: {
    echoCancellation?: boolean;
    noiseSuppression?: boolean;
    autoGainControl?: boolean;
  };
}

export interface SautiEvents {
  joined: { participantId: string };
  left: { participantId: string };
  unreachable: { participantId: string };
  reconnecting: Record<string, never>;
  reconnected: Record<string, never>;
  'state-changed': { participantId: string; muted: boolean; onHold: boolean };
  'quality-changed': { participantId: string; quality: Quality };
  'qoe-sample': QoeSample;
  'devices-changed': { devices: DeviceInfo[] };
  error: SautiError;
}
