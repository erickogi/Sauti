import { useCallback } from 'react';
import type { SautiCallBinding } from '@sauti/react';

export interface MuteToggle {
  muted: boolean;
  toggle(): void;
}

export function useMuteToggle(binding: SautiCallBinding): MuteToggle {
  const { localMuted, setMuted } = binding;
  const toggle = useCallback(() => {
    setMuted(!localMuted);
  }, [setMuted, localMuted]);
  return { muted: localMuted, toggle };
}
