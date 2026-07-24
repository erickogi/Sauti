import type { IncomingMessage, Server as HttpServer } from 'node:http';
import type { Duplex } from 'node:stream';
import { WebSocketServer, WebSocket, type RawData } from 'ws';
import {
  isVersionMismatch,
  joinFrameSchema,
  clientStateFrameSchema,
  iceCandidateSchema,
  PROTOCOL_VERSION,
  type Participant
} from '@sauti/protocol';
import type {
  ActiveRoom,
  CreateServerDeps,
  SautiEvent,
  SautiServer
} from './types.js';
import { CLAIM_SLOT, RECLAIM_SLOT, SWEEP_SLOT, UPDATE_SLOT } from './scripts.js';
import { decodeSlot, encodeSlot, slotToParticipant, type SlotRecord } from './slot.js';
import { mintIceServers } from './turn.js';

interface Conn {
  ws: WebSocket;
  joined: boolean;
  left: boolean;
  roomId: string;
  participantId: string;
  generation: string;
}

type Envelope =
  | { kind: 'relay'; target: string; frame: unknown }
  | { kind: 'broadcast'; exclude?: string; frame: unknown };

const DEFAULT_MAX = 3;
const DEFAULT_GRACE_MS = 30000;

export function createSautiServer(deps: CreateServerDeps): SautiServer {
  if (typeof deps.namespace !== 'string' || deps.namespace.length === 0) {
    throw new Error('sauti: namespace is required and must be a non-empty string');
  }

  const redis = deps.redis;
  const max = deps.maxParticipantsPerRoom ?? DEFAULT_MAX;
  const graceMs = deps.graceMs ?? DEFAULT_GRACE_MS;
  const requireOrigin = deps.requireOrigin ?? true;
  const allowedOrigins = deps.allowedOrigins;
  const roomTtl = Math.max(graceMs * 2, 60000);

  const wss = new WebSocketServer({ noServer: true });
  const rooms = new Map<string, Map<string, WebSocket>>();
  const roomHandlers = new Map<string, (message: string) => void>();
  const sweeps = new Map<string, ReturnType<typeof setTimeout>>();
  const allSockets = new Set<WebSocket>();

  const roomKey = (roomId: string): string => `${deps.namespace}:room:${roomId}`;
  const metaKey = (roomId: string): string =>
    `${deps.namespace}:room:${roomId}:meta`;
  const registryKey = `${deps.namespace}:rooms`;
  const sweepKey = (roomId: string, participantId: string): string =>
    `${roomId}${participantId}`;

  function emit(
    type: string,
    roomId: string,
    participantId?: string,
    data?: Record<string, unknown>
  ): void {
    if (!deps.onEvent) return;
    const event: SautiEvent = { type, roomId, at: Date.now() };
    if (participantId !== undefined) event.participantId = participantId;
    if (data !== undefined) event.data = data;
    deps.onEvent(event);
  }

  function send(ws: WebSocket, frame: unknown): void {
    if (ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify(frame));
    }
  }

  function deliverLocal(roomId: string, env: Envelope): void {
    const locals = rooms.get(roomId);
    if (!locals) return;
    if (env.kind === 'relay') {
      const target = locals.get(env.target);
      if (target) send(target, env.frame);
      return;
    }
    for (const [pid, ws] of locals) {
      if (pid === env.exclude) continue;
      send(ws, env.frame);
    }
  }

  async function publishRoom(roomId: string, env: Envelope): Promise<void> {
    await redis.publish(roomKey(roomId), JSON.stringify(env));
  }

  async function ensureSubscribed(roomId: string): Promise<void> {
    if (roomHandlers.has(roomId)) return;
    const handler = (message: string): void => {
      try {
        deliverLocal(roomId, JSON.parse(message) as Envelope);
      } catch {
        return;
      }
    };
    roomHandlers.set(roomId, handler);
    await redis.subscribe(roomKey(roomId), handler);
  }

  async function unsubscribeRoom(roomId: string): Promise<void> {
    const handler = roomHandlers.get(roomId);
    if (!handler) return;
    roomHandlers.delete(roomId);
    await redis.unsubscribe(roomKey(roomId), handler);
  }

  function addLocal(roomId: string, participantId: string, ws: WebSocket): void {
    let locals = rooms.get(roomId);
    if (!locals) {
      locals = new Map();
      rooms.set(roomId, locals);
    }
    locals.set(participantId, ws);
  }

  async function removeLocalSocket(
    roomId: string,
    participantId: string
  ): Promise<void> {
    const locals = rooms.get(roomId);
    if (!locals) return;
    locals.delete(participantId);
    if (locals.size === 0) {
      rooms.delete(roomId);
      await unsubscribeRoom(roomId);
    }
  }

  async function getStartedAt(roomId: string): Promise<number | null> {
    const raw = await redis.hget(metaKey(roomId), 'startedAt');
    return raw === null ? null : Number(raw);
  }

  async function cleanupRedisRoomIfEmpty(roomId: string): Promise<void> {
    const all = await redis.hgetall(roomKey(roomId));
    if (Object.keys(all).length > 0) return;
    await redis.hdel(registryKey, roomId);
    await redis.hdel(metaKey(roomId), 'startedAt');
    emit('room.closed', roomId);
  }

  async function stampStartedAtIfReady(
    roomId: string,
    now: number
  ): Promise<void> {
    const all = await redis.hgetall(roomKey(roomId));
    let connected = 0;
    for (const enc of Object.values(all)) {
      if (decodeSlot(enc).connectionState === 'connected') connected += 1;
    }
    if (connected < 2) return;
    const stamped = await redis.hsetnx(metaKey(roomId), 'startedAt', String(now));
    if (stamped === 1) {
      await publishRoom(roomId, {
        kind: 'broadcast',
        frame: { v: 1, type: 'room-started', startedAt: now }
      });
      emit('room.started', roomId, undefined, { startedAt: now });
    }
  }

  async function buildPeers(
    roomId: string,
    selfId: string
  ): Promise<Participant[]> {
    const all = await redis.hgetall(roomKey(roomId));
    const peers: Participant[] = [];
    for (const [pid, enc] of Object.entries(all)) {
      if (pid === selfId) continue;
      peers.push(slotToParticipant(decodeSlot(enc)));
    }
    return peers;
  }

  async function sendReady(
    conn: Conn,
    self: SlotRecord,
    resumed: boolean
  ): Promise<void> {
    const now = Date.now();
    const peers = await buildPeers(conn.roomId, conn.participantId);
    const startedAt = await getStartedAt(conn.roomId);
    send(conn.ws, {
      v: 1,
      type: 'ready',
      self: slotToParticipant(self),
      peers,
      room: { roomId: conn.roomId, startedAt, maxParticipants: max },
      iceServers: mintIceServers(deps.turn, conn.participantId, now),
      serverNow: now,
      resumed
    });
  }

  async function freshJoin(
    conn: Conn,
    metadata: Record<string, unknown>
  ): Promise<void> {
    const now = Date.now();
    const record: SlotRecord = {
      participantId: conn.participantId,
      joinedAt: now,
      metadata: metadata as SlotRecord['metadata'],
      connectionState: 'connected',
      state: { muted: false, onHold: false },
      generation: conn.generation,
      dropEpoch: 0
    };
    const outcome = (await redis.eval(
      CLAIM_SLOT,
      [roomKey(conn.roomId)],
      [conn.participantId, String(max), encodeSlot(record)]
    )) as string;
    if (outcome === 'full') {
      send(conn.ws, {
        v: 1,
        type: 'error',
        code: 'room_full',
        message: 'room is at capacity'
      });
      conn.ws.close();
      return;
    }
    if (outcome !== 'ok') {
      send(conn.ws, {
        v: 1,
        type: 'error',
        code: 'slot_conflict',
        message: 'slot is already held'
      });
      conn.ws.close();
      return;
    }
    conn.joined = true;
    await redis.hsetnx(registryKey, conn.roomId, '1');
    await redis.pexpire(roomKey(conn.roomId), roomTtl);
    await redis.pexpire(metaKey(conn.roomId), roomTtl);
    addLocal(conn.roomId, conn.participantId, conn.ws);
    await ensureSubscribed(conn.roomId);
    await stampStartedAtIfReady(conn.roomId, now);
    await sendReady(conn, record, false);
    await publishRoom(conn.roomId, {
      kind: 'broadcast',
      exclude: conn.participantId,
      frame: {
        v: 1,
        type: 'participant-joined',
        participant: slotToParticipant(record)
      }
    });
    emit('participant.joined', conn.roomId, conn.participantId);
  }

  async function tryResume(
    conn: Conn,
    existing: SlotRecord
  ): Promise<'resumed' | 'gone'> {
    const now = Date.now();
    const record: SlotRecord = { ...existing, connectionState: 'connected' };
    const outcome = (await redis.eval(
      RECLAIM_SLOT,
      [roomKey(conn.roomId)],
      [conn.participantId, conn.generation, encodeSlot(record)]
    )) as string;
    if (outcome !== 'ok') return 'gone';
    conn.joined = true;
    const pending = sweeps.get(sweepKey(conn.roomId, conn.participantId));
    if (pending) {
      clearTimeout(pending);
      sweeps.delete(sweepKey(conn.roomId, conn.participantId));
    }
    await redis.pexpire(roomKey(conn.roomId), roomTtl);
    addLocal(conn.roomId, conn.participantId, conn.ws);
    await ensureSubscribed(conn.roomId);
    await stampStartedAtIfReady(conn.roomId, now);
    await sendReady(conn, record, true);
    emit('participant.resumed', conn.roomId, conn.participantId);
    return 'resumed';
  }

  async function handleJoin(conn: Conn, token: string): Promise<void> {
    const result = await deps.authorize(token);
    if (!result) {
      send(conn.ws, {
        v: 1,
        type: 'error',
        code: 'unauthorized',
        message: 'authorization failed'
      });
      conn.ws.close();
      return;
    }
    conn.roomId = result.roomId;
    conn.participantId = result.participantId;
    conn.generation = String(result.slotGeneration ?? '0');
    const metadata = result.metadata ?? {};

    const existingRaw = await redis.hget(roomKey(conn.roomId), conn.participantId);
    if (existingRaw !== null) {
      const existing = decodeSlot(existingRaw);
      if (existing.generation === conn.generation) {
        const outcome = await tryResume(conn, existing);
        if (outcome === 'resumed') return;
      }
    }
    await freshJoin(conn, metadata);
  }

  async function allowFrame(participantId: string): Promise<boolean> {
    if (!deps.rateLimiter) return true;
    return deps.rateLimiter.allowFrame(participantId);
  }

  async function handleRelay(
    conn: Conn,
    type: 'offer' | 'answer' | 'ice',
    raw: Record<string, unknown>
  ): Promise<void> {
    if (!(await allowFrame(conn.participantId))) {
      emit('relay.frame_dropped', conn.roomId, conn.participantId, {
        reason: 'rate_limited',
        type
      });
      return;
    }
    const permitted =
      type === 'ice'
        ? ['v', 'type', 'to', 'candidate']
        : ['v', 'type', 'to', 'sdp'];
    for (const key of Object.keys(raw)) {
      if (!permitted.includes(key)) {
        emit('relay.frame_dropped', conn.roomId, conn.participantId, {
          reason: 'unexpected_field',
          type
        });
        return;
      }
    }
    const to = raw.to;
    if (typeof to !== 'string') {
      emit('relay.frame_dropped', conn.roomId, conn.participantId, {
        reason: 'bad_target',
        type
      });
      return;
    }
    let payload: Record<string, unknown>;
    if (type === 'ice') {
      const candidate = iceCandidateSchema.safeParse(raw.candidate);
      if (!candidate.success) {
        emit('relay.frame_dropped', conn.roomId, conn.participantId, {
          reason: 'bad_payload',
          type
        });
        return;
      }
      payload = { candidate: candidate.data };
    } else {
      if (typeof raw.sdp !== 'string') {
        emit('relay.frame_dropped', conn.roomId, conn.participantId, {
          reason: 'bad_payload',
          type
        });
        return;
      }
      payload = { sdp: raw.sdp };
    }
    const member = await redis.hget(roomKey(conn.roomId), to);
    if (member === null) {
      emit('relay.frame_dropped', conn.roomId, conn.participantId, {
        reason: 'unknown_target',
        type
      });
      return;
    }
    await publishRoom(conn.roomId, {
      kind: 'relay',
      target: to,
      frame: { v: 1, type, from: conn.participantId, ...payload }
    });
  }

  async function handleState(
    conn: Conn,
    raw: Record<string, unknown>
  ): Promise<void> {
    if (!(await allowFrame(conn.participantId))) {
      emit('relay.frame_dropped', conn.roomId, conn.participantId, {
        reason: 'rate_limited',
        type: 'state'
      });
      return;
    }
    const parsed = clientStateFrameSchema.safeParse(raw);
    if (!parsed.success) {
      emit('relay.frame_dropped', conn.roomId, conn.participantId, {
        reason: 'bad_payload',
        type: 'state'
      });
      return;
    }
    const currentRaw = await redis.hget(roomKey(conn.roomId), conn.participantId);
    if (currentRaw === null) return;
    const record = decodeSlot(currentRaw);
    const changed: { muted?: boolean; onHold?: boolean } = {};
    const nextState = { ...record.state };
    if (parsed.data.state.muted !== undefined) {
      nextState.muted = parsed.data.state.muted;
      changed.muted = parsed.data.state.muted;
    }
    if (parsed.data.state.onHold !== undefined) {
      nextState.onHold = parsed.data.state.onHold;
      changed.onHold = parsed.data.state.onHold;
    }
    if (Object.keys(changed).length === 0) return;
    record.state = nextState;
    record.connectionState = 'connected';
    await redis.eval(
      UPDATE_SLOT,
      [roomKey(conn.roomId)],
      [conn.participantId, encodeSlot(record)]
    );
    await publishRoom(conn.roomId, {
      kind: 'broadcast',
      exclude: conn.participantId,
      frame: {
        v: 1,
        type: 'participant-state',
        participantId: conn.participantId,
        state: changed
      }
    });
    emit('participant.state', conn.roomId, conn.participantId, {
      keys: Object.keys(changed)
    });
  }

  async function handleLeave(conn: Conn): Promise<void> {
    if (conn.left) return;
    conn.left = true;
    await redis.hdel(roomKey(conn.roomId), conn.participantId);
    await publishRoom(conn.roomId, {
      kind: 'broadcast',
      exclude: conn.participantId,
      frame: {
        v: 1,
        type: 'participant-left',
        participantId: conn.participantId
      }
    });
    emit('participant.left', conn.roomId, conn.participantId, { reason: 'leave' });
    await removeLocalSocket(conn.roomId, conn.participantId);
    await cleanupRedisRoomIfEmpty(conn.roomId);
    conn.ws.close();
  }

  async function runSweep(
    roomId: string,
    participantId: string,
    generation: string,
    epoch: number
  ): Promise<void> {
    sweeps.delete(sweepKey(roomId, participantId));
    const outcome = (await redis.eval(
      SWEEP_SLOT,
      [roomKey(roomId)],
      [participantId, generation, String(epoch)]
    )) as string;
    if (outcome !== 'swept') return;
    await publishRoom(roomId, {
      kind: 'broadcast',
      frame: { v: 1, type: 'participant-left', participantId }
    });
    emit('participant.left', roomId, participantId, { reason: 'grace_expired' });
    await cleanupRedisRoomIfEmpty(roomId);
  }

  async function handleDrop(conn: Conn): Promise<void> {
    if (conn.left || !conn.joined) return;
    const currentRaw = await redis.hget(roomKey(conn.roomId), conn.participantId);
    if (currentRaw === null) {
      await removeLocalSocket(conn.roomId, conn.participantId);
      return;
    }
    const record = decodeSlot(currentRaw);
    const epoch = record.dropEpoch + 1;
    record.connectionState = 'unreachable';
    record.dropEpoch = epoch;
    await redis.eval(
      UPDATE_SLOT,
      [roomKey(conn.roomId)],
      [conn.participantId, encodeSlot(record)]
    );
    await publishRoom(conn.roomId, {
      kind: 'broadcast',
      exclude: conn.participantId,
      frame: {
        v: 1,
        type: 'participant-unreachable',
        participantId: conn.participantId
      }
    });
    emit('participant.unreachable', conn.roomId, conn.participantId);
    const roomId = conn.roomId;
    const participantId = conn.participantId;
    const generation = conn.generation;
    const timer = setTimeout(() => {
      void runSweep(roomId, participantId, generation, epoch);
    }, graceMs);
    sweeps.set(sweepKey(roomId, participantId), timer);
    await removeLocalSocket(conn.roomId, conn.participantId);
  }

  async function onFirstFrame(conn: Conn, raw: unknown): Promise<void> {
    if (isVersionMismatch(raw)) {
      send(conn.ws, {
        v: 1,
        type: 'error',
        code: 'version_mismatch',
        message: 'unsupported protocol version'
      });
      conn.ws.close();
      return;
    }
    const parsed = joinFrameSchema.safeParse(raw);
    if (!parsed.success) {
      send(conn.ws, {
        v: 1,
        type: 'error',
        code: 'bad_handshake',
        message: 'first frame must be a valid join'
      });
      conn.ws.close();
      return;
    }
    await handleJoin(conn, parsed.data.token);
  }

  async function onJoinedFrame(conn: Conn, raw: unknown): Promise<void> {
    if (typeof raw !== 'object' || raw === null) return;
    const frame = raw as Record<string, unknown>;
    if (frame.v !== PROTOCOL_VERSION) {
      emit('relay.frame_dropped', conn.roomId, conn.participantId, {
        reason: 'version_mismatch'
      });
      return;
    }
    switch (frame.type) {
      case 'offer':
      case 'answer':
      case 'ice':
        await handleRelay(conn, frame.type, frame);
        return;
      case 'state':
        await handleState(conn, frame);
        return;
      case 'leave':
        await handleLeave(conn);
        return;
      default:
        emit('relay.frame_dropped', conn.roomId, conn.participantId, {
          reason: 'unknown_type'
        });
        return;
    }
  }

  function onConnection(ws: WebSocket): void {
    const conn: Conn = {
      ws,
      joined: false,
      left: false,
      roomId: '',
      participantId: '',
      generation: '0'
    };
    allSockets.add(ws);
    ws.on('message', (data: RawData) => {
      let raw: unknown;
      try {
        raw = JSON.parse(data.toString());
      } catch {
        return;
      }
      if (!conn.joined) {
        void onFirstFrame(conn, raw);
      } else {
        void onJoinedFrame(conn, raw);
      }
    });
    ws.on('close', () => {
      allSockets.delete(ws);
      void handleDrop(conn);
    });
    ws.on('error', () => {});
  }

  function originAllowed(req: IncomingMessage): boolean {
    const origin = req.headers.origin;
    if (origin === undefined) return !requireOrigin;
    if (allowedOrigins && !allowedOrigins.includes(origin)) return false;
    return true;
  }

  function reject(socket: Duplex, status: number, reason: string): void {
    socket.write(`HTTP/1.1 ${status} ${reason}\r\nConnection: close\r\n\r\n`);
    socket.destroy();
  }

  async function handleUpgrade(
    req: IncomingMessage,
    socket: Duplex,
    head: Buffer,
    wsPath: string | undefined
  ): Promise<void> {
    if (wsPath !== undefined) {
      const pathname = new URL(req.url ?? '/', 'http://localhost').pathname;
      if (pathname !== wsPath) {
        reject(socket, 404, 'Not Found');
        return;
      }
    }
    if (!originAllowed(req)) {
      reject(socket, 403, 'Forbidden');
      return;
    }
    if (deps.rateLimiter) {
      const key = req.socket.remoteAddress ?? 'unknown';
      if (!(await deps.rateLimiter.allowConnect(key))) {
        reject(socket, 429, 'Too Many Requests');
        return;
      }
    }
    wss.handleUpgrade(req, socket, head, (ws) => {
      onConnection(ws);
    });
  }

  function attach(httpServer: HttpServer, opts?: { path?: string }): void {
    httpServer.on('upgrade', (req, socket, head) => {
      void handleUpgrade(req, socket, head, opts?.path);
    });
  }

  async function listActiveRooms(): Promise<ActiveRoom[]> {
    const registry = await redis.hgetall(registryKey);
    const out: ActiveRoom[] = [];
    for (const roomId of Object.keys(registry)) {
      const all = await redis.hgetall(roomKey(roomId));
      const participantIds = Object.keys(all);
      out.push({
        roomId,
        participantCount: participantIds.length,
        participantIds,
        startedAt: await getStartedAt(roomId)
      });
    }
    return out;
  }

  async function close(): Promise<void> {
    for (const timer of sweeps.values()) clearTimeout(timer);
    sweeps.clear();
    for (const roomId of [...roomHandlers.keys()]) {
      await unsubscribeRoom(roomId);
    }
    rooms.clear();
    for (const ws of allSockets) ws.terminate();
    allSockets.clear();
    await new Promise<void>((resolve) => wss.close(() => resolve()));
  }

  return { attach, listActiveRooms, close };
}
