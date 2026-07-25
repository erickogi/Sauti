import { vi } from 'vitest';
import type { CallSnapshot, DeviceInfo, ParticipantView, SautiCall } from '@sauti/core';

export function emptySnapshot(): CallSnapshot {
  return {
    phase: 'idle',
    participants: [],
    localMuted: false,
    localOnHold: false,
    durationMs: 0,
    quality: 'good',
    reconnecting: false,
    audioBlocked: false,
    fallback: false
  };
}

export function participant(overrides: Partial<ParticipantView>): ParticipantView {
  return {
    participantId: 'p-default',
    metadata: {},
    muted: false,
    onHold: false,
    connectionState: 'connected',
    quality: 'good',
    ...overrides
  };
}

export class FakeCall implements SautiCall {
  private listeners = new Set<() => void>();
  private snapshot: CallSnapshot = emptySnapshot();
  private devices: DeviceInfo[] = [];

  join = vi.fn(async () => undefined);
  leave = vi.fn(() => undefined);
  setMuted = vi.fn((muted: boolean) => {
    this.push({ localMuted: muted });
  });
  setHold = vi.fn(() => undefined);
  acquireMic = vi.fn(async () => undefined);
  unlockAudio = vi.fn(async () => undefined);
  enumerateDevices = vi.fn(async () => this.devices);
  selectInputDevice = vi.fn(async () => undefined);
  on = vi.fn(() => () => undefined);
  off = vi.fn(() => undefined);

  getSnapshot = (): CallSnapshot => this.snapshot;

  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };

  listenerCount(): number {
    return this.listeners.size;
  }

  setDevices(devices: DeviceInfo[]): void {
    this.devices = devices;
  }

  push(next: Partial<CallSnapshot>): void {
    this.snapshot = { ...this.snapshot, ...next };
    for (const listener of [...this.listeners]) listener();
  }
}
