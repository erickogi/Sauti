import { vi } from 'vitest';
import type { CallSnapshot, DeviceInfo } from '@sauti/core';
import type { SautiCallBinding } from '@sauti/react';
import { emptySnapshot } from './fakeCall.js';

export interface FakeBinding extends SautiCallBinding {
  join: ReturnType<typeof vi.fn>;
  leave: ReturnType<typeof vi.fn>;
  setMuted: ReturnType<typeof vi.fn>;
  setHold: ReturnType<typeof vi.fn>;
  acquireMic: ReturnType<typeof vi.fn>;
  unlockAudio: ReturnType<typeof vi.fn>;
  enumerateDevices: ReturnType<typeof vi.fn>;
  selectInputDevice: ReturnType<typeof vi.fn>;
}

export function fakeBinding(
  snapshot: Partial<CallSnapshot> = {},
  devices: DeviceInfo[] = []
): FakeBinding {
  return {
    ...emptySnapshot(),
    ...snapshot,
    join: vi.fn(async () => undefined),
    leave: vi.fn(() => undefined),
    setMuted: vi.fn(() => undefined),
    setHold: vi.fn(() => undefined),
    acquireMic: vi.fn(async () => undefined),
    unlockAudio: vi.fn(async () => undefined),
    enumerateDevices: vi.fn(async () => devices),
    selectInputDevice: vi.fn(async () => undefined)
  };
}
