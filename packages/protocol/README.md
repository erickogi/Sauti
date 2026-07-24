# @sauti/protocol

Dependency-light, domain-pure wire contract shared by every Sauti package. It is
types, zod schemas, and pure functions. It performs no I/O, opens no socket, touches
no Redis, and has no side effect on import.

## What is here

- `Participant` and `Room` object model. `Participant.metadata` is an opaque
  host-owned `Record<string, Json>`; the library stores and diffs it and never reads
  a key.
- `Room.startedAt: number | null`. It is `null` until the second participant
  connects, at which moment the server stamps it once with an epoch-ms value. It is
  never re-stamped by later joins, leaves, or reconnects.
- Runtime zod schemas for every client-to-server and server-to-client frame, plus
  `clientFrameSchema` and `serverFrameSchema` discriminated unions.
- `PROTOCOL_VERSION` (`1`). Every frame carries `v: 1`. `isVersionMismatch` reports a
  frame whose `v` differs from the current protocol so a parser can reject it with a
  version signal instead of misparsing it.
- `politePeer(a, b)` and `isPolite(self, peer)`: role-free glare avoidance. The
  lexicographically smaller `participantId` is the polite peer. The comparison is a
  plain string ordering computed identically and independently on every client with
  no server round-trip. Web and Android must implement the same comparison.

## Purity

The `purity` script greps this package's `src` for the banned host-domain vocabulary
and exits non-zero on a hit. It is a build gate.
