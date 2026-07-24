import { describe, it, expect } from 'vitest';
import {
  joinFrameSchema,
  leaveFrameSchema,
  clientStateFrameSchema,
  offerSendSchema,
  answerSendSchema,
  iceSendSchema,
  offerDeliveredSchema,
  answerDeliveredSchema,
  iceDeliveredSchema,
  readyFrameSchema,
  participantJoinedFrameSchema,
  participantStateFrameSchema,
  participantUnreachableFrameSchema,
  participantLeftFrameSchema,
  roomStartedFrameSchema,
  errorFrameSchema,
  clientFrameSchema,
  serverFrameSchema,
  isVersionMismatch,
  parseClientFrame,
  parseServerFrame
} from '../src/frames.js';
import type { Participant } from '../src/model.js';

const participant: Participant = {
  participantId: 'A',
  joinedAt: 111,
  metadata: { seat: 'x' },
  connectionState: 'connected',
  state: { muted: false, onHold: false }
};

describe('join frame', () => {
  it('accepts a valid join and rejects missing token, missing v, or v != 1', () => {
    expect(joinFrameSchema.safeParse({ v: 1, type: 'join', token: 't' }).success).toBe(
      true
    );
    expect(joinFrameSchema.safeParse({ v: 1, type: 'join' }).success).toBe(false);
    expect(joinFrameSchema.safeParse({ type: 'join', token: 't' }).success).toBe(false);
    expect(
      joinFrameSchema.safeParse({ v: 2, type: 'join', token: 't' }).success
    ).toBe(false);
  });
});

describe('ready frame', () => {
  const valid = {
    v: 1,
    type: 'ready',
    self: participant,
    peers: [participant],
    room: { roomId: 'r', startedAt: null, maxParticipants: 3 },
    iceServers: [{ urls: 'turn:host:3478', username: 'u', credential: 'c' }],
    serverNow: 1700000000000,
    resumed: false
  };

  it('accepts a fully populated ready frame', () => {
    const parsed = readyFrameSchema.parse(valid);
    expect(typeof parsed.serverNow).toBe('number');
    expect(typeof parsed.resumed).toBe('boolean');
  });

  it('accepts a numeric room.startedAt', () => {
    expect(
      readyFrameSchema.safeParse({
        ...valid,
        room: { roomId: 'r', startedAt: 123, maxParticipants: 3 }
      }).success
    ).toBe(true);
  });

  it('rejects a ready dropping serverNow, resumed, or room.startedAt', () => {
    const noServerNow = { ...valid } as Record<string, unknown>;
    delete noServerNow.serverNow;
    expect(readyFrameSchema.safeParse(noServerNow).success).toBe(false);

    const noResumed = { ...valid } as Record<string, unknown>;
    delete noResumed.resumed;
    expect(readyFrameSchema.safeParse(noResumed).success).toBe(false);

    expect(
      readyFrameSchema.safeParse({
        ...valid,
        room: { roomId: 'r', maxParticipants: 3 }
      }).success
    ).toBe(false);
  });

  it('rejects a non-boolean resumed and non-number serverNow', () => {
    expect(readyFrameSchema.safeParse({ ...valid, resumed: 'yes' }).success).toBe(
      false
    );
    expect(readyFrameSchema.safeParse({ ...valid, serverNow: '1' }).success).toBe(
      false
    );
  });
});

describe('participant-joined frame', () => {
  it('requires a full Participant', () => {
    expect(
      participantJoinedFrameSchema.safeParse({
        v: 1,
        type: 'participant-joined',
        participant
      }).success
    ).toBe(true);
    expect(
      participantJoinedFrameSchema.safeParse({
        v: 1,
        type: 'participant-joined',
        participant: { participantId: 'A', joinedAt: 1, metadata: {} }
      }).success
    ).toBe(false);
  });
});

describe('participant-state server frame', () => {
  it('accepts a partial state carrying only muted or only onHold or empty', () => {
    for (const state of [{ muted: true }, { onHold: true }, {}]) {
      expect(
        participantStateFrameSchema.safeParse({
          v: 1,
          type: 'participant-state',
          participantId: 'A',
          state
        }).success
      ).toBe(true);
    }
  });

  it('requires participantId', () => {
    expect(
      participantStateFrameSchema.safeParse({
        v: 1,
        type: 'participant-state',
        state: { muted: true }
      }).success
    ).toBe(false);
  });
});

describe('participant-unreachable and participant-left frames', () => {
  it('require participantId', () => {
    expect(
      participantUnreachableFrameSchema.safeParse({
        v: 1,
        type: 'participant-unreachable',
        participantId: 'A'
      }).success
    ).toBe(true);
    expect(
      participantUnreachableFrameSchema.safeParse({
        v: 1,
        type: 'participant-unreachable'
      }).success
    ).toBe(false);
    expect(
      participantLeftFrameSchema.safeParse({
        v: 1,
        type: 'participant-left',
        participantId: 'A'
      }).success
    ).toBe(true);
    expect(
      participantLeftFrameSchema.safeParse({ v: 1, type: 'participant-left' }).success
    ).toBe(false);
  });
});

describe('room-started frame', () => {
  it('requires a concrete numeric startedAt', () => {
    expect(
      roomStartedFrameSchema.safeParse({
        v: 1,
        type: 'room-started',
        startedAt: 123
      }).success
    ).toBe(true);
    expect(
      roomStartedFrameSchema.safeParse({
        v: 1,
        type: 'room-started',
        startedAt: null
      }).success
    ).toBe(false);
    expect(
      roomStartedFrameSchema.safeParse({ v: 1, type: 'room-started' }).success
    ).toBe(false);
  });
});

describe('error frame', () => {
  it('requires code and message', () => {
    expect(
      errorFrameSchema.safeParse({
        v: 1,
        type: 'error',
        code: 'x',
        message: 'y'
      }).success
    ).toBe(true);
    expect(
      errorFrameSchema.safeParse({ v: 1, type: 'error', message: 'y' }).success
    ).toBe(false);
    expect(
      errorFrameSchema.safeParse({ v: 1, type: 'error', code: 'x' }).success
    ).toBe(false);
  });
});

describe('client leave and state frames', () => {
  it('accepts a bare leave frame', () => {
    expect(leaveFrameSchema.safeParse({ v: 1, type: 'leave' }).success).toBe(true);
  });

  it('accepts partial client state and rejects a client-supplied participantId or from', () => {
    expect(
      clientStateFrameSchema.safeParse({
        v: 1,
        type: 'state',
        state: { muted: false }
      }).success
    ).toBe(true);
    expect(
      clientStateFrameSchema.safeParse({
        v: 1,
        type: 'state',
        state: { onHold: true }
      }).success
    ).toBe(true);
    expect(
      clientStateFrameSchema.safeParse({
        v: 1,
        type: 'state',
        state: { muted: true },
        participantId: 'B'
      }).success
    ).toBe(false);
    expect(
      clientStateFrameSchema.safeParse({
        v: 1,
        type: 'state',
        state: { muted: true },
        from: 'B'
      }).success
    ).toBe(false);
  });
});

describe('addressed relay frames', () => {
  it('accepts send-side offer/answer/ice with to set and rejects them with to missing', () => {
    expect(
      offerSendSchema.safeParse({ v: 1, type: 'offer', to: 'B', sdp: 's' }).success
    ).toBe(true);
    expect(offerSendSchema.safeParse({ v: 1, type: 'offer', sdp: 's' }).success).toBe(
      false
    );
    expect(
      answerSendSchema.safeParse({ v: 1, type: 'answer', to: 'B', sdp: 's' }).success
    ).toBe(true);
    expect(
      answerSendSchema.safeParse({ v: 1, type: 'answer', sdp: 's' }).success
    ).toBe(false);
    const cand = { candidate: 'c', sdpMid: null, sdpMLineIndex: null };
    expect(
      iceSendSchema.safeParse({ v: 1, type: 'ice', to: 'B', candidate: cand }).success
    ).toBe(true);
    expect(
      iceSendSchema.safeParse({ v: 1, type: 'ice', candidate: cand }).success
    ).toBe(false);
  });

  it('validates the ice candidate shape including nullable fields', () => {
    expect(
      iceSendSchema.safeParse({
        v: 1,
        type: 'ice',
        to: 'B',
        candidate: { candidate: 'c', sdpMid: 'audio', sdpMLineIndex: 0 }
      }).success
    ).toBe(true);
    expect(
      iceSendSchema.safeParse({
        v: 1,
        type: 'ice',
        to: 'B',
        candidate: { candidate: 'c' }
      }).success
    ).toBe(false);
  });

  it('delivered variants require from and forbid to', () => {
    expect(
      offerDeliveredSchema.safeParse({ v: 1, type: 'offer', from: 'A', sdp: 's' })
        .success
    ).toBe(true);
    expect(
      offerDeliveredSchema.safeParse({ v: 1, type: 'offer', sdp: 's' }).success
    ).toBe(false);
    expect(
      offerDeliveredSchema.safeParse({
        v: 1,
        type: 'offer',
        from: 'A',
        to: 'B',
        sdp: 's'
      }).success
    ).toBe(false);
    expect(
      answerDeliveredSchema.safeParse({ v: 1, type: 'answer', from: 'A', sdp: 's' })
        .success
    ).toBe(true);
    expect(
      iceDeliveredSchema.safeParse({
        v: 1,
        type: 'ice',
        from: 'A',
        candidate: { candidate: 'c', sdpMid: null, sdpMLineIndex: null }
      }).success
    ).toBe(true);
    expect(
      iceDeliveredSchema.safeParse({
        v: 1,
        type: 'ice',
        from: 'A',
        to: 'B',
        candidate: { candidate: 'c', sdpMid: null, sdpMLineIndex: null }
      }).success
    ).toBe(false);
  });
});

describe('version enforcement', () => {
  const v2Frames = [
    { v: 2, type: 'join', token: 't' },
    { v: 2, type: 'leave' },
    { v: 2, type: 'state', state: { muted: true } },
    { v: 2, type: 'offer', to: 'B', sdp: 's' },
    { v: 2, type: 'answer', to: 'B', sdp: 's' },
    {
      v: 2,
      type: 'ice',
      to: 'B',
      candidate: { candidate: 'c', sdpMid: null, sdpMLineIndex: null }
    }
  ];

  it('rejects every v:2 client frame through the top-level parser and flags the mismatch', () => {
    for (const f of v2Frames) {
      const result = parseClientFrame(f);
      expect(result.success).toBe(false);
      expect(isVersionMismatch(f)).toBe(true);
    }
  });

  it('accepts every v:1 client frame through the top-level parser', () => {
    const ok = [
      { v: 1, type: 'join', token: 't' },
      { v: 1, type: 'leave' },
      { v: 1, type: 'state', state: { muted: true } },
      { v: 1, type: 'offer', to: 'B', sdp: 's' },
      { v: 1, type: 'answer', to: 'B', sdp: 's' },
      {
        v: 1,
        type: 'ice',
        to: 'B',
        candidate: { candidate: 'c', sdpMid: null, sdpMLineIndex: null }
      }
    ];
    for (const f of ok) {
      expect(parseClientFrame(f).success).toBe(true);
      expect(isVersionMismatch(f)).toBe(false);
    }
  });

  it('does not flag a mismatch for a non-object or a frame without v', () => {
    expect(isVersionMismatch(null)).toBe(false);
    expect(isVersionMismatch(42)).toBe(false);
    expect(isVersionMismatch({ type: 'join' })).toBe(false);
  });

  it('rejects an unknown client frame type', () => {
    expect(parseClientFrame({ v: 1, type: 'nope' }).success).toBe(false);
  });
});

describe('server frame union', () => {
  it('parses a valid room-started server frame and rejects an unknown type', () => {
    expect(
      parseServerFrame({ v: 1, type: 'room-started', startedAt: 5 }).success
    ).toBe(true);
    expect(parseServerFrame({ v: 1, type: 'mystery' }).success).toBe(false);
  });

  it('enumerates only participant, room, and opaque metadata as domain-carrying fields', () => {
    const clientTypes = clientFrameSchema.options.map((o) => o.shape.type.value);
    expect(clientTypes.sort()).toEqual(
      ['answer', 'ice', 'join', 'leave', 'offer', 'state'].sort()
    );
    const serverTypes = serverFrameSchema.options.map((o) => o.shape.type.value);
    expect(serverTypes).toContain('participant-joined');
    expect(serverTypes).toContain('ready');
  });
});
