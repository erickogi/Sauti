import type { ParticipantView, Quality } from '@sauti/core';

type ConnectionState = ParticipantView['connectionState'];

export interface SautiLabels {
  you: string;
  mute: string;
  unmute: string;
  muted: string;
  onHold: string;
  end: string;
  statusIdle: string;
  statusConnecting: string;
  statusReconnecting: string;
  statusInCall: string;
  statusEnded: string;
  qualityGood: string;
  qualityFair: string;
  qualityPoor: string;
  unlockAudio: string;
  weakConnectionTitle: string;
  weakConnectionBody: string;
  participantsHeading: string;
  microphoneLabel: string;
  microphoneDefault: string;
}

export const defaultLabels: SautiLabels = {
  you: 'You',
  mute: 'Mute',
  unmute: 'Unmute',
  muted: 'Muted',
  onHold: 'On hold',
  end: 'End',
  statusIdle: 'Ready',
  statusConnecting: 'Connecting',
  statusReconnecting: 'Reconnecting…',
  statusInCall: 'In call',
  statusEnded: 'Ended',
  qualityGood: 'Good',
  qualityFair: 'Fair',
  qualityPoor: 'Poor',
  unlockAudio: 'Enable audio',
  weakConnectionTitle: 'Weak connection',
  weakConnectionBody: 'The connection is unstable.',
  participantsHeading: 'Participants',
  microphoneLabel: 'Microphone',
  microphoneDefault: 'Default'
};

export function resolveLabels(overrides?: Partial<SautiLabels>): SautiLabels {
  return overrides ? { ...defaultLabels, ...overrides } : defaultLabels;
}

export function qualityLabel(quality: Quality, labels: SautiLabels): string {
  switch (quality) {
    case 'good':
      return labels.qualityGood;
    case 'degraded':
      return labels.qualityFair;
    case 'poor':
      return labels.qualityPoor;
    default:
      return labels.qualityFair;
  }
}

export function isReconnecting(state: ConnectionState): boolean {
  return state === 'reconnecting' || state === 'unreachable';
}

export function qualityToken(quality: Quality): 'good' | 'fair' | 'poor' {
  switch (quality) {
    case 'good':
      return 'good';
    case 'degraded':
      return 'fair';
    case 'poor':
      return 'poor';
    default:
      return 'fair';
  }
}
