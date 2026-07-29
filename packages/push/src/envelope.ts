import { encodeWakePushData, type WakePushPayload } from '@sauti/protocol';

export interface WakePushDeliveryOptions {
  ttlSeconds?: number;
  collapseKey?: string;
}

export interface WakePushEnvelope {
  dataOnly: true;
  priority: 'high';
  ttlSeconds: number;
  collapseKey: string;
  data: Record<string, string>;
}

export function buildWakePushEnvelope(
  payload: WakePushPayload,
  options?: WakePushDeliveryOptions
): WakePushEnvelope {
  return {
    dataOnly: true,
    priority: 'high',
    ttlSeconds: options?.ttlSeconds ?? payload.ttlSeconds,
    collapseKey: options?.collapseKey ?? payload.callId,
    data: encodeWakePushData(payload)
  };
}
