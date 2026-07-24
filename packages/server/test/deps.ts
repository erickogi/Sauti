import { FakeRedisPort } from './fakeRedis.js';
import type {
  AuthorizeResult,
  CreateServerDeps,
  SautiEvent,
  TurnConfig
} from '../src/types.js';

export const turn: TurnConfig = {
  urls: ['turn:turn.example:3478'],
  secret: 'static-auth-secret-value',
  ttlSeconds: 3600,
  realm: 'sauti'
};

export function tokenFor(
  roomId: string,
  participantId: string,
  extra?: { slotGeneration?: string | number; metadata?: Record<string, unknown> }
): string {
  return JSON.stringify({
    roomId,
    participantId,
    slotGeneration: extra?.slotGeneration,
    metadata: extra?.metadata
  });
}

export function makeDeps(
  overrides?: Partial<CreateServerDeps> & { redis?: FakeRedisPort }
): CreateServerDeps & { events: SautiEvent[]; redis: FakeRedisPort } {
  const redis = overrides?.redis ?? new FakeRedisPort();
  const events: SautiEvent[] = [];
  const deps: CreateServerDeps = {
    redis,
    turn,
    namespace: overrides?.namespace ?? 'sauti',
    authorize:
      overrides?.authorize ??
      (async (token: string): Promise<AuthorizeResult | null> => {
        if (token === 'deny') return null;
        try {
          const claims = JSON.parse(token) as AuthorizeResult;
          if (!claims.roomId || !claims.participantId) return null;
          return claims;
        } catch {
          return null;
        }
      }),
    onEvent: overrides?.onEvent ?? ((e) => events.push(e)),
    maxParticipantsPerRoom: overrides?.maxParticipantsPerRoom,
    graceMs: overrides?.graceMs,
    allowedOrigins: overrides?.allowedOrigins,
    requireOrigin: overrides?.requireOrigin,
    rateLimiter: overrides?.rateLimiter
  };
  return { ...deps, events, redis };
}
