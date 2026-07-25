import { createSautiCall, type SautiCall } from '@sauti/core';

interface RemoteAudioReport {
  peerConnectionCount: number;
  liveAudioReceivers: number;
  bytesReceived: number;
  audioLevel: number;
}

interface SautiHandle {
  join(url: string, token: string): Promise<void>;
  getState(): unknown;
  setMuted(muted: boolean): void;
  remoteAudio(): Promise<RemoteAudioReport>;
  localDescriptionType(): string | null;
}

declare global {
  interface Window {
    __sauti: SautiHandle;
  }
}

const NativePeerConnection = window.RTCPeerConnection;
const nativeGetStats = NativePeerConnection.prototype.getStats;
const peerConnections: RTCPeerConnection[] = [];

class RecordingPeerConnection extends NativePeerConnection {
  constructor(config?: RTCConfiguration) {
    super(config);
    peerConnections.push(this);
  }
}

window.RTCPeerConnection =
  RecordingPeerConnection as unknown as typeof RTCPeerConnection;

let call: SautiCall | null = null;

async function remoteAudio(): Promise<RemoteAudioReport> {
  let liveAudioReceivers = 0;
  let bytesReceived = 0;
  let audioLevel = 0;
  for (const pc of peerConnections) {
    for (const receiver of pc.getReceivers()) {
      const track = receiver.track;
      if (track && track.kind === 'audio' && track.readyState === 'live') {
        liveAudioReceivers += 1;
      }
    }
    const report = await nativeGetStats.call(pc);
    report.forEach((entry) => {
      const stat = entry as Record<string, unknown>;
      if (stat.type === 'inbound-rtp' && stat.kind === 'audio') {
        if (typeof stat.bytesReceived === 'number') {
          bytesReceived += stat.bytesReceived;
        }
        if (typeof stat.audioLevel === 'number') {
          audioLevel = Math.max(audioLevel, stat.audioLevel);
        }
      }
    });
  }
  return {
    peerConnectionCount: peerConnections.length,
    liveAudioReceivers,
    bytesReceived,
    audioLevel
  };
}

function localDescriptionType(): string | null {
  const pc = peerConnections[0];
  if (!pc || !pc.localDescription) return null;
  return pc.localDescription.type;
}

window.__sauti = {
  async join(url, token) {
    call = createSautiCall();
    await call.join({ url, token });
  },
  getState() {
    return call ? call.getSnapshot() : null;
  },
  setMuted(muted) {
    call?.setMuted(muted);
  },
  remoteAudio,
  localDescriptionType
};
