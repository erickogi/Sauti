import { createHash, createHmac } from 'node:crypto';
import type { TurnConfig } from './types.js';

export interface IceServer {
  urls: string[];
  username: string;
  credential: string;
  realm?: string;
}

export function opaqueRef(participantId: string): string {
  return createHash('sha256').update(participantId).digest('hex').slice(0, 16);
}

export function mintIceServers(
  turn: TurnConfig,
  participantId: string,
  now: number
): IceServer[] {
  const exp = Math.floor(now / 1000) + turn.ttlSeconds;
  const username = `${exp}:${opaqueRef(participantId)}`;
  const credential = createHmac('sha1', turn.secret)
    .update(username)
    .digest('base64');
  const server: IceServer = { urls: turn.urls, username, credential };
  if (turn.realm !== undefined) {
    server.realm = turn.realm;
  }
  return [server];
}
