import { PROTOCOL_VERSION, isPolite } from '@sauti/protocol';
import { transformOpus, type OpusOptions } from './sdp.js';
import type {
  IceCandidateLike,
  PeerLike,
  SenderLike,
  StreamLike,
  TrackLike
} from './types.js';

export interface PeerSignal {
  send(frame: unknown): void;
  onRemoteStream(peerId: string, stream: StreamLike): void;
  onUnrecoverable(peerId: string): void;
  onConnected(peerId: string): void;
  onError(error: unknown): void;
}

export class Peer {
  readonly peerId: string;
  readonly pc: PeerLike;
  readonly polite: boolean;

  private makingOffer = false;
  private ignoreOffer = false;
  private restartAttempted = false;
  private localTrack: TrackLike;

  constructor(
    peerId: string,
    selfId: string,
    pc: PeerLike,
    localStream: StreamLike,
    localTrack: TrackLike,
    private readonly signal: PeerSignal,
    private readonly opus: OpusOptions = {}
  ) {
    this.peerId = peerId;
    this.pc = pc;
    this.polite = isPolite(selfId, peerId);
    this.localTrack = localTrack;

    pc.addTrack(localTrack, localStream);

    pc.onnegotiationneeded = () => {
      void this.makeOffer();
    };
    pc.onicecandidate = ({ candidate }) => {
      if (candidate) {
        this.signal.send({
          v: PROTOCOL_VERSION,
          type: 'ice',
          to: peerId,
          candidate
        });
      }
    };
    pc.ontrack = (ev) => {
      const stream = ev.streams[0];
      if (stream) this.signal.onRemoteStream(peerId, stream);
    };
    pc.oniceconnectionstatechange = () => {
      const state = pc.iceConnectionState;
      if (state === 'connected' || state === 'completed') {
        this.restartAttempted = false;
        this.signal.onConnected(peerId);
      } else if (state === 'failed') {
        this.recover();
      }
    };
  }

  start(): void {
    if (!this.polite) void this.makeOffer();
  }

  private async makeOffer(): Promise<void> {
    if (this.makingOffer) return;
    this.makingOffer = true;
    try {
      const offer = await this.pc.createOffer();
      const sdp = transformOpus(offer.sdp ?? '', { fec: this.opus.fec ?? false, dtx: false });
      await this.pc.setLocalDescription({ type: 'offer', sdp });
      this.signal.send({
        v: PROTOCOL_VERSION,
        type: 'offer',
        to: this.peerId,
        sdp
      });
    } catch (error) {
      this.signal.onError(error);
    } finally {
      this.makingOffer = false;
    }
  }

  async onOffer(sdp: string): Promise<void> {
    const collision = this.makingOffer || this.pc.signalingState !== 'stable';
    this.ignoreOffer = !this.polite && collision;
    if (this.ignoreOffer) return;
    if (collision) {
      await this.pc.setLocalDescription({ type: 'rollback' });
    }
    await this.pc.setRemoteDescription({ type: 'offer', sdp });
    const answer = await this.pc.createAnswer();
    const local = transformOpus(answer.sdp ?? '', { fec: this.opus.fec ?? false, dtx: false });
    await this.pc.setLocalDescription({ type: 'answer', sdp: local });
    this.signal.send({
      v: PROTOCOL_VERSION,
      type: 'answer',
      to: this.peerId,
      sdp: local
    });
  }

  async onAnswer(sdp: string): Promise<void> {
    await this.pc.setRemoteDescription({ type: 'answer', sdp });
  }

  async onIce(candidate: IceCandidateLike): Promise<void> {
    try {
      await this.pc.addIceCandidate(candidate);
    } catch (error) {
      if (!this.ignoreOffer) this.signal.onError(error);
    }
  }

  iceRestart(): void {
    this.pc.restartIce();
  }

  private recover(): void {
    if (!this.restartAttempted) {
      this.restartAttempted = true;
      this.pc.restartIce();
    } else {
      this.signal.onUnrecoverable(this.peerId);
    }
  }

  async replaceTrack(track: TrackLike): Promise<void> {
    this.localTrack = track;
    for (const sender of this.senders()) {
      await sender.replaceTrack(track);
    }
  }

  senders(): SenderLike[] {
    return this.pc.getSenders();
  }

  close(): void {
    this.pc.onicecandidate = null;
    this.pc.ontrack = null;
    this.pc.onnegotiationneeded = null;
    this.pc.oniceconnectionstatechange = null;
    this.pc.onconnectionstatechange = null;
    this.pc.close();
  }
}
