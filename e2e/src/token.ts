import type { AuthorizeResult } from '@sauti/server';

export interface TokenClaims {
  roomId: string;
  participantId: string;
  slotGeneration?: string;
}

export function mintToken(claims: TokenClaims): string {
  return Buffer.from(JSON.stringify(claims), 'utf8').toString('base64url');
}

export function decodeToken(token: string): AuthorizeResult | null {
  try {
    const claims = JSON.parse(
      Buffer.from(token, 'base64url').toString('utf8')
    ) as TokenClaims;
    if (
      typeof claims.roomId !== 'string' ||
      typeof claims.participantId !== 'string'
    ) {
      return null;
    }
    return claims;
  } catch {
    return null;
  }
}
