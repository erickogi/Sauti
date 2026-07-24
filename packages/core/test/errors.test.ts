import { describe, it, expect } from 'vitest';
import { makeCall, URL, TOKEN } from './harness.js';
import { flush, makeRuntime } from './fakes.js';
import {
  InsecureTransportError,
  MicError,
  RoomFullError,
  SautiError,
  VersionMismatchError,
  mapMicError
} from '../src/errors.js';

describe('errors', () => {
  it('throws InsecureTransport before opening a socket for ws:// or insecure pages [ERR-01]', async () => {
    const { h, call } = makeCall();
    await expect(call.join({ url: 'ws://sauti.test', token: TOKEN })).rejects.toMatchObject({
      code: 'insecure-transport'
    });
    expect(h.sockets).toHaveLength(0);

    const insecure = makeRuntime({ isSecureContext: () => false });
    const call2 = makeCall({}, insecure).call;
    await expect(call2.join({ url: URL, token: TOKEN })).rejects.toMatchObject({
      code: 'insecure-transport'
    });
    expect(insecure.sockets).toHaveLength(0);
  });

  it('maps getUserMedia failures to distinct typed errors [ERR-02]', async () => {
    const cases: [string, string][] = [
      ['NotAllowedError', 'permission-denied'],
      ['NotFoundError', 'no-hardware'],
      ['NotReadableError', 'in-use'],
      ['TrackStartError', 'in-use']
    ];
    for (const [name, code] of cases) {
      const h = makeRuntime();
      h.setGetUserMedia(async () => {
        throw { name };
      });
      const { call } = makeCall({}, h);
      await expect(call.acquireMic()).rejects.toMatchObject({ code });
    }
  });

  it('exposes acquireMic independently and does not swallow mic errors in join [ERR-03]', async () => {
    const okRuntime = makeRuntime();
    const okCall = makeCall({}, okRuntime).call;
    await expect(okCall.acquireMic()).resolves.toBeUndefined();

    const badRuntime = makeRuntime();
    badRuntime.setGetUserMedia(async () => {
      throw { name: 'NotAllowedError' };
    });
    const badCall = makeCall({}, badRuntime).call;
    await expect(badCall.join({ url: URL, token: TOKEN })).rejects.toMatchObject({
      code: 'permission-denied'
    });
    await flush();
  });

  it('maps the remaining DOMException names and unknown shapes [ERR-02]', () => {
    expect(mapMicError({ name: 'SecurityError' }).code).toBe('permission-denied');
    expect(mapMicError({ name: 'OverconstrainedError' }).code).toBe('no-hardware');
    expect(mapMicError({ name: 'SomethingElse' }).code).toBe('mic-unknown');
    expect(mapMicError('a bare string').code).toBe('mic-unknown');
    expect(mapMicError(null).code).toBe('mic-unknown');
  });

  it('every typed error carries a stable code from the enum [ERR-04]', () => {
    expect(new InsecureTransportError('x').code).toBe('insecure-transport');
    expect(new RoomFullError('x').code).toBe('room-full');
    expect(new VersionMismatchError({ v: 2 }).code).toBe('version-mismatch');
    expect(new MicError('in-use', 'x').code).toBe('in-use');
    expect(new InsecureTransportError('x')).toBeInstanceOf(SautiError);
  });
});
