import { describe, it, expect } from 'vitest';
import { politePeer, isPolite } from '../src/politeness.js';

describe('politeness tie-break', () => {
  it('returns the lexicographically smaller id as polite when a < b', () => {
    expect(politePeer('a', 'b')).toBe('a');
    expect(isPolite('a', 'b')).toBe(true);
    expect(isPolite('b', 'a')).toBe(false);
  });

  it('returns the lexicographically smaller id as polite when a > b', () => {
    expect(politePeer('z', 'm')).toBe('m');
    expect(isPolite('z', 'm')).toBe(false);
    expect(isPolite('m', 'z')).toBe(true);
  });

  it('handles identical ids by returning that id', () => {
    expect(politePeer('same', 'same')).toBe('same');
    expect(isPolite('same', 'same')).toBe(true);
  });

  it('matches a plain byte-order string comparison for multi-character ids', () => {
    const ids = ['participant-9', 'participant-10', 'aa', 'ab', 'Z', 'a'];
    for (const a of ids) {
      for (const b of ids) {
        const expected = a <= b ? a : b;
        expect(politePeer(a, b)).toBe(expected);
      }
    }
  });

  it('is deterministic and order-independent for the polite selection', () => {
    expect(politePeer('alice', 'bob')).toBe(politePeer('bob', 'alice'));
  });
});
