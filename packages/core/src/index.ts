import { Call, type SautiCall } from './call.js';
import { resolveRuntime } from './runtime.js';
import type { SautiOptions } from './types.js';

export function createSautiCall(options: SautiOptions = {}): SautiCall {
  return new Call(resolveRuntime(options.runtime), options);
}

export { Call } from './call.js';
export type { SautiCall } from './call.js';
export { adaptStats, browserRuntime, resolveRuntime } from './runtime.js';
export { classify, QualityTracker } from './quality.js';
export { computeBackoff } from './backoff.js';
export { AudioSinkManager } from './audio.js';
export { CallStore } from './store.js';
export { Emitter } from './emitter.js';
export { Mesh } from './mesh.js';
export { Peer } from './peer.js';
export { SignalingClient } from './signaling.js';
export {
  SautiError,
  InsecureTransportError,
  RoomFullError,
  VersionMismatchError,
  DisconnectedError,
  ServerFrameError,
  MicError,
  isFatalServerCode,
  mapMicError,
  type SautiErrorCode
} from './errors.js';
export type {
  CallSnapshot,
  ParticipantView,
  Quality,
  DeviceInfo,
  JoinOptions,
  SautiOptions,
  SautiRuntime,
  SautiEvents,
  SocketLike,
  PeerLike,
  SenderLike,
  TrackLike,
  StreamLike,
  AudioSinkLike,
  StatsSample,
  IceCandidateLike,
  MediaConstraints,
  RtcConfig,
  IceServerConfig,
  QoeSample
} from './types.js';
