import { z } from 'zod';
import type { Json } from './json.js';
import { PROTOCOL_VERSION } from './version.js';

const jsonSchema: z.ZodType<Json> = z.lazy(() =>
  z.union([
    z.string(),
    z.number(),
    z.boolean(),
    z.null(),
    z.array(jsonSchema),
    z.record(jsonSchema)
  ])
);

const v = z.literal(PROTOCOL_VERSION);

export const connectionStateSchema = z.enum([
  'connecting',
  'connected',
  'reconnecting',
  'unreachable',
  'left'
]);

export const participantStateSchema = z.object({
  muted: z.boolean(),
  onHold: z.boolean()
});

export const partialStateSchema = z.object({
  muted: z.boolean().optional(),
  onHold: z.boolean().optional()
});

export const participantSchema = z.object({
  participantId: z.string(),
  joinedAt: z.number(),
  metadata: z.record(jsonSchema),
  connectionState: connectionStateSchema,
  state: participantStateSchema
});

export const iceCandidateSchema = z.object({
  candidate: z.string(),
  sdpMid: z.string().nullable(),
  sdpMLineIndex: z.number().nullable()
});

export const iceServerSchema = z
  .object({
    urls: z.union([z.string(), z.array(z.string())]),
    username: z.string().optional(),
    credential: z.string().optional(),
    realm: z.string().optional()
  })
  .passthrough();

export const joinFrameSchema = z
  .object({
    v,
    type: z.literal('join'),
    token: z.string()
  })
  .strict();

export const leaveFrameSchema = z
  .object({
    v,
    type: z.literal('leave')
  })
  .strict();

export const clientStateFrameSchema = z
  .object({
    v,
    type: z.literal('state'),
    state: partialStateSchema
  })
  .strict();

export const offerSendSchema = z
  .object({
    v,
    type: z.literal('offer'),
    to: z.string(),
    sdp: z.string()
  })
  .strict();

export const answerSendSchema = z
  .object({
    v,
    type: z.literal('answer'),
    to: z.string(),
    sdp: z.string()
  })
  .strict();

export const iceSendSchema = z
  .object({
    v,
    type: z.literal('ice'),
    to: z.string(),
    candidate: iceCandidateSchema
  })
  .strict();

export const offerDeliveredSchema = z
  .object({
    v,
    type: z.literal('offer'),
    from: z.string(),
    sdp: z.string()
  })
  .strict();

export const answerDeliveredSchema = z
  .object({
    v,
    type: z.literal('answer'),
    from: z.string(),
    sdp: z.string()
  })
  .strict();

export const iceDeliveredSchema = z
  .object({
    v,
    type: z.literal('ice'),
    from: z.string(),
    candidate: iceCandidateSchema
  })
  .strict();

export const readyFrameSchema = z.object({
  v,
  type: z.literal('ready'),
  self: participantSchema,
  peers: z.array(participantSchema),
  room: z.object({
    roomId: z.string(),
    startedAt: z.number().nullable(),
    maxParticipants: z.number()
  }),
  iceServers: z.array(iceServerSchema),
  serverNow: z.number(),
  resumed: z.boolean()
});

export const participantJoinedFrameSchema = z.object({
  v,
  type: z.literal('participant-joined'),
  participant: participantSchema
});

export const participantStateFrameSchema = z.object({
  v,
  type: z.literal('participant-state'),
  participantId: z.string(),
  state: partialStateSchema
});

export const participantUnreachableFrameSchema = z.object({
  v,
  type: z.literal('participant-unreachable'),
  participantId: z.string()
});

export const participantLeftFrameSchema = z.object({
  v,
  type: z.literal('participant-left'),
  participantId: z.string()
});

export const roomStartedFrameSchema = z.object({
  v,
  type: z.literal('room-started'),
  startedAt: z.number()
});

export const errorFrameSchema = z.object({
  v,
  type: z.literal('error'),
  code: z.string(),
  message: z.string()
});

export const clientFrameSchema = z.discriminatedUnion('type', [
  joinFrameSchema,
  leaveFrameSchema,
  clientStateFrameSchema,
  offerSendSchema,
  answerSendSchema,
  iceSendSchema
]);

export const serverFrameSchema = z.discriminatedUnion('type', [
  readyFrameSchema,
  participantJoinedFrameSchema,
  participantStateFrameSchema,
  participantUnreachableFrameSchema,
  participantLeftFrameSchema,
  roomStartedFrameSchema,
  errorFrameSchema,
  offerDeliveredSchema,
  answerDeliveredSchema,
  iceDeliveredSchema
]);

export type JoinFrame = z.infer<typeof joinFrameSchema>;
export type LeaveFrame = z.infer<typeof leaveFrameSchema>;
export type ClientStateFrame = z.infer<typeof clientStateFrameSchema>;
export type OfferSendFrame = z.infer<typeof offerSendSchema>;
export type AnswerSendFrame = z.infer<typeof answerSendSchema>;
export type IceSendFrame = z.infer<typeof iceSendSchema>;
export type OfferDeliveredFrame = z.infer<typeof offerDeliveredSchema>;
export type AnswerDeliveredFrame = z.infer<typeof answerDeliveredSchema>;
export type IceDeliveredFrame = z.infer<typeof iceDeliveredSchema>;
export type ReadyFrame = z.infer<typeof readyFrameSchema>;
export type ParticipantJoinedFrame = z.infer<typeof participantJoinedFrameSchema>;
export type ParticipantStateFrame = z.infer<typeof participantStateFrameSchema>;
export type ParticipantUnreachableFrame = z.infer<
  typeof participantUnreachableFrameSchema
>;
export type ParticipantLeftFrame = z.infer<typeof participantLeftFrameSchema>;
export type RoomStartedFrame = z.infer<typeof roomStartedFrameSchema>;
export type ErrorFrame = z.infer<typeof errorFrameSchema>;
export type ClientFrame = z.infer<typeof clientFrameSchema>;
export type ServerFrame = z.infer<typeof serverFrameSchema>;

export function isVersionMismatch(raw: unknown): boolean {
  return (
    typeof raw === 'object' &&
    raw !== null &&
    'v' in raw &&
    (raw as { v: unknown }).v !== PROTOCOL_VERSION
  );
}

export function parseClientFrame(raw: unknown) {
  return clientFrameSchema.safeParse(raw);
}

export function parseServerFrame(raw: unknown) {
  return serverFrameSchema.safeParse(raw);
}
