import { describe, it, expect } from 'vitest';
import type { CallSnapshot } from '@sauti/core';
import { ringbackMode } from '../src/hooks/ringbackMode.js';
import { emptySnapshot, participant } from './fakeCall.js';

function snapshot(overrides: Partial<CallSnapshot>): CallSnapshot {
  return { ...emptySnapshot(), ...overrides };
}

describe('ringbackMode', () => {
  it('is silent at idle', () => {
    expect(ringbackMode(snapshot({ phase: 'idle' }))).toBe('silent');
  });

  it('rings while connecting', () => {
    expect(ringbackMode(snapshot({ phase: 'connecting' }))).toBe('ringing');
  });

  it('rings while connecting even with a remote peer present', () => {
    const mode = ringbackMode(
      snapshot({ phase: 'connecting', participants: [participant({ participantId: 'peer-1' })] }),
      'self-1'
    );
    expect(mode).toBe('ringing');
  });

  it('rings when connected with only self present', () => {
    const mode = ringbackMode(
      snapshot({ phase: 'connected', participants: [participant({ participantId: 'self-1' })] }),
      'self-1'
    );
    expect(mode).toBe('ringing');
  });

  it('rings when connected with an empty roster and a self id', () => {
    expect(ringbackMode(snapshot({ phase: 'connected', participants: [] }), 'self-1')).toBe(
      'ringing'
    );
  });

  it('falls silent when connected with a remote peer present', () => {
    const mode = ringbackMode(
      snapshot({
        phase: 'connected',
        participants: [
          participant({ participantId: 'self-1' }),
          participant({ participantId: 'peer-1' })
        ]
      }),
      'self-1'
    );
    expect(mode).toBe('silent');
  });

  it('is silent after leaving', () => {
    expect(ringbackMode(snapshot({ phase: 'left' }))).toBe('silent');
  });

  it('treats any participant as remote when no self id is given', () => {
    const mode = ringbackMode(
      snapshot({ phase: 'connected', participants: [participant({ participantId: 'self-1' })] })
    );
    expect(mode).toBe('silent');
  });

  it('rings when connected with an empty roster and no self id', () => {
    expect(ringbackMode(snapshot({ phase: 'connected', participants: [] }))).toBe('ringing');
  });

  it('is pure: same input yields the same output and mutates nothing', () => {
    const input = snapshot({
      phase: 'connected',
      participants: [participant({ participantId: 'self-1' })]
    });
    const frozen = Object.freeze(input);
    const first = ringbackMode(frozen, 'self-1');
    const second = ringbackMode(frozen, 'self-1');
    expect(first).toBe('ringing');
    expect(second).toBe(first);
    expect(frozen.participants).toHaveLength(1);
  });
});
