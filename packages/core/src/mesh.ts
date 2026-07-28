import type { AudioSinkManager } from './audio.js';
import { Peer, type PeerSignal } from './peer.js';
import type { OpusOptions } from './sdp.js';
import type {
  IceCandidateLike,
  IceServerConfig,
  SautiRuntime,
  StreamLike,
  TrackLike
} from './types.js';

export interface MeshHooks {
  send(frame: unknown): void;
  onConnected(peerId: string): void;
  onError(error: unknown): void;
}

export class Mesh {
  private readonly peers = new Map<string, Peer>();
  private iceServers: IceServerConfig[] = [];
  private local: { stream: StreamLike; track: TrackLike } | null = null;

  private readonly signal: PeerSignal;

  constructor(
    private readonly runtime: SautiRuntime,
    private readonly sinks: AudioSinkManager,
    private readonly selfId: string,
    private readonly hooks: MeshHooks,
    private readonly opus: OpusOptions = {}
  ) {
    this.signal = {
      send: (frame) => this.hooks.send(frame),
      onRemoteStream: (peerId, stream) => this.sinks.attach(peerId, stream),
      onUnrecoverable: (peerId) => this.rebuild(peerId),
      onConnected: (peerId) => this.hooks.onConnected(peerId),
      onError: (error) => this.hooks.onError(error)
    };
  }

  setIceServers(servers: IceServerConfig[]): void {
    this.iceServers = servers;
  }

  setLocalMedia(stream: StreamLike, track: TrackLike): void {
    this.local = { stream, track };
  }

  has(peerId: string): boolean {
    return this.peers.has(peerId);
  }

  ids(): string[] {
    return [...this.peers.keys()];
  }

  ensurePeer(peerId: string): Peer {
    const existing = this.peers.get(peerId);
    if (existing) return existing;
    return this.createPeer(peerId);
  }

  private createPeer(peerId: string): Peer {
    if (!this.local) {
      throw new Error('local media not initialized');
    }
    const pc = this.runtime.createPeerConnection({ iceServers: this.iceServers });
    const peer = new Peer(
      peerId,
      this.selfId,
      pc,
      this.local.stream,
      this.local.track,
      this.signal,
      this.opus
    );
    this.peers.set(peerId, peer);
    return peer;
  }

  addPeer(peerId: string): Peer {
    const peer = this.createPeer(peerId);
    peer.start();
    return peer;
  }

  private rebuild(peerId: string): void {
    const old = this.peers.get(peerId);
    if (!old) return;
    old.close();
    this.peers.delete(peerId);
    const peer = this.createPeer(peerId);
    peer.start();
  }

  removePeer(peerId: string): void {
    const peer = this.peers.get(peerId);
    if (!peer) return;
    peer.close();
    this.peers.delete(peerId);
    this.sinks.remove(peerId);
  }

  async routeOffer(from: string, sdp: string): Promise<void> {
    const peer = this.ensurePeer(from);
    await peer.onOffer(sdp);
  }

  async routeAnswer(from: string, sdp: string): Promise<void> {
    const peer = this.peers.get(from);
    if (peer) await peer.onAnswer(sdp);
  }

  async routeIce(from: string, candidate: IceCandidateLike): Promise<void> {
    const peer = this.peers.get(from);
    if (peer) await peer.onIce(candidate);
  }

  iceRestartAll(): void {
    for (const peer of this.peers.values()) peer.iceRestart();
  }

  async replaceTrackAll(track: TrackLike): Promise<void> {
    if (this.local) this.local.track = track;
    for (const peer of this.peers.values()) await peer.replaceTrack(track);
  }

  teardown(): void {
    for (const peer of this.peers.values()) peer.close();
    this.peers.clear();
    this.sinks.clear();
  }
}
