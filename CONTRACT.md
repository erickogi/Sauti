# Sauti contract (v1)

The frozen object model and wire protocol shared by every Sauti package. It is
domain-agnostic: it has no concept of a user, a role, a driver, a passenger, or a
trip. It has participants, rooms, and opaque metadata. The host maps its domain onto
metadata and never sees Sauti invent any.

Every frame carries `v: 1`. A future breaking change bumps `v` so old and new peers
can detect the mismatch instead of misparsing.

## Object model

```
Participant {
  participantId: string            opaque, host-supplied via the authorize hook
  joinedAt: number                 epoch ms, server-stamped
  metadata: Record<string, Json>   host-owned; the library stores and diffs, never inspects
  connectionState: 'connecting' | 'connected' | 'reconnecting' | 'unreachable' | 'left'
  state: { muted: boolean; onHold: boolean }
}

Room {
  roomId: string                   opaque, host-supplied
  participants: Map<participantId, Participant>
  metadata: Record<string, Json>
  maxParticipants: number          host-configured; the mesh is only sane to about 3
  startedAt: number | null         epoch ms, set when the 2nd participant connects
}
```

No `role` field with a closed set of values exists anywhere in the library. If a
host needs the notion of a seat kind, it puts it in `metadata`.

## Identity, tokens, and authorization

The host mints one token type and supplies it to the client. The library never mints
or inspects host identity; it calls an injected `authorize(token)` hook.

`authorize(token) -> { roomId, participantId, metadata? } | null`

The same token is presented on a first join and on a reconnect. For reconnect it
additionally carries, as claims the host includes when it mints the token, the
`roomId`, `participantId`, and a `slotGeneration` so the library can tell a resume
from a fresh join. This is the "extended join token" decision: one token, extra
claims, not a second artifact.

## Glare avoidance: perfect negotiation, role-free

There is no "one side always offers" rule. Each pair of peers runs WebRTC perfect
negotiation. Politeness is decided by comparing the two `participantId` strings: the
lexicographically smaller id is the polite peer (rolls back on collision), the larger
is impolite (wins). This is computed identically and independently by every client
with no server round-trip, and it is a fixed cross-platform invariant: web and
Android must implement the exact same comparison.

## Wire protocol

Transport is a WebSocket the client opens to the server with its token. All frames
are JSON objects with `v` and `type`.

### Handshake

Client's first frame after the socket opens:
```
{ v:1, type:'join', token: string }
```
The server calls `authorize(token)`, and either admits the participant or replies
with `error` and closes.

### Server to client

`ready` (sent once on a fresh join or a resume):
```
{ v:1, type:'ready',
  self: Participant,
  peers: Participant[],            everyone else currently in the room, full state
  room: { roomId, startedAt: number | null, maxParticipants },
  iceServers: RTCIceServer[],      coturn ephemeral credentials
  serverNow: number,               epoch ms, for client clock-offset correction
  resumed: boolean }
```

`participant-joined`:
```
{ v:1, type:'participant-joined', participant: Participant }
```

`participant-state`: a participant's mute/hold (or future generic state) changed:
```
{ v:1, type:'participant-state', participantId: string, state: { muted?: boolean, onHold?: boolean } }
```

`participant-unreachable`: a participant's socket dropped and they are in the grace
window; peers keep the RTCPeerConnection and show a reconnecting state:
```
{ v:1, type:'participant-unreachable', participantId: string }
```

`participant-left`: a participant left for real (explicit leave or grace expiry):
```
{ v:1, type:'participant-left', participantId: string }
```

`room-started`: the call clock started (the 2nd participant connected):
```
{ v:1, type:'room-started', startedAt: number }
```

`error`:
```
{ v:1, type:'error', code: string, message: string }
```

### Client to server, and addressed relay

`leave`: `{ v:1, type:'leave' }`

`state`: the local participant toggled mute or hold:
```
{ v:1, type:'state', state: { muted?: boolean, onHold?: boolean } }
```

Addressed media-negotiation relay. The sender sets `to` (a `participantId`); the
server stamps `from` and delivers only to that one peer:
```
{ v:1, type:'offer',  to: string, sdp: string }
{ v:1, type:'answer', to: string, sdp: string }
{ v:1, type:'ice',    to: string, candidate: { candidate: string, sdpMid: string|null, sdpMLineIndex: number|null } }
```
On delivery the server rewrites `to` out and stamps `from`, so the receiver gets e.g.
`{ v:1, type:'offer', from: '<participantId>', sdp }`.

The server validates and forwards only these frame types with only these fields.
Anything else, or a `to` that is not a current room member, is dropped.

## Requirements, in wire terms

1. **Mute awareness.** `state` from a client is applied to that participant and
   broadcast as `participant-state`. Persisted in room state so `ready.peers` carries
   current mute for every peer on join and resume. Never inferred from audio.
2. **Domain purity.** Only `participantId`, `roomId`, and an opaque `metadata` bag on
   the wire. No role vocabulary. A CI check enforces it.
3. **Call duration.** `startedAt` is set once, when the 2nd participant connects, and
   sent in `ready.room.startedAt` and `room-started`. Clients compute
   `elapsed = (Date.now() + clockOffset) - startedAt`, where
   `clockOffset = serverNow - Date.now()` measured once from `ready`. Survives
   reconnect because it is server-anchored.
4. **Reconnect to the same room.** On a socket drop the server does NOT release the
   room slot. It marks the participant `unreachable`, broadcasts
   `participant-unreachable`, and holds the slot for a grace window (default 30000 ms,
   configurable). A reconnect within the window that presents a token whose
   `slotGeneration` matches reclaims the same slot, gets a `ready` with `resumed:true`
   and the current room snapshot, and is not re-announced to peers as a fresh join.
   Grace expiry releases the slot and broadcasts `participant-left`. Reclaim is a
   single atomic Redis operation so a grace sweep and a resume can never both win.

## Injected-dependency surface (the `@sauti/server` API)

```
interface RedisPort {
  hsetnx(key: string, field: string, value: string): Promise<0 | 1>
  hget(key: string, field: string): Promise<string | null>
  hgetall(key: string): Promise<Record<string, string>>
  hdel(key: string, field: string): Promise<void>
  pexpire(key: string, ms: number): Promise<void>
  eval(script: string, keys: string[], args: string[]): Promise<unknown>
  publish(channel: string, message: string): Promise<void>
  subscribe(channel: string, handler: (message: string) => void): Promise<void>
  unsubscribe(channel: string, handler: (message: string) => void): Promise<void>
}

interface TurnConfig { urls: string[]; secret: string; ttlSeconds: number; realm?: string }

type AuthorizeFn = (token: string) => Promise<{ roomId: string; participantId: string; metadata?: Record<string, unknown> } | null>

interface SautiEvent { type: string; roomId: string; participantId?: string; at: number; data?: Record<string, unknown> }
type EventSink = (event: SautiEvent) => void

interface RateLimiter {
  allowConnect(key: string): Promise<boolean>
  allowFrame(participantId: string): Promise<boolean>
}

interface CreateServerDeps {
  redis: RedisPort
  turn: TurnConfig
  authorize: AuthorizeFn
  namespace: string                required; prefixes every Redis key and channel
  maxParticipantsPerRoom?: number  default 3
  graceMs?: number                 default 30000
  allowedOrigins?: string[]
  requireOrigin?: boolean          default true
  onEvent?: EventSink
  rateLimiter?: RateLimiter
}

function createSautiServer(deps: CreateServerDeps): {
  attach(httpServer: import('http').Server, opts?: { path?: string }): void
  listActiveRooms(): Promise<Array<{ roomId: string; participantCount: number; participantIds: string[]; startedAt: number | null }>>
  close(): Promise<void>
}
```

Rules the server enforces: origin is checked on the WS upgrade and a missing Origin
is rejected when `requireOrigin` is true; TURN credentials are minted with the coturn
REST scheme (`username = <exp>:<participantId-derived-opaque-ref>`, `credential =
base64(hmacSha1(username, secret))`) and never expose the secret; cross-pod relay and
presence go through `redis.publish`/`subscribe` on `<namespace>:room:<roomId>`, keyed
so two peers on different pods still connect; the room slot claim lives at
`<namespace>:room:<roomId>` as a hash; nothing host-domain is ever logged or emitted.

## Events and commands (client cores expose, not defined here in detail)

Web `@sauti/core` and Android `io.sauti` expose the same vocabulary over their native
idioms (an event emitter / a Kotlin Flow):
- Commands: `join`, `leave`, `setMuted(bool)`, `setHold(bool)`.
- State: participants (with per-participant muted/onHold/connectionState/quality),
  local mute/hold, duration, aggregate connection quality, reconnecting.
- Events mirror the server frames: joined, left, unreachable, state-changed,
  reconnecting, reconnected, quality-changed.

## Purity

No package source may contain `driver`, `passenger`, `trip`, or `rider`
(case-insensitive) outside of test fixtures. Each package ships a `purity` script
that greps its own source and fails on a hit. This is a build gate, not a convention.
