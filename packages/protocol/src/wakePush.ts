import { z } from 'zod';

export const WAKE_PUSH_KIND = 'io.sauti.wake.call' as const;

export type WakePushKind = typeof WAKE_PUSH_KIND;

export const WAKE_PUSH_VERSION = 1 as const;

export type WakePushVersion = typeof WAKE_PUSH_VERSION;

export const WAKE_PUSH_JOIN_HINT_KEY = 'sauti.joinHint' as const;

const META_PREFIX = 'meta.';

export interface WakePushPayload {
  kind: WakePushKind;
  v: WakePushVersion;
  callId: string;
  roomId: string;
  issuedAt: number;
  ttlSeconds: number;
  callerDisplayHint?: string;
  joinHint?: string;
  metadata?: Record<string, string>;
}

export type WakePushParseResult =
  | { ok: true; payload: WakePushPayload }
  | { ok: false; reason: 'wrong-kind' | 'unsupported-version' | 'malformed' };

const bodySchema = z.object({
  callId: z.string().min(1),
  roomId: z.string().min(1),
  issuedAt: z.coerce.number().refine((n) => Number.isFinite(n)),
  ttlSeconds: z.coerce.number().refine((n) => Number.isFinite(n))
});

export function encodeWakePushData(p: WakePushPayload): Record<string, string> {
  const data: Record<string, string> = {
    kind: p.kind,
    v: String(p.v),
    callId: p.callId,
    roomId: p.roomId,
    issuedAt: String(p.issuedAt),
    ttlSeconds: String(p.ttlSeconds)
  };
  if (p.callerDisplayHint !== undefined) {
    data['callerDisplayHint'] = p.callerDisplayHint;
  }
  if (p.joinHint !== undefined) {
    data['joinHint'] = p.joinHint;
  }
  if (p.metadata !== undefined) {
    for (const [key, value] of Object.entries(p.metadata)) {
      data[META_PREFIX + key] = value;
    }
  }
  return data;
}

export function decodeWakePushData(
  data: Record<string, string>
): WakePushParseResult {
  if (data['kind'] !== WAKE_PUSH_KIND) {
    return { ok: false, reason: 'wrong-kind' };
  }
  if (String(data['v']) !== String(WAKE_PUSH_VERSION)) {
    return { ok: false, reason: 'unsupported-version' };
  }
  const parsed = bodySchema.safeParse(data);
  if (!parsed.success) {
    return { ok: false, reason: 'malformed' };
  }
  const collected: Record<string, string> = {};
  for (const [key, value] of Object.entries(data)) {
    if (key.startsWith(META_PREFIX)) {
      collected[key.slice(META_PREFIX.length)] = value;
    }
  }
  const payload: WakePushPayload = {
    kind: WAKE_PUSH_KIND,
    v: WAKE_PUSH_VERSION,
    callId: parsed.data.callId,
    roomId: parsed.data.roomId,
    issuedAt: parsed.data.issuedAt,
    ttlSeconds: parsed.data.ttlSeconds,
    metadata: collected
  };
  if (data['callerDisplayHint'] !== undefined) {
    payload.callerDisplayHint = data['callerDisplayHint'];
  }
  if (data['joinHint'] !== undefined) {
    payload.joinHint = data['joinHint'];
  }
  return { ok: true, payload };
}

export function isWakePushExpired(p: WakePushPayload, now: number): boolean {
  return now > p.issuedAt + p.ttlSeconds * 1000;
}
