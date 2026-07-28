import { describe, it, expect } from 'vitest';
import type { CallSnapshot, ParticipantView, Quality } from '@sauti/core';
import {
  defaultLabels,
  resolveLabels,
  qualityLabel,
  qualityToken,
  isReconnecting
} from '../src/labels.js';
import { formatDuration } from '../src/hooks/useDurationLabel.js';
import { deriveStatusKey } from '../src/hooks/useCallStatus.js';
import { resolveName, shortId } from '../src/hooks/useParticipantRoster.js';
import { themeToStyle } from '../src/theme.js';

function snap(overrides: Partial<CallSnapshot>): CallSnapshot {
  return {
    phase: 'idle',
    participants: [],
    localMuted: false,
    localOnHold: false,
    durationMs: 0,
    quality: 'good',
    reconnecting: false,
    audioBlocked: false,
    fallback: false,
    ...overrides
  };
}

function part(overrides: Partial<ParticipantView>): ParticipantView {
  return {
    participantId: 'p-1',
    metadata: {},
    muted: false,
    onHold: false,
    connectionState: 'connected',
    quality: 'good',
    ...overrides
  };
}

describe('labels', () => {
  it('returns the defaults when no overrides are given', () => {
    expect(resolveLabels()).toBe(defaultLabels);
  });

  it('merges partial overrides onto the defaults', () => {
    const merged = resolveLabels({ you: 'Me' });
    expect(merged.you).toBe('Me');
    expect(merged.end).toBe(defaultLabels.end);
  });

  it('maps quality to labels including the fallback branch', () => {
    expect(qualityLabel('good', defaultLabels)).toBe('Good');
    expect(qualityLabel('degraded', defaultLabels)).toBe('Fair');
    expect(qualityLabel('poor', defaultLabels)).toBe('Poor');
    expect(qualityLabel('other' as unknown as Quality, defaultLabels)).toBe('Fair');
  });

  it('maps quality to a css token including the fallback branch', () => {
    expect(qualityToken('good')).toBe('good');
    expect(qualityToken('degraded')).toBe('fair');
    expect(qualityToken('poor')).toBe('poor');
    expect(qualityToken('other' as unknown as Quality)).toBe('fair');
  });

  it('defaults the reconnecting label to the ellipsis spelling', () => {
    expect(defaultLabels.statusReconnecting).toBe('Reconnecting…');
  });
});

describe('isReconnecting', () => {
  it('reduces reconnecting and unreachable to true and leaves the rest false', () => {
    expect(isReconnecting('reconnecting')).toBe(true);
    expect(isReconnecting('unreachable')).toBe(true);
    expect(isReconnecting('connected')).toBe(false);
    expect(isReconnecting('connecting')).toBe(false);
    expect(isReconnecting('left')).toBe(false);
  });
});

describe('formatDuration', () => {
  it('formats sub-hour durations as mm:ss', () => {
    expect(formatDuration(0)).toBe('00:00');
    expect(formatDuration(-500)).toBe('00:00');
    expect(formatDuration(65000)).toBe('01:05');
  });

  it('formats durations over an hour with a leading hours field', () => {
    expect(formatDuration(3661000)).toBe('1:01:01');
  });
});

describe('deriveStatusKey', () => {
  it('covers every phase and the reconnecting override', () => {
    expect(deriveStatusKey(snap({ phase: 'left' }))).toBe('ended');
    expect(deriveStatusKey(snap({ phase: 'connected', reconnecting: true }))).toBe(
      'reconnecting'
    );
    expect(deriveStatusKey(snap({ phase: 'connecting' }))).toBe('connecting');
    expect(deriveStatusKey(snap({ phase: 'connected' }))).toBe('connected');
    expect(deriveStatusKey(snap({ phase: 'idle' }))).toBe('idle');
  });

  it('reports reconnecting when a non-self peer is reconnecting or unreachable', () => {
    const withPeer = (state: ParticipantView['connectionState']) =>
      snap({
        phase: 'connected',
        reconnecting: false,
        participants: [
          part({ participantId: 'p-self' }),
          part({ participantId: 'p-peer', connectionState: state })
        ]
      });
    expect(deriveStatusKey(withPeer('reconnecting'), 'p-self')).toBe('reconnecting');
    expect(deriveStatusKey(withPeer('unreachable'), 'p-self')).toBe('reconnecting');
    expect(deriveStatusKey(withPeer('connected'), 'p-self')).toBe('connected');
  });

  it('ignores a self-only reconnection and keeps the phase-derived key', () => {
    const snapshot = snap({
      phase: 'connected',
      reconnecting: false,
      participants: [
        part({ participantId: 'p-self', connectionState: 'reconnecting' }),
        part({ participantId: 'p-peer', connectionState: 'connected' })
      ]
    });
    expect(deriveStatusKey(snapshot, 'p-self')).toBe('connected');
  });
});

describe('participant naming', () => {
  it('prefers a trimmed metadata name', () => {
    expect(resolveName(part({ metadata: { name: '  Ada  ' } }))).toBe('Ada');
  });

  it('falls back to a short id when the name is blank or missing', () => {
    expect(resolveName(part({ participantId: 'p-1', metadata: { name: '   ' } }))).toBe(
      'p-1'
    );
    expect(resolveName(part({ participantId: 'p-1', metadata: {} }))).toBe('p-1');
  });

  it('truncates long ids', () => {
    expect(shortId('short')).toBe('short');
    expect(shortId('0123456789abcdef')).toBe('0123456789ab…');
  });
});

describe('themeToStyle', () => {
  it('returns an empty object when no theme is given', () => {
    expect(themeToStyle()).toEqual({});
  });

  it('maps theme keys to namespaced custom properties and skips undefined', () => {
    const style = themeToStyle({ colorBg: '#000', touchTarget: '48px', radius: undefined });
    expect(style).toMatchObject({
      '--sauti-color-bg': '#000',
      '--sauti-touch-target': '48px'
    });
    expect('--sauti-radius' in (style as Record<string, string>)).toBe(false);
  });
});
