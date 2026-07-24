export type SautiErrorCode =
  | 'insecure-transport'
  | 'permission-denied'
  | 'no-hardware'
  | 'in-use'
  | 'mic-unknown'
  | 'room-full'
  | 'version-mismatch'
  | 'disconnected'
  | 'server-error';

export class SautiError extends Error {
  readonly code: SautiErrorCode;

  constructor(code: SautiErrorCode, message: string) {
    super(message);
    this.name = 'SautiError';
    this.code = code;
  }
}

export class InsecureTransportError extends SautiError {
  constructor(message: string) {
    super('insecure-transport', message);
    this.name = 'InsecureTransportError';
  }
}

export class RoomFullError extends SautiError {
  constructor(message: string) {
    super('room-full', message);
    this.name = 'RoomFullError';
  }
}

export class VersionMismatchError extends SautiError {
  readonly received: unknown;

  constructor(received: unknown) {
    super('version-mismatch', 'server frame carried an unsupported protocol version');
    this.name = 'VersionMismatchError';
    this.received = received;
  }
}

export class DisconnectedError extends SautiError {
  constructor(message: string) {
    super('disconnected', message);
    this.name = 'DisconnectedError';
  }
}

export class ServerFrameError extends SautiError {
  readonly serverCode: string;

  constructor(serverCode: string, message: string) {
    super('server-error', message);
    this.name = 'ServerFrameError';
    this.serverCode = serverCode;
  }
}

export class MicError extends SautiError {}

const FATAL_SERVER_CODES = new Set([
  'unauthorized',
  'forbidden',
  'room-not-found',
  'room-closed',
  'fatal'
]);

export function isFatalServerCode(code: string): boolean {
  return FATAL_SERVER_CODES.has(code);
}

export function mapMicError(reason: unknown): MicError {
  const name =
    typeof reason === 'object' && reason !== null && 'name' in reason
      ? String((reason as { name: unknown }).name)
      : '';
  if (name === 'NotAllowedError' || name === 'SecurityError') {
    return new MicError('permission-denied', 'microphone permission was denied');
  }
  if (name === 'NotFoundError' || name === 'OverconstrainedError') {
    return new MicError('no-hardware', 'no microphone hardware is available');
  }
  if (name === 'NotReadableError' || name === 'TrackStartError') {
    return new MicError('in-use', 'the microphone is already in use');
  }
  return new MicError('mic-unknown', 'the microphone could not be acquired');
}
