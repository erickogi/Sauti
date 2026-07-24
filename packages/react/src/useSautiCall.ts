import { useMemo, useSyncExternalStore } from 'react';
import type {
  CallSnapshot,
  DeviceInfo,
  JoinOptions,
  SautiCall
} from '@sauti/core';

export interface SautiCallBinding extends CallSnapshot {
  join(options: JoinOptions): Promise<void>;
  leave(): void;
  setMuted(muted: boolean): void;
  setHold(onHold: boolean): void;
  acquireMic(deviceId?: string): Promise<void>;
  unlockAudio(): Promise<void>;
  enumerateDevices(): Promise<DeviceInfo[]>;
  selectInputDevice(deviceId: string): Promise<void>;
}

export function useSautiCall(call: SautiCall): SautiCallBinding {
  const snapshot = useSyncExternalStore(
    call.subscribe.bind(call),
    call.getSnapshot.bind(call),
    call.getSnapshot.bind(call)
  );

  const commands = useMemo(
    () => ({
      join: (options: JoinOptions) => call.join(options),
      leave: () => call.leave(),
      setMuted: (muted: boolean) => call.setMuted(muted),
      setHold: (onHold: boolean) => call.setHold(onHold),
      acquireMic: (deviceId?: string) => call.acquireMic(deviceId),
      unlockAudio: () => call.unlockAudio(),
      enumerateDevices: () => call.enumerateDevices(),
      selectInputDevice: (deviceId: string) => call.selectInputDevice(deviceId)
    }),
    [call]
  );

  return { ...snapshot, ...commands };
}
