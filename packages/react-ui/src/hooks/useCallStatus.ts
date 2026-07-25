import { useMemo } from 'react';
import type { CallSnapshot } from '@sauti/core';
import type { SautiLabels } from '../labels.js';

export type CallStatusKey =
  | 'idle'
  | 'connecting'
  | 'reconnecting'
  | 'connected'
  | 'ended';

export interface CallStatusView {
  key: CallStatusKey;
  text: string;
}

export function deriveStatusKey(snapshot: CallSnapshot): CallStatusKey {
  if (snapshot.phase === 'left') return 'ended';
  if (snapshot.reconnecting) return 'reconnecting';
  if (snapshot.phase === 'connecting') return 'connecting';
  if (snapshot.phase === 'connected') return 'connected';
  return 'idle';
}

export function useCallStatus(
  snapshot: CallSnapshot,
  labels: SautiLabels
): CallStatusView {
  return useMemo(() => {
    const key = deriveStatusKey(snapshot);
    const text: Record<CallStatusKey, string> = {
      idle: labels.statusIdle,
      connecting: labels.statusConnecting,
      reconnecting: labels.statusReconnecting,
      connected: labels.statusInCall,
      ended: labels.statusEnded
    };
    return { key, text: text[key] };
  }, [snapshot, labels]);
}
