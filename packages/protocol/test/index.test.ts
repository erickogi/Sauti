import { describe, it, expect } from 'vitest';
import * as protocol from '../src/index.js';

describe('package barrel', () => {
  it('re-exports the version constant, model schemas, politeness, and frame parsers', () => {
    expect(protocol.PROTOCOL_VERSION).toBe(1);
    expect(typeof protocol.politePeer).toBe('function');
    expect(typeof protocol.isPolite).toBe('function');
    expect(typeof protocol.parseClientFrame).toBe('function');
    expect(typeof protocol.parseServerFrame).toBe('function');
    expect(typeof protocol.isVersionMismatch).toBe('function');
    expect(protocol.participantSchema.safeParse({}).success).toBe(false);
    expect(
      protocol.parseClientFrame({ v: 1, type: 'join', token: 't' }).success
    ).toBe(true);
  });
});
