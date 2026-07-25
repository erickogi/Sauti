import { useCallback, useEffect, useState } from 'react';
import type { DeviceInfo } from '@sauti/core';
import type { SautiCallBinding } from '@sauti/react';

export interface AudioDevicesState {
  devices: DeviceInfo[];
  selectedId: string | null;
  refresh(): Promise<void>;
  select(deviceId: string): Promise<void>;
}

export function useAudioDevices(binding: SautiCallBinding): AudioDevicesState {
  const { enumerateDevices, selectInputDevice } = binding;
  const [devices, setDevices] = useState<DeviceInfo[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    const all = await enumerateDevices();
    setDevices(all.filter((device) => device.kind === 'audioinput'));
  }, [enumerateDevices]);

  const select = useCallback(
    async (deviceId: string) => {
      await selectInputDevice(deviceId);
      setSelectedId(deviceId);
    },
    [selectInputDevice]
  );

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return { devices, selectedId, refresh, select };
}
