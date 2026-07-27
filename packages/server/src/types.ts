export interface RedisPort {
  hsetnx(key: string, field: string, value: string): Promise<0 | 1>;
  hget(key: string, field: string): Promise<string | null>;
  hgetall(key: string): Promise<Record<string, string>>;
  hdel(key: string, field: string): Promise<void>;
  pexpire(key: string, ms: number): Promise<void>;
  eval(script: string, keys: string[], args: string[]): Promise<unknown>;
  publish(channel: string, message: string): Promise<void>;
  subscribe(channel: string, handler: (message: string) => void): Promise<void>;
  unsubscribe(channel: string, handler: (message: string) => void): Promise<void>;
}

export interface TurnConfig {
  urls: string[];
  secret: string;
  ttlSeconds: number;
  realm?: string;
}

export interface AuthorizeResult {
  roomId: string;
  participantId: string;
  metadata?: Record<string, unknown>;
  slotGeneration?: string | number;
}

export type AuthorizeFn = (token: string) => Promise<AuthorizeResult | null>;

export interface SautiEvent {
  type: string;
  roomId: string;
  participantId?: string;
  at: number;
  data?: Record<string, unknown>;
}

export type EventSink = (event: SautiEvent) => void;

export interface RateLimiter {
  allowConnect(key: string): Promise<boolean>;
  allowFrame(participantId: string): Promise<boolean>;
}

export interface CreateServerDeps {
  redis: RedisPort;
  turn: TurnConfig;
  authorize: AuthorizeFn;
  namespace: string;
  maxParticipantsPerRoom?: number;
  graceMs?: number;
  heartbeatMs?: number;
  allowedOrigins?: string[];
  requireOrigin?: boolean;
  onEvent?: EventSink;
  rateLimiter?: RateLimiter;
}

export interface ActiveRoom {
  roomId: string;
  participantCount: number;
  participantIds: string[];
  startedAt: number | null;
}

export interface SautiServer {
  attach(httpServer: import('http').Server, opts?: { path?: string }): void;
  listActiveRooms(): Promise<ActiveRoom[]>;
  close(): Promise<void>;
}
